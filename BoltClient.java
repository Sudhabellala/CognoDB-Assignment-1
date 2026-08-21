package com.wexa.benchmark.clients;

import com.wexa.benchmark.Models.Edge;
import com.wexa.benchmark.Models.Node;
import org.neo4j.driver.*;
import org.neo4j.driver.Record;

import java.util.*;

/**
 * Shared client for every Bolt+Cypher platform: CognoDB, Neo4j, Memgraph.
 * This is the whole point of picking Neo4j/Memgraph as comparisons -- identical
 * client code and identical Cypher text run against all three, which removes
 * "query translation fairness" as a variable for this half of the comparison.
 */
public class BoltClient implements GraphClient {

    public final Driver driver;

    public BoltClient(String uri, String user, String password) {
        this.driver = (user != null && !user.isBlank())
                ? GraphDatabase.driver(uri, AuthTokens.basic(user, password))
                : GraphDatabase.driver(uri);
    }

    @Override
    public void close() {
        driver.close();
    }

    public List<Record> run(String query, Map<String, Object> params) {
        try (Session session = driver.session()) {
            return session.run(query, params == null ? Map.of() : params).list();
        }
    }

    public void runWrite(String query, Map<String, Object> params) {
        try (Session session = driver.session()) {
            session.executeWrite(tx -> {
                tx.run(query, params == null ? Map.of() : params).consume();
                return null;
            });
        }
    }

    @Override
    public void setupSchema() {
        run("CREATE CONSTRAINT node_id_unique IF NOT EXISTS " +
                "FOR (n:Person) REQUIRE n.id IS UNIQUE", null);
        run("CREATE INDEX node_group_idx IF NOT EXISTS " +
                "FOR (n:Person) ON (n.group)", null);
    }

    @Override
    public void bulkLoadNodes(List<Node> nodes, int batchSize) {
        for (int i = 0; i < nodes.size(); i += batchSize) {
            List<Node> batch = nodes.subList(i, Math.min(i + batchSize, nodes.size()));
            List<Map<String, Object>> rows = new ArrayList<>();
            for (Node n : batch) {
                rows.add(Map.of("id", n.id, "group", n.group));
            }
            runWrite("UNWIND $rows AS row " +
                    "MERGE (n:Person {id: row.id}) SET n.group = row.group",
                    Map.of("rows", rows));
        }
    }

    @Override
    public void bulkLoadEdges(List<Edge> edges, int batchSize) {
        for (int i = 0; i < edges.size(); i += batchSize) {
            List<Edge> batch = edges.subList(i, Math.min(i + batchSize, edges.size()));
            List<Map<String, Object>> rows = new ArrayList<>();
            for (Edge e : batch) {
                rows.add(Map.of("src", e.src, "dst", e.dst));
            }
            runWrite("UNWIND $rows AS row " +
                    "MATCH (a:Person {id: row.src}), (b:Person {id: row.dst}) " +
                    "MERGE (a)-[:SENT_EMAIL]->(b)",
                    Map.of("rows", rows));
        }
    }

    @Override
    public void clear() {
        // Best-effort wipe for re-runs; on a truly fresh free-tier instance this is a no-op.
        run("MATCH (n) DETACH DELETE n", null);
    }
}
