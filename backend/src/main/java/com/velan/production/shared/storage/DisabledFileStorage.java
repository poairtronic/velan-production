package com.velan.production.shared.storage;

import com.velan.production.shared.error.ServiceUnavailableException;
import java.io.InputStream;

public class DisabledFileStorage implements FileStoragePort {

    private static final String MSG = "File storage is not configured";

    @Override
    public void put(String key, InputStream data, long contentLength, String contentType) {
        throw new ServiceUnavailableException(MSG);
    }

    @Override
    public InputStream get(String key) {
        throw new ServiceUnavailableException(MSG);
    }

    @Override
    public void delete(String key) {
        throw new ServiceUnavailableException(MSG);
    }

    @Override
    public boolean exists(String key) {
        throw new ServiceUnavailableException(MSG);
    }
}
