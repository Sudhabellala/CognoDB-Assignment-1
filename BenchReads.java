package com.wexa.benchmark;

import com.google.gson.JsonObject;
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

public class BenchReads {

    static final Path DATA_DIR = Paths.get("data");
    static final int ITERATIONS = 100;
    static final int WARMUP = 10;
    static final List<String> WORKLOAD_KEYS = List.of(
            "traversal_1hop", "traversal_2hop", "traversal_3hop",
            "point_lookup", "filtered_lookup", "aggregation");

    static List<Long> sampleIds(int n) throws Exception {
        List<Long> ids = new ArrayList<>();
        try (CSVParser parser = CSVParser.parse(new FileReader(DATA_DIR.resolve("nodes.csv").toFile()),
                CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).build())) {
            for (CSVRecord r : parser) ids.add(Long.parseLong(r.get("id")));
        }
        Collections.shuffle(ids);
        return ids.subList(0, Math.min(n, ids.size()));
    }

    static boolean needsParams(String workloadKey) {
        return !workloadKey.equals("aggregation");
    }

    static Runnable makeQueryFn(String platform, GraphClient client, String workloadKey, List<Long> idPool) {
        Random rnd = new Random();

        if (Workloads.BOLT_PLATFORMS.contains(platform)) {
            BoltClient bc = (BoltClient) client;
            String q = Workloads.CYPHER.get(workloadKey);
            return () -> {
                long id = idPool.get(rnd.nextInt(idPool.size()));
                Map<String, Object> params = needsParams(workloadKey)
                        ? (workloadKey.equals("filtered_lookup")
                            ? Map.of("group", id % 100)
                            : Map.of("id", id))
                        : Map.of();
                bc.run(q, params);
            };
        }

        if (platform.equals("arangodb")) {
            ArangoDBGraphClient ac = (ArangoDBGraphClient) client;
            String q = Workloads.AQL.get(workloadKey);
            return () -> {
                long id = idPool.get(rnd.nextInt(idPool.size()));
                Map<String, Object> bind = needsParams(workloadKey)
                        ? (workloadKey.equals("filtered_lookup")
                            ? Map.of("group", id % 100)
                            : Map.of("id", id))
                        : Map.of();
                ac.run(q, bind, JsonObject.class).forEachRemaining(x -> {});
            };
        }

        if (platform.equals("dgraph")) {
            DgraphGraphClient dc = (DgraphGraphClient) client;
            String q = Workloads.DQL.get(workloadKey);
            return () -> {
                long id = idPool.get(rnd.nextInt(idPool.size()));
                Map<String, String> vars = needsParams(workloadKey)
                        ? (workloadKey.equals("filtered_lookup")
                            ? Map.of("$group", String.valueOf(id % 100))
                            : Map.of("$id", String.valueOf(id)))
                        : Map.of();
                dc.query(q, vars);
            };
        }

        throw new IllegalArgumentException(platform);
    }

    public static Map<String, Object> runAllReads(String platform) throws Exception {
        try (GraphClient client = Connect.getClient(platform)) {
            List<Long> idPool = sampleIds(200);

            Map<String, Object> results = new LinkedHashMap<>();
            for (String key : WORKLOAD_KEYS) {
                System.out.printf("[%s] running %s (%d iterations, %d warmup)...%n",
                        platform, key, ITERATIONS, WARMUP);
                Runnable fn = makeQueryFn(platform, client, key, idPool);
                List<Double> latencies = Common.runMeasured(fn, ITERATIONS, WARMUP);
                Map<String, Object> pct = Common.percentiles(latencies, 50, 95);
                results.put(key, pct);
                System.out.printf("    p50=%sms p95=%sms%n", pct.get("p50"), pct.get("p95"));
            }

            Common.saveResult(platform, "reads", results);
            return results;
        }
    }

    public static void main(String[] args) throws Exception {
        runAllReads(args.length > 0 ? args[0] : "neo4j");
    }
}
