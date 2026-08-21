package com.wexa.benchmark;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;

import java.io.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.zip.GZIPInputStream;

/**
 * Downloads the SNAP email-Enron dataset, dedups edges, assigns a synthetic
 * `group` property to each node (used by the filtered-lookup workload, since
 * the raw dataset has no node properties), and writes flat CSVs that every
 * loader reads from.
 *
 * Source: https://snap.stanford.edu/data/email-Enron.html
 * 36,692 nodes / 367,662 directed edges.
 */
public class DownloadDataset {

    static final String SNAP_URL = "https://snap.stanford.edu/data/email-Enron.txt.gz";
    static final Path DATA_DIR = Paths.get("data");

    public static void main(String[] args) throws Exception {
        Files.createDirectories(DATA_DIR);
        System.out.println("Downloading " + SNAP_URL + " ...");
        byte[] gz = download(SNAP_URL);
        String text = gunzip(gz);

        TreeSet<Long> nodes = new TreeSet<>();
        LinkedHashSet<long[]> edgeSet = new LinkedHashSet<>();
        List<long[]> edges = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(new StringReader(text))) {
            String line;
            Set<Long> seenPairs = new HashSet<>();
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("#") || line.isBlank()) continue;
                String[] parts = line.trim().split("\\s+");
                if (parts.length != 2) continue;
                long src = Long.parseLong(parts[0]);
                long dst = Long.parseLong(parts[1]);
                if (src == dst) continue;
                long key = src * 10_000_000L + dst; // dedup key, dataset ids are small
                if (seenPairs.add(key)) {
                    edges.add(new long[]{src, dst});
                    nodes.add(src);
                    nodes.add(dst);
                }
            }
        }

        Path nodesPath = DATA_DIR.resolve("nodes.csv");
        Path edgesPath = DATA_DIR.resolve("edges.csv");

        try (CSVPrinter printer = new CSVPrinter(new FileWriter(nodesPath.toFile()),
                CSVFormat.DEFAULT.builder().setHeader("id", "group").build())) {
            for (long n : nodes) {
                printer.printRecord(n, n % 100);
            }
        }

        try (CSVPrinter printer = new CSVPrinter(new FileWriter(edgesPath.toFile()),
                CSVFormat.DEFAULT.builder().setHeader("src", "dst").build())) {
            for (long[] e : edges) {
                printer.printRecord(e[0], e[1]);
            }
        }

        System.out.printf("Nodes: %,d -> %s%n", nodes.size(), nodesPath);
        System.out.printf("Edges: %,d -> %s%n", edges.size(), edgesPath);
        if (edges.size() < 100_000 || edges.size() > 500_000) {
            System.out.println("WARNING: edge count is outside the assignment's recommended " +
                    "100k-500k range. Consider sampling down, or picking a different SNAP dataset.");
        }
    }

    static byte[] download(String url) throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url)).build();
        HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() != 200) {
            throw new IOException("Download failed: HTTP " + response.statusCode());
        }
        return response.body();
    }

    static String gunzip(byte[] gz) throws IOException {
        try (GZIPInputStream gis = new GZIPInputStream(new ByteArrayInputStream(gz));
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            gis.transferTo(out);
            return out.toString("UTF-8");
        }
    }
}
