package com.velan.production.execution.internal.domain;

public enum PieceOperationStatus {
    PENDING,
    READY,
    OPENED,
    QC_PENDING,
    QC_IN_PROGRESS,
    PASSED,
    FAILED,
    CONCESSION_ACCEPTED,
    BYPASSED,
    CANCELLED
}
