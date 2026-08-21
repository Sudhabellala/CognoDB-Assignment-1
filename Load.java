package com.wexa.benchmark;

import com.wexa.benchmark.Models.Edge;
import com.wexa.benchmark.Models.Node;
import com.wexa.benchmark.clients.GraphClient;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

import java.io.FileReader;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

public class Load {

    static final Path DATA_DIR = Paths.get("data");

    public static List<Node> readNodes() throws Exception {
        List<Node> nodes = new ArrayList<>();
        try (CSVParser parser = CSVParser.parse(new FileReader(DATA_DIR.resolve("nodes.csv").toFile()),
                CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).build())) {
            for (CSVRecord r : parser) {
                nodes.add(new Node(Long.parseLong(r.get("id")), Long.parseLong(r.get("group"))));
            }
        }
        return nodes;
    }

    public static List<Edge> readEdges() throws Exception {
        List<Edge> edges = new ArrayList<>();
        try (CSVParser parser = CSVParser.parse(new FileReader(DATA_DIR.resolve("edges.csv").toFile()),
                CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).build())) {
            for (CSVRecord r : parser) {
                edges.add(new Edge(Long.parseLong(r.get("src")), Long.parseLong(r.get("dst"))));
            }
        }
        return edges;
    }

    public static Map<String, Object> loadPlatform(String platform, boolean clearFirst) throws Exception {
        System.out.println("[" + platform + "] loading...");
        try (GraphClient client = Connect.getClient(platform)) {
            List<Node> nodes = readNodes();
            List<Edge> edges = readEdges();

            if (clearFirst) {
                System.out.println("[" + platform + "] clearing existing data...");
                client.clear();
            }
            client.setupSchema();

            long t0 = System.nanoTime();
            client.bulkLoadNodes(nodes, 1000);
            long t1 = System.nanoTime();
            client.bulkLoadEdges(edges, 1000);
            long t2 = System.nanoTime();

            double nodeSeconds = (t1 - t0) / 1_000_000_000.0;
            double edgeSeconds = (t2 - t1) / 1_000_000_000.0;
            double totalSeconds = (t2 - t0) / 1_000_000_000.0;

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("node_count", nodes.size());
            result.put("edge_count", edges.size());
            result.put("node_load_seconds", round3(nodeSeconds));
            result.put("edge_load_seconds", round3(edgeSeconds));
            result.put("total_load_seconds", round3(totalSeconds));
            result.put("nodes_per_second", nodeSeconds > 0 ? round1(nodes.size() / nodeSeconds) : null);
            result.put("relationships_per_second", edgeSeconds > 0 ? round1(edges.size() / edgeSeconds) : null);

            Common.saveResult(platform, "load", result);
            System.out.printf("[%s] loaded %,d nodes, %,d edges in %.1fs (%s nodes/s, %s rels/s)%n",
                    platform, nodes.size(), edges.size(), totalSeconds,
                    result.get("nodes_per_second"), result.get("relationships_per_second"));
            return result;
        }
    }

    private static double round3(double v) { return Math.round(v * 1000.0) / 1000.0; }
    private static double round1(double v) { return Math.round(v * 10.0) / 10.0; }

    public static void main(String[] args) throws Exception {
        loadPlatform(args.length > 0 ? args[0] : "neo4j", true);
    }
}
