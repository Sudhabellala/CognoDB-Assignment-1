package com.wexa.benchmark;

/**
 * Single entry point for the fat jar. Dispatches to the right subcommand.
 *
 * Usage:
 *   java -jar graph-benchmark.jar download-dataset
 *   java -jar graph-benchmark.jar load <platform>
 *   java -jar graph-benchmark.jar bench-reads <platform>
 *   java -jar graph-benchmark.jar bench-mixed <platform>
 *   java -jar graph-benchmark.jar run-all [platform...] [--skip-load]
 *   java -jar graph-benchmark.jar report
 */
public class Main {
    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            printUsage();
            return;
        }
        String cmd = args[0];
        String[] rest = java.util.Arrays.copyOfRange(args, 1, args.length);

        switch (cmd) {
            case "download-dataset":
                DownloadDataset.main(rest);
                break;
            case "load":
                Load.main(rest);
                break;
            case "bench-reads":
                BenchReads.main(rest);
                break;
            case "bench-mixed":
                BenchMixed.main(rest);
                break;
            case "run-all":
                RunAll.main(rest);
                break;
            case "report":
                Report.main(rest);
                break;
            default:
                System.out.println("Unknown command: " + cmd);
                printUsage();
        }
    }

    static void printUsage() {
        System.out.println("""
                Usage: java -jar graph-benchmark.jar <command> [args]

                Commands:
                  download-dataset                 Download and preprocess the SNAP dataset
                  load <platform>                  Load data into one platform
                  bench-reads <platform>            Run traversal/lookup/aggregation benchmarks
                  bench-mixed <platform>            Run mixed concurrent read/write benchmark
                  run-all [platform...] [--skip-load]   Run the full suite
                  report                            Print markdown result tables

                Platforms: cognodb, neo4j, memgraph, arangodb, dgraph
                """);
    }
}
