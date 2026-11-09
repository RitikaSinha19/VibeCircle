package com.vibecircle.common;

import java.util.List;

/** Offset page. Callers fetch size+1 rows so hasNext needs no COUNT query. */
public record PageResult<T>(List<T> items, int page, int size, boolean hasNext) {

    public static final int MAX_SIZE = 50;

    public static int safePage(int page) {
        return Math.max(0, page);
    }

    public static int safeSize(int size) {
        return Math.max(1, Math.min(size, MAX_SIZE));
    }

    /** rows must have been fetched with LIMIT size + 1 */
    public static <T> PageResult<T> of(List<T> rows, int page, int size) {
        boolean hasNext = rows.size() > size;
        return new PageResult<>(List.copyOf(hasNext ? rows.subList(0, size) : rows), page, size, hasNext);
    }
}
