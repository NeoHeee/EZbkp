package com.neo.ezaccounting;

import java.util.LinkedHashMap;
import java.util.Map;

final class PagePositionCache {
    static final class Position {
        final int x;
        final int y;

        Position(int x, int y) {
            this.x = Math.max(0, x);
            this.y = Math.max(0, y);
        }
    }

    private static final int MAX_ENTRIES = 8;
    private final LinkedHashMap<String, Position> positions =
            new LinkedHashMap<String, Position>(MAX_ENTRIES, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Position> eldest) {
                    return size() > MAX_ENTRIES;
                }
            };

    synchronized void save(String url, int x, int y) {
        if (url == null || url.trim().isEmpty()) return;
        positions.put(url, new Position(x, y));
    }

    synchronized Position get(String url) {
        return url == null ? null : positions.get(url);
    }

    synchronized void clear() {
        positions.clear();
    }
}
