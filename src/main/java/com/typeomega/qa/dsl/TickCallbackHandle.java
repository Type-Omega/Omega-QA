package com.typeomega.qa.dsl;

/** Idempotent handle controlling a per-tick invariant registered via {@link OmegaTestHelper}. */
public interface TickCallbackHandle {

    void enable();

    void disable();

    void remove();

    boolean isEnabled();

    String name();
}
