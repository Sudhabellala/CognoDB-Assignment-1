package com.wexa.benchmark.clients;

import com.arangodb.ArangoCollection;
import com.arangodb.ArangoCursor;
import com.arangodb.ArangoDB;
import com.arangodb.ArangoDatabase;
import com.arangodb.entity.EdgeDefinition;
import com.arangodb.model.CollectionCreateOptions;
import com.arangodb.model.PersistentIndexOptions;
import com.arangodb.model.DocumentImportOptions;
import com.wexa.benchmark.Models.Edge;
import com.wexa.benchmark.Models.Node;

import java.util.*;

/** ArangoDB client (AQL over HTTP). Uses a named graph so 1..N-hop traversal
 *  syntax (`FOR v IN 1..N OUTBOUND`) is available. */
public class ArangoDBGraphClient implements GraphClient {

    public final ArangoDatabase db;

    public ArangoDBGraphClient(String host, int port, String user, String password, String dbName) {
        ArangoDB arango = new ArangoDB.Builder()
                .host(host, port)
                .user(user)
                .password(password)
                .build();
        if (!arango.db(dbName).exists()) {
            arango.createDatabase(dbName);
        }
        this.db = arango.db(dbName);
    }

    @Override
    public void close() {
        // underlying ArangoDB connection pool is closed by the driver's shutdown hook
    }

    @Override
    public void setupSchema() {
        if (!db.collection("persons").exists()) {
            db.createCollection("persons");
        }
        if (!db.collection("sent_email").exists()) {
            db.createCollection("sent_email", new CollectionCreateOptions().type(com.arangodb.entity.CollectionType.EDGES));
        }
        if (!db.graph("email_graph").exists()) {
            db.createGraph("email_graph", List.of(
                    new EdgeDefinition().collection("sent_email")
                            .from("persons").to("persons")));
        }
        ArangoCollection persons = db.collection("persons");
        persons.ensurePersistentIndex(List.of("id"), new PersistentIndexOptions().unique(true));
        persons.ensurePersistentIndex(List.of("group"), new PersistentIndexOptions().unique(false));
    }

    @Override
    public void bulkLoadNodes(List<Node> nodes, int batchSize) {
        ArangoCollection persons = db.collection("persons");
        for (int i = 0; i < nodes.size(); i += batchSize) {
            List<Node> batch = nodes.subList(i, Math.min(i + batchSize, nodes.size()));
            List<Map<String, Object>> docs = new ArrayList<>();
            for (Node n : batch) {
                docs.add(Map.of("_key", String.valueOf(n.id), "id", n.id, "group", n.group));
            }
            persons.importDocuments(docs, new DocumentImportOptions()
                    .onDuplicate(DocumentImportOptions.OnDuplicate.ignore));
        }
    }

    @Override
    public void bulkLoadEdges(List<Edge> edges, int batchSize) {
        ArangoCollection sent = db.collection("sent_email");
        for (int i = 0; i < edges.size(); i += batchSize) {
            List<Edge> batch = edges.subList(i, Math.min(i + batchSize, edges.size()));
            List<Map<String, Object>> docs = new ArrayList<>();
            for (Edge e : batch) {
                docs.add(Map.of("_from", "persons/" + e.src, "_to", "persons/" + e.dst));
            }
            sent.importDocuments(docs, new DocumentImportOptions()
                    .onDuplicate(DocumentImportOptions.OnDuplicate.ignore));
        }
    }

    public <T> ArangoCursor<T> run(String aql, Map<String, Object> bindVars, Class<T> type) {
        return db.query(aql, bindVars, type);
    }

    @Override
    public void clear() {
        if (db.collection("persons").exists()) db.collection("persons").truncate();
        if (db.collection("sent_email").exists()) db.collection("sent_email").truncate();
    }
}
