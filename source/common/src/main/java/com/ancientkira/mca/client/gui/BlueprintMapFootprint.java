package com.ancientkira.mca.client.gui;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

final class BlueprintMapFootprint {
    private BlueprintMapFootprint() {
    }

    static Set<Cell> rectangle(int minX, int minZ, int maxX, int maxZ) {
        Set<Cell> cells = new LinkedHashSet<>();
        for (int z = minZ; z <= maxZ; z++) {
            for (int x = minX; x <= maxX; x++) {
                cells.add(new Cell(x, z));
            }
        }
        return Set.copyOf(cells);
    }

    static Shape shape(Set<Cell> cells) {
        Map<Integer, List<Integer>> rows = new TreeMap<>();
        for (Cell cell : cells) {
            rows.computeIfAbsent(cell.z(), ignored -> new ArrayList<>()).add(cell.x());
        }

        List<RowSpan> spans = new ArrayList<>();
        for (Map.Entry<Integer, List<Integer>> row : rows.entrySet()) {
            List<Integer> xs = row.getValue().stream().distinct().sorted().toList();
            if (xs.isEmpty()) {
                continue;
            }

            int start = xs.getFirst();
            int previous = start;
            for (int i = 1; i < xs.size(); i++) {
                int x = xs.get(i);
                if (x != previous + 1) {
                    spans.add(new RowSpan(row.getKey(), start, previous));
                    start = x;
                }
                previous = x;
            }
            spans.add(new RowSpan(row.getKey(), start, previous));
        }

        List<Edge> edges = new ArrayList<>();
        for (Cell cell : cells) {
            if (!cells.contains(new Cell(cell.x(), cell.z() - 1))) {
                edges.add(new Edge(cell.x(), cell.z(), cell.x() + 1, cell.z()));
            }
            if (!cells.contains(new Cell(cell.x(), cell.z() + 1))) {
                edges.add(new Edge(cell.x(), cell.z() + 1, cell.x() + 1, cell.z() + 1));
            }
            if (!cells.contains(new Cell(cell.x() - 1, cell.z()))) {
                edges.add(new Edge(cell.x(), cell.z(), cell.x(), cell.z() + 1));
            }
            if (!cells.contains(new Cell(cell.x() + 1, cell.z()))) {
                edges.add(new Edge(cell.x() + 1, cell.z(), cell.x() + 1, cell.z() + 1));
            }
        }

        edges.sort(Comparator.comparingInt(Edge::z0).thenComparingInt(Edge::x0).thenComparingInt(Edge::z1).thenComparingInt(Edge::x1));
        return new Shape(Set.copyOf(cells), List.copyOf(spans), List.copyOf(edges));
    }

    record Cell(int x, int z) {
    }

    record Edge(int x0, int z0, int x1, int z1) {
    }

    record RowSpan(int z, int minX, int maxX) {
    }

    record Shape(Set<Cell> cells, List<RowSpan> spans, List<Edge> edges) {
    }
}
