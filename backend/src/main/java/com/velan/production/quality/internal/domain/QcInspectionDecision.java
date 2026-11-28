package com.velan.production.quality.internal.domain;

public enum QcInspectionDecision {
    SCHEDULED,
    CLAIMED_IN_PROGRESS,
    PASSED,
    FAILED,
    CANCELLED
}
