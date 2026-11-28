package com.velan.production.shared.error;

import org.springframework.http.HttpStatus;

public class BusinessRuleException extends ApiException {

    public BusinessRuleException(String message) {
        this(ErrorCode.ERR_BUSINESS_RULE, message);
    }

    public BusinessRuleException(ErrorCode code, String message) {
        super(HttpStatus.UNPROCESSABLE_CONTENT, code, message);
    }
}
