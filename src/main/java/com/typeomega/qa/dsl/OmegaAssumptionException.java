package com.typeomega.qa.dsl;

/**
 * Controlled assumption failure thrown by {@code OmegaTestHelper.assume*} so a test is recorded as {@code SKIPPED}
 * instead of silently passing or failing as a defect.
 */
public final class OmegaAssumptionException extends RuntimeException {

    public OmegaAssumptionException(String reason) {
        super(reason == null || reason.isBlank() ? "assumption failed" : reason);
    }
}
