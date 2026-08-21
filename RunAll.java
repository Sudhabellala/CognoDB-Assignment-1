package com.wexa.benchmark;

import com.wexa.benchmark.workloads.Workloads;

import java.util.*;

/**
 * Runs the full suite (load -> traversals/lookups/aggregation -> mixed workload)
 * for one or more platforms.
 *
 * Usage:
 *   java -jar graph-benchmark.jar run-all cognodb neo4j memgraph arangodb dgraph
 *   java -jar graph-benchmark.jar run-all neo4j                 # just one, while iterating
 *   java -jar graph-benchmark.jar run-all neo4j --skip-load     # reuse already-loaded data
 */
public class RunAll {

    public static void main(String[] args) throws Exception {
        List<String> platforms = new ArrayList<>();
        boolean skipLoad = false;

        for (String arg : args) {
            if (arg.equals("--skip-load")) {
                skipLoad = true;
            } else if (Workloads.ALL_PLATFORMS.contains(arg)) {
                platforms.add(arg);
            }
        }
        if (platforms.isEmpty()) {
            platforms = new ArrayList<>(Workloads.ALL_PLATFORMS);
            Collections.sort(platforms);
        }

        List<String[]> failures = new ArrayList<>();
        for (String platform : platforms) {
            System.out.println("\n" + "=".repeat(60));
            System.out.println(platform.toUpperCase());
            System.out.println("=".repeat(60));
            try {
                if (!skipLoad) {
                    Load.loadPlatform(platform, true);
                }
                BenchReads.runAllReads(platform);
                BenchMixed.runMixed(platform);
            } catch (Exception e) {
                System.out.println("[" + platform + "] FAILED: " + e.getMessage());
                e.printStackTrace();
                failures.add(new String[]{platform, String.valueOf(e.getMessage())});
            }
        }

        System.out.println("\n" + "=".repeat(60));
        System.out.println("DONE");
        System.out.println("=".repeat(60));
        if (!failures.isEmpty()) {
            System.out.println("The following platforms had failures (see README caveats section — " +
                    "record these honestly rather than omitting them):");
            for (String[] f : failures) {
                System.out.println("  - " + f[0] + ": " + f[1]);
            }
            System.exit(1);
        }
    }
}
