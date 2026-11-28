package com.velan.production.integration.api;

import com.velan.production.vendor.api.domain.VendorDcStatus;
import java.util.Objects;

/**
 * Vendor DC return status notice pulled from RM.
 * Production does not track vendor expected-return or actual-return dates.
 */
public record VendorReturnNotice(
        String dcNumber,
        String sizeCardNo,
        String productName,
        String vendorName,
        String processName,
        int dispatchedQuantity,
        int receivedQuantity,
        VendorDcStatus status) {

    public VendorReturnNotice {
        if (dcNumber == null || dcNumber.isBlank()) {
            throw new IllegalArgumentException("dcNumber must not be blank");
        }
        if (sizeCardNo == null || sizeCardNo.isBlank()) {
            throw new IllegalArgumentException("sizeCardNo must not be blank");
        }
        if (productName == null || productName.isBlank()) {
            throw new IllegalArgumentException("productName must not be blank");
        }
        if (vendorName == null || vendorName.isBlank()) {
            throw new IllegalArgumentException("vendorName must not be blank");
        }
        if (processName == null || processName.isBlank()) {
            throw new IllegalArgumentException("processName must not be blank");
        }
        if (dispatchedQuantity <= 0) {
            throw new IllegalArgumentException("dispatchedQuantity must be positive");
        }
        if (receivedQuantity < 0) {
            throw new IllegalArgumentException("receivedQuantity must not be negative");
        }
        Objects.requireNonNull(status, "status must not be null");
    }
}
