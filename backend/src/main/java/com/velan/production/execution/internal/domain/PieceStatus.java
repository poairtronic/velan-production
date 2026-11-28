package com.velan.production.execution.internal.domain;

public enum PieceStatus {
    CREATED,
    AWAITING_MATERIAL,
    IN_PRODUCTION,
    ON_HOLD_QC_FAILED,
    IN_PRODUCTION_EXTRA_PROCESS,
    IN_PRODUCTION_REWORK,
    COMPLETED,
    SCRAPPED
}
