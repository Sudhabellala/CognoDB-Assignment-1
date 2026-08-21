package com.wexa.benchmark.clients;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import io.dgraph.DgraphClient;
import io.dgraph.DgraphGrpc;
import io.dgraph.DgraphProto;
import io.dgraph.DgraphProto.Mutation;
import io.dgraph.DgraphProto.Operation;
import io.dgraph.DgraphProto.Response;
import io.dgraph.TxnConflictException;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import com.wexa.benchmark.Models.Edge;
import com.wexa.benchmark.Models.Node;

import java.nio.charset.StandardCharsets;
import java.util.*;

/** Dgraph client (DQL over gRPC). */
public class DgraphGraphClient implements GraphClient {

    public final DgraphClient client;
    private final ManagedChannel channel;
    private final Gson gson = new Gson();
    /** external id -> Dgraph-assigned uid */
    public final Map<Long, String> uidMap = new HashMap<>();

    public DgraphGraphClient(String host, int port) {
        this.channel = ManagedChannelBuilder.forAddress(host, port).usePlaintext().build();
        DgraphGrpc.DgraphStub stub = DgraphGrpc.newStub(channel);
        this.client = new DgraphClient(stub);
    }

    @Override
    public void close() {
        channel.shutdown();
    }

    @Override
    public void setupSchema() {
        String schema = "id: int @index(int) @upsert .\n" +
                "group: int @index(int) .\n" +
                "sent_email: [uid] @reverse .\n";
        client.alter(Operation.newBuilder().setSchema(schema).build());
    }

    @Override
    public void bulkLoadNodes(List<Node> nodes, int batchSize) {
        uidMap.clear();
        for (int i = 0; i < nodes.size(); i += batchSize) {
            List<Node> batch = nodes.subList(i, Math.min(i + batchSize, nodes.size()));
            JsonArray arr = new JsonArray();
            for (Node n : batch) {
                JsonObject obj = new JsonObject();
                obj.addProperty("id", n.id);
                obj.addProperty("group", n.group);
                arr.add(obj);
            }
            io.dgraph.DgraphClient.Transaction txn = client.newTransaction();
            try {
                Mutation mu = Mutation.newBuilder()
                        .setSetJson(com.google.protobuf.ByteString.copyFromUtf8(gson.toJson(arr)))
                        .build();
                Response res = txn.mutate(mu);
                txn.commit();
                // Dgraph returns blank-node -> uid map; blank nodes are assigned "blank-0", "blank-1"... in order
                Map<String, String> uids = res.getUidsMap();
                List<String> assigned = new ArrayList<>(uids.values());
                for (int j = 0; j < batch.size() && j < assigned.size(); j++) {
                    uidMap.put(batch.get(j).id, assigned.get(j));
                }
            } finally {
                txn.discard();
            }
        }
        if (uidMap.size() < nodes.size()) {
            resolveMissingUids(nodes);
        }
    }

    private void resolveMissingUids(List<Node> nodes) {
        List<Long> missing = new ArrayList<>();
        for (Node n : nodes) {
            if (!uidMap.containsKey(n.id)) missing.add(n.id);
        }
        for (int i = 0; i < missing.size(); i += 1000) {
            List<Long> batch = missing.subList(i, Math.min(i + 1000, missing.size()));
            StringBuilder ids = new StringBuilder("[");
            for (int j = 0; j < batch.size(); j++) {
                if (j > 0) ids.append(",");
                ids.append(batch.get(j));
            }
            ids.append("]");
            String query = "{ q(func: type(Person)) @filter(eq(id, " + ids + ")) { uid id } }";
            Response resp = client.newReadOnlyTransaction().query(query);
            JsonObject data = gson.fromJson(resp.getJson().toString(StandardCharsets.UTF_8), JsonObject.class);
            for (var el : data.getAsJsonArray("q")) {
                JsonObject row = el.getAsJsonObject();
                uidMap.put(row.get("id").getAsLong(), row.get("uid").getAsString());
            }
        }
    }

    @Override
    public void bulkLoadEdges(List<Edge> edges, int batchSize) {
        for (int i = 0; i < edges.size(); i += batchSize) {
            List<Edge> batch = edges.subList(i, Math.min(i + batchSize, edges.size()));
            StringBuilder nquads = new StringBuilder();
            for (Edge e : batch) {
                String srcUid = uidMap.get(e.src);
                String dstUid = uidMap.get(e.dst);
                if (srcUid == null || dstUid == null) continue;
                nquads.append("<").append(srcUid).append("> <sent_email> <")
                        .append(dstUid).append("> .\n");
            }
            io.dgraph.DgraphClient.Transaction txn = client.newTransaction();
            try {
                Mutation mu = Mutation.newBuilder()
                        .setSetNquads(com.google.protobuf.ByteString.copyFromUtf8(nquads.toString()))
                        .build();
                txn.mutate(mu);
                txn.commit();
            } finally {
                txn.discard();
            }
        }
    }

    public JsonObject query(String dql, Map<String, String> variables) {
        Response resp = (variables == null || variables.isEmpty())
                ? client.newReadOnlyTransaction().query(dql)
                : client.newReadOnlyTransaction().queryWithVars(dql, variables);
        return gson.fromJson(resp.getJson().toString(StandardCharsets.UTF_8), JsonObject.class);
    }

    @Override
    public void clear() {
        client.alter(Operation.newBuilder().setDropAll(true).build());
    }
}
