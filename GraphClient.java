package com.wexa.benchmark.clients;

import com.wexa.benchmark.Models.Edge;
import com.wexa.benchmark.Models.Node;

import java.util.List;
import java.util.Map;

/** Common surface every platform client implements, so loaders/benchmarks stay platform-agnostic
 *  wherever possible. Read/write query execution for benchmarks is still platform-specific
 *  (different query languages) -- see the Workloads classes and bench runners. */
public interface GraphClient extends AutoCloseable {

    void setupSchema();

    void bulkLoadNodes(List<Node> nodes, int batchSize);

    void bulkLoadEdges(List<Edge> edges, int batchSize);

    void clear();

    @Override
    void close();
}
