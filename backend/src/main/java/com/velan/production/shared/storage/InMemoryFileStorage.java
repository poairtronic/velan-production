package com.velan.production.shared.storage;

import com.velan.production.shared.error.NotFoundException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryFileStorage implements FileStoragePort {

    private final Map<String, byte[]> store = new ConcurrentHashMap<>();

    @Override
    public void put(String key, InputStream data, long contentLength, String contentType) {
        try {
            store.put(key, data.readAllBytes());
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to read stream", ex);
        }
    }

    @Override
    public InputStream get(String key) {
        byte[] bytes = store.get(key);
        if (bytes == null) {
            throw new NotFoundException("File not found");
        }
        return new ByteArrayInputStream(bytes);
    }

    @Override
    public void delete(String key) {
        store.remove(key);
    }

    @Override
    public boolean exists(String key) {
        return store.containsKey(key);
    }
}
