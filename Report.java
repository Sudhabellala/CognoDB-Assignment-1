package com.wexa.benchmark;

import java.util.*;

public class Report {

    static final List<String> PLATFORM_ORDER = List.of("cognodb", "neo4j", "memgraph", "arangodb", "dgraph");
    static final Map<String, String> PLATFORM_LABEL = Map.of(
            "cognodb", "CognoDB", "neo4j", "Neo4j", "memgraph", "Memgraph",
            "arangodb", "ArangoDB", "dgraph", "Dgraph");

    @SuppressWarnings("unchecked")
    static Map<String, Map<String, Map<String, Object>>> indexResults() {
        Map<String, Map<String, Map<String, Object>>> byPlatform = new LinkedHashMap<>();
        for (Map<String, Object> r : Common.loadAllResults()) {
            String platform = (String) r.get("platform");
            String workload = (String) r.get("workload");
            byPlatform.computeIfAbsent(platform, k -> new LinkedHashMap<>()).put(workload, r);
        }
        return byPlatform;
    }

    static String fmt(Object v) {
        return v == null ? "-" : String.valueOf(v);
    }

    @SuppressWarnings("unchecked")
    static String renderLoadTable(Map<String, Map<String, Map<String, Object>>> byPlatform) {
        StringBuilder sb = new StringBuilder("### Data loading\n\n");
        sb.append("| Platform | Nodes/sec | Relationships/sec | Total load time |\n");
        sb.append("|---|---|---|---|\n");
        for (String p : PLATFORM_ORDER) {
            Map<String, Object> r = byPlatform.getOrDefault(p, Map.of()).get("load");
            if (r == null) {
                sb.append("| ").append(PLATFORM_LABEL.get(p)).append(" | - | - | - |\n");
                continue;
            }
            sb.append("| ").append(PLATFORM_LABEL.get(p)).append(" | ")
                    .append(fmt(r.get("nodes_per_second"))).append(" | ")
                    .append(fmt(r.get("relationships_per_second"))).append(" | ")
                    .append(fmt(r.get("total_load_seconds"))).append("s |\n");
        }
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    static String renderTraversalTable(Map<String, Map<String, Map<String, Object>>> byPlatform) {
        StringBuilder sb = new StringBuilder("### Traversals (p50 / p95, ms)\n\n");
        sb.append("| Platform | 1-hop | 2-hop | 3-hop |\n|---|---|---|---|\n");
        for (String p : PLATFORM_ORDER) {
            Map<String, Object> reads = byPlatform.getOrDefault(p, Map.of()).get("reads");
            sb.append("| ").append(PLATFORM_LABEL.get(p)).append(" |");
            for (String key : List.of("traversal_1hop", "traversal_2hop", "traversal_3hop")) {
                Map<String, Object> r = reads == null ? null : (Map<String, Object>) reads.get(key);
                if (r == null) sb.append(" - |");
                else sb.append(" ").append(fmt(r.get("p50"))).append(" / ").append(fmt(r.get("p95"))).append(" |");
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    static String renderLookupTable(Map<String, Map<String, Map<String, Object>>> byPlatform) {
        StringBuilder sb = new StringBuilder("### Lookups (p50 / p95, ms)\n\n");
        sb.append("| Platform | Point lookup | Filtered lookup |\n|---|---|---|\n");
        for (String p : PLATFORM_ORDER) {
            Map<String, Object> reads = byPlatform.getOrDefault(p, Map.of()).get("reads");
            sb.append("| ").append(PLATFORM_LABEL.get(p)).append(" |");
            for (String key : List.of("point_lookup", "filtered_lookup")) {
                Map<String, Object> r = reads == null ? null : (Map<String, Object>) reads.get(key);
                if (r == null) sb.append(" - |");
                else sb.append(" ").append(fmt(r.get("p50"))).append(" / ").append(fmt(r.get("p95"))).append(" |");
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    static String renderAggregationTable(Map<String, Map<String, Map<String, Object>>> byPlatform) {
        StringBuilder sb = new StringBuilder("### Aggregation (p50 / p95, ms)\n\n");
        sb.append("| Platform | p50 | p95 |\n|---|---|---|\n");
        for (String p : PLATFORM_ORDER) {
            Map<String, Object> reads = byPlatform.getOrDefault(p, Map.of()).get("reads");
            Map<String, Object> r = reads == null ? null : (Map<String, Object>) reads.get("aggregation");
            if (r == null) sb.append("| ").append(PLATFORM_LABEL.get(p)).append(" | - | - |\n");
            else sb.append("| ").append(PLATFORM_LABEL.get(p)).append(" | ")
                    .append(fmt(r.get("p50"))).append(" | ").append(fmt(r.get("p95"))).append(" |\n");
        }
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    static String renderMixedTable(Map<String, Map<String, Map<String, Object>>> byPlatform) {
        StringBuilder sb = new StringBuilder("### Mixed concurrent workload (queries/sec)\n\n");
        sb.append("| Platform | 1 client | 10 clients | 40 clients |\n|---|---|---|---|\n");
        for (String p : PLATFORM_ORDER) {
            Map<String, Object> mixed = byPlatform.getOrDefault(p, Map.of()).get("mixed");
            List<Map<String, Object>> levels = mixed == null
                    ? List.of() : (List<Map<String, Object>>) mixed.get("levels");
            Map<Double, Object> byLevel = new HashMap<>();
            for (Map<String, Object> lvl : levels) {
                byLevel.put(((Number) lvl.get("concurrency")).doubleValue(), lvl.get("queries_per_second"));
            }
            sb.append("| ").append(PLATFORM_LABEL.get(p)).append(" |");
            for (double lvl : new double[]{1, 10, 40}) {
                sb.append(" ").append(fmt(byLevel.get(lvl))).append(" |");
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    public static void main(String[] args) {
        Map<String, Map<String, Map<String, Object>>> byPlatform = indexResults();
        System.out.println(renderLoadTable(byPlatform));
        System.out.println(renderTraversalTable(byPlatform));
        System.out.println(renderLookupTable(byPlatform));
        System.out.println(renderAggregationTable(byPlatform));
        System.out.println(renderMixedTable(byPlatform));
    }
}
