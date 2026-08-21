package com.wexa.benchmark;

import com.wexa.benchmark.clients.ArangoDBGraphClient;
import com.wexa.benchmark.clients.BoltClient;
import com.wexa.benchmark.clients.DgraphGraphClient;
import com.wexa.benchmark.clients.GraphClient;
import io.github.cdimascio.dotenv.Dotenv;

public class Connect {

    private static final Dotenv DOTENV = Dotenv.configure()
            .ignoreIfMissing()
            .ignoreIfMalformed()
            .load();

    private static String env(String key, String fallback) {
        String fromEnv = System.getenv(key);
        if (fromEnv != null && !fromEnv.isBlank()) return fromEnv;
        String fromDotenv = DOTENV.get(key);
        if (fromDotenv != null && !fromDotenv.isBlank()) return fromDotenv;
        return fallback;
    }

    private static String require(String key) {
        String v = env(key, null);
        if (v == null) {
            throw new IllegalStateException("Missing required env var: " + key +
                    " (set it in .env or the environment)");
        }
        return v;
    }

    public static GraphClient getClient(String platform) {
        switch (platform) {
            case "cognodb":
                return new BoltClient(
                        require("COGNODB_URI"),
                        env("COGNODB_USER", "cognodb"),
                        require("COGNODB_PASSWORD"));
            case "neo4j":
                return new BoltClient(
                        env("NEO4J_URI", "bolt://localhost:7687"),
                        env("NEO4J_USER", "neo4j"),
                        env("NEO4J_PASSWORD", "benchmarkpass"));
            case "memgraph":
                return new BoltClient(
                        env("MEMGRAPH_URI", "bolt://localhost:7688"),
                        env("MEMGRAPH_USER", ""),
                        env("MEMGRAPH_PASSWORD", ""));
            case "arangodb":
                return new ArangoDBGraphClient(
                        env("ARANGODB_HOST", "localhost"),
                        Integer.parseInt(env("ARANGODB_PORT", "8529")),
                        env("ARANGODB_USER", "root"),
                        env("ARANGODB_PASSWORD", "benchmarkpass"),
                        env("ARANGODB_DB", "benchmark"));
            case "dgraph":
                return new DgraphGraphClient(
                        env("DGRAPH_HOST", "localhost"),
                        Integer.parseInt(env("DGRAPH_PORT", "9080")));
            default:
                throw new IllegalArgumentException("Unknown platform: " + platform);
        }
    }
}
