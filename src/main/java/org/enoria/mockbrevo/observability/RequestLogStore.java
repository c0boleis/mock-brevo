package org.enoria.mockbrevo.observability;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Component;

@Component
public class RequestLogStore {

    private static final int MAX_ENTRIES = 500;

    private final Deque<RequestLogEntry> buffer = new ArrayDeque<>(MAX_ENTRIES);
    private final AtomicLong sequence = new AtomicLong();

    public long nextId() {
        return sequence.incrementAndGet();
    }

    public synchronized void append(RequestLogEntry entry) {
        if (buffer.size() >= MAX_ENTRIES) {
            buffer.pollFirst();
        }
        buffer.addLast(entry);
    }

    public synchronized List<RequestLogEntry> snapshot(int limit, String apiKeyPreviewFilter) {
        List<RequestLogEntry> out = new ArrayList<>(Math.min(limit, buffer.size()));
        var it = buffer.descendingIterator();
        while (it.hasNext() && out.size() < limit) {
            RequestLogEntry e = it.next();
            if (apiKeyPreviewFilter != null && !apiKeyPreviewFilter.isBlank()
                    && (e.apiKeyPreview() == null || !e.apiKeyPreview().contains(apiKeyPreviewFilter))) {
                continue;
            }
            out.add(e);
        }
        return out;
    }

    public synchronized Optional<RequestLogEntry> findById(long id) {
        for (RequestLogEntry e : buffer) {
            if (e.id() == id) return Optional.of(e);
        }
        return Optional.empty();
    }

    public synchronized int size() {
        return buffer.size();
    }
}
