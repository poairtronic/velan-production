package com.velan.production.integration.api;

import java.util.List;
import java.util.Objects;

/**
 * Size Card intake notice pulled from RM.
 * Order schema (purchase_orders, sc_headers) has no customer_name column.
 */
public record SizeCardIntakeNotice(
        String poNumber,
        String sizeCardNo,
        List<SizeCardIntakeItem> items) {

    public SizeCardIntakeNotice {
        if (poNumber == null || poNumber.isBlank()) {
            throw new IllegalArgumentException("poNumber must not be blank");
        }
        if (sizeCardNo == null || sizeCardNo.isBlank()) {
            throw new IllegalArgumentException("sizeCardNo must not be blank");
        }
        items = items == null ? List.of() : List.copyOf(items);
    }

    public record SizeCardIntakeItem(String productName, int quantity) {
        public SizeCardIntakeItem {
            Objects.requireNonNull(productName, "productName must not be null");
            if (productName.isBlank()) {
                throw new IllegalArgumentException("productName must not be blank");
            }
            if (quantity <= 0) {
                throw new IllegalArgumentException("quantity must be positive");
            }
        }
    }
}
