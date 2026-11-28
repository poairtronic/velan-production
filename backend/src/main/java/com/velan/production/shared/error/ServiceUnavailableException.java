package com.velan.production.shared.error;

import org.springframework.http.HttpStatus;

public class ServiceUnavailableException extends ApiException {

    public ServiceUnavailableException(String message) {
        super(HttpStatus.SERVICE_UNAVAILABLE, ErrorCode.ERR_UNAVAILABLE, message);
    }
}
