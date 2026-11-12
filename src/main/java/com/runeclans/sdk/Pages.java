package com.runeclans.sdk;

import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.function.Function;

/** Walks a paged list page by page, following {@code nextCursor}. Each page is fetched when the loop reaches it. */
public final class Pages {
    private Pages() {}

    /**
     * Every item of a paged list. {@code fetch} gets the cursor (null for the first page); {@code items} and {@code next} read a page.
     * A call that fails throws its {@link RuneClanException} out of the loop.
     */
    public static <P, T> Iterable<T> iterate(Function<String, ApiAction<P>> fetch, Function<P, List<T>> items, Function<P, String> next) {
        return () -> new Iterator<>() {
            private Iterator<T> current = List.<T>of().iterator();
            private String cursor;
            private boolean last;

            @Override
            public boolean hasNext() {
                while (!current.hasNext() && !last) {
                    P page = fetch.apply(cursor).complete();
                    List<T> list = page == null ? null : items.apply(page);
                    current = (list == null ? List.<T>of() : list).iterator();
                    String n = page == null ? null : next.apply(page);
                    last = n == null || n.isEmpty() || n.equals(cursor);
                    cursor = n;
                }
                return current.hasNext();
            }

            @Override
            public T next() {
                if (!hasNext()) throw new NoSuchElementException();
                return current.next();
            }
        };
    }
}
