package com.typeomega.qa.spi;

import java.util.Map;
import java.util.Optional;

/** Sink for live test diagnostics and world failure overlays (implemented by core memory sink and visual module). */
public interface FailureOverlaySink {

    void publish(FailureDiagnostic diagnostic);

    void clearTest(String testId);

    void clearAll();

    default Optional<FailureDiagnostic> latest(String testId) {
        return Optional.empty();
    }

    default Map<String, FailureDiagnostic> activeDiagnostics() {
        return Map.of();
    }
}
