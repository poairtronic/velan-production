package com.velan.production.shared.storage;

import java.io.InputStream;
import java.net.URI;
import java.time.Duration;
import java.util.Optional;

public interface FileStoragePort {

    void put(String key, InputStream data, long contentLength, String contentType);

    InputStream get(String key);

    void delete(String key);

    boolean exists(String key);

    default Optional<URI> presignedGetUrl(String key, Duration ttl) {
        return Optional.empty();
    }
}
