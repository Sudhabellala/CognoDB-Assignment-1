package com.wexa.benchmark;

public class Models {

    public static class Node {
        public final long id;
        public final long group;

        public Node(long id, long group) {
            this.id = id;
            this.group = group;
        }
    }

    public static class Edge {
        public final long src;
        public final long dst;

        public Edge(long src, long dst) {
            this.src = src;
            this.dst = dst;
        }
    }
}
