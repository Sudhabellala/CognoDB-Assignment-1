package com.wexa.benchmark;

import com.wexa.benchmark.clients.ArangoDBGraphClient;
import com.wexa.benchmark.clients.BoltClient;
import com.wexa.benchmark.clients.DgraphGraphClient;
import com.wexa.benchmark.clients.GraphClient;
import com.wexa.benchmark.workloads.Workloads;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

import java.io.FileReader;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Mixed concurrent read/write workload: 80% point-lookup reads, 20% single-edge
 * inserts (new synthetic node + edge, so writes don't collide with each other),
 * run at several client-concurrency levels. Reports sustained queries/sec.
 */
public class BenchMixed {

    static final Path DATA_DIR = Paths.get("data");
    static final int DURATION_SECONDS = 10;
    static final int[] CONCURRENCY_LEVELS = {1, 10, 40};
    static final double READ_WRITE_SPLIT = 0.8; // 80% reads

    // Writes use ids starting well above the real dataset's max id so they never
    // collide with existing nodes or with each other across threads.
    static final long WRITE_ID_START = 10_000_000L;
    static final AtomicLong writeCounter = new AtomicLong(WRITE_ID_START);

    static List<Long> sampleIds(int n) throws Exception {
        List<Long> ids = new ArrayList<>();
        try (CSVParser parser = CSVParser.parse(new FileReader(DATA_DIR.resolve("nodes.csv").toFile()),
                CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).build())) {
            for (CSVRecord r : parser) ids.add(Long.parseLong(r.get("id")));
        }
        Collections.shuffle(ids);
        return ids.subList(0, Math.min(n, ids.size()));
    }

    static long worker(String platform, GraphClient client, List<Long> idPool, long stopAtNanos) {
        Random rnd = new Random();
        long count = 0;

        if (Workloads.BOLT_PLATFORMS.contains(platform)) {
            BoltClient bc = (BoltClient) client;
            String readQ = Workloads.CYPHER.get("mixed_read");
            String writeQ = Workloads.CYPHER.get("mixed_write");
            while (System.nanoTime() < stopAtNanos) {
                if (rnd.nextDouble() < READ_WRITE_SPLIT) {
                    bc.run(readQ, Map.of("id", idPool.get(rnd.nextInt(idPool.size()))));
                } else {
                    long newId = writeCounter.getAndIncrement();
                    bc.runWrite(writeQ, Map.of(
                            "src", idPool.get(rnd.nextInt(idPool.size())),
                            "dst", newId,
                            "group", newId % 100));
                }
                count++;
            }
            return count;
        }

        if (platform.equals("arangodb")) {
            ArangoDBGraphClient ac = (ArangoDBGraphClient) client;
            String readQ = Workloads.AQL.get("mixed_read");
            while (System.nanoTime() < stopAtNanos) {
                if (rnd.nextDouble() < READ_WRITE_SPLIT) {
                    ac.run(readQ, Map.of("id", idPool.get(rnd.nextInt(idPool.size()))),
                            com.google.gson.JsonObject.class).forEachRemaining(x -> {});
                } else {
                    long newId = writeCounter.getAndIncrement();
                    ac.db.collection("persons").insertDocument(
                            Map.of("_key", String.valueOf(newId), "id", newId, "group", newId % 100));
                    ac.db.collection("sent_email").insertDocument(Map.of(
                            "_from", "persons/" + idPool.get(rnd.nextInt(idPool.size())),
                            "_to", "persons/" + newId));
                }
                count++;
            }
            return count;
        }

        if (platform.equals("dgraph")) {
            DgraphGraphClient dc = (DgraphGraphClient) client;
            String q = "query q($id: string) { p(func: eq(id, $id)) { uid id group } }";
            while (System.nanoTime() < stopAtNanos) {
                if (rnd.nextDouble() < READ_WRITE_SPLIT) {
                    long id = idPool.get(rnd.nextInt(idPool.size()));
                    dc.query(q, Map.of("$id", String.valueOf(id)));
                } else {
                    long newId = writeCounter.getAndIncrement();
                    var txn = dc.client.newTransaction();
                    try {
                        com.google.gson.JsonObject obj = new com.google.gson.JsonObject();
                        obj.addProperty("id", newId);
                        obj.addProperty("group", newId % 100);
                        io.dgraph.DgraphProto.Mutation mu = io.dgraph.DgraphProto.Mutation.newBuilder()
                                .setSetJson(com.google.protobuf.ByteString.copyFromUtf8(obj.toString()))
                                .build();
                        txn.mutate(mu);
                        txn.commit();
                    } finally {
                        txn.discard();
                    }
                }
                count++;
            }
            return count;
        }

        throw new IllegalArgumentException(platform);
    }

    static Map<String, Object> runConcurrencyLevel(String platform, int concurrency, List<Long> idPool)
            throws Exception {
        List<GraphClient> clients = new ArrayList<>();
        for (int i = 0; i < concurrency; i++) clients.add(Connect.getClient(platform));

        long startNanos = System.nanoTime();
        long stopAtNanos = startNanos + DURATION_SECONDS * 1_000_000_000L;

        ExecutorService pool = Executors.newFixedThreadPool(concurrency);
        List<Future<Long>> futures = new ArrayList<>();
        for (GraphClient c : clients) {
            futures.add(pool.submit(() -> worker(platform, c, idPool, stopAtNanos)));
        }
        long totalOps = 0;
        for (Future<Long> f : futures) totalOps += f.get();
        pool.shutdown();

        double elapsedSeconds = (System.nanoTime() - startNanos) / 1_000_000_000.0;
        for (GraphClient c : clients) c.close();

        double qps = totalOps / elapsedSeconds;
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("concurrency", concurrency);
        result.put("total_ops", totalOps);
        result.put("elapsed_seconds", Math.round(elapsedSeconds * 100.0) / 100.0);
        result.put("queries_per_second", Math.round(qps * 10.0) / 10.0);
        return result;
    }

    public static List<Map<String, Object>> runMixed(String platform) throws Exception {
        List<Long> idPool = sampleIds(500);
        List<Map<String, Object>> results = new ArrayList<>();
        for (int level : CONCURRENCY_LEVELS) {
            System.out.printf("[%s] mixed workload @ concurrency=%d for %ds (%d%% read / %d%% write)...%n",
                    platform, level, DURATION_SECONDS, (int) (READ_WRITE_SPLIT * 100),
                    (int) ((1 - READ_WRITE_SPLIT) * 100));
            Map<String, Object> r = runConcurrencyLevel(platform, level, idPool);
            System.out.printf("    %s qps (%s ops in %ss)%n",
                    r.get("queries_per_second"), r.get("total_ops"), r.get("elapsed_seconds"));
            results.add(r);
        }
        Common.saveResult(platform, "mixed", Map.of("levels", results));
        return results;
    }

    public static void main(String[] args) throws Exception {
        runMixed(args.length > 0 ? args[0] : "neo4j");
    }
}
