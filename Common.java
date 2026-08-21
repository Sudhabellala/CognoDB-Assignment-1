package com.wexa.benchmark;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class Common {

    public static final Path RESULTS_DIR = Paths.get("results");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** Runs `fn` `warmup` times (discarded), then `iterations` times, timing each call in ms. */
    public static List<Double> runMeasured(Runnable fn, int iterations, int warmup) {
        for (int i = 0; i < warmup; i++) {
            fn.run();
        }
        List<Double> latencies = new ArrayList<>(iterations);
        for (int i = 0; i < iterations; i++) {
            long start = System.nanoTime();
            fn.run();
            long elapsedNanos = System.nanoTime() - start;
            latencies.add(elapsedNanos / 1_000_000.0);
        }
        return latencies;
    }

    /** Nearest-rank percentiles. Returns a map with keys "p50", "p95", "mean", "n". */
    public static Map<String, Object> percentiles(List<Double> latenciesMs, int... ps) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (latenciesMs.isEmpty()) {
            for (int p : ps) out.put("p" + p, null);
            out.put("mean", null);
            out.put("n", 0);
            return out;
        }
        List<Double> sorted = latenciesMs.stream().sorted().collect(Collectors.toList());
        for (int p : ps) {
            int k = (int) Math.round(p / 100.0 * (sorted.size() - 1));
            k = Math.max(0, Math.min(sorted.size() - 1, k));
            out.put("p" + p, round3(sorted.get(k)));
        }
        double mean = sorted.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        out.put("mean", round3(mean));
        out.put("n", sorted.size());
        return out;
    }

    private static double round3(double v) {
        return Math.round(v * 1000.0) / 1000.0;
    }

    public static void saveResult(String platform, String workload, Map<String, Object> payload) {
        try {
            Files.createDirectories(RESULTS_DIR);
            Path path = RESULTS_DIR.resolve(platform + "__" + workload + ".json");
            Map<String, Object> full = new LinkedHashMap<>();
            full.put("platform", platform);
            full.put("workload", workload);
            full.putAll(payload);
            try (FileWriter w = new FileWriter(path.toFile())) {
                GSON.toJson(full, w);
            }
            System.out.println("  -> wrote " + path);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @SuppressWarnings("unchecked")
    public static List<Map<String, Object>> loadAllResults() {
        List<Map<String, Object>> out = new ArrayList<>();
        if (!Files.isDirectory(RESULTS_DIR)) return out;
        try {
            List<Path> files = Files.list(RESULTS_DIR)
                    .filter(p -> p.toString().endsWith(".json"))
                    .sorted()
                    .collect(Collectors.toList());
            for (Path f : files) {
                try (var reader = Files.newBufferedReader(f)) {
                    Map<String, Object> parsed = GSON.fromJson(reader, Map.class);
                    out.add(parsed);
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return out;
    }

    public static <T> T time(Supplier<T> fn, double[] elapsedMsOut) {
        long start = System.nanoTime();
        T result = fn.get();
        elapsedMsOut[0] = (System.nanoTime() - start) / 1_000_000.0;
        return result;
    }
}
