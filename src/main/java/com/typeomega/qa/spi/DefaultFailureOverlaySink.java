package com.typeomega.qa.spi;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Thread-safe in-memory {@link FailureOverlaySink} used when no visual client sink overrides it. */
public final class DefaultFailureOverlaySink implements FailureOverlaySink {

    private final ConcurrentHashMap<String, FailureDiagnostic> diagnostics = new ConcurrentHashMap<>();

    @Override
    public void publish(FailureDiagnostic diagnostic) {
        if (diagnostic == null || diagnostic.testId() == null) return;
        diagnostics.put(diagnostic.testId(), diagnostic);
    }

    @Override
    public void clearTest(String testId) {
        if (testId != null) diagnostics.remove(testId);
    }

    @Override
    public void clearAll() {
        diagnostics.clear();
    }

    @Override
    public Optional<FailureDiagnostic> latest(String testId) {
        return Optional.ofNullable(diagnostics.get(testId));
    }

    @Override
    public Map<String, FailureDiagnostic> activeDiagnostics() {
        return Collections.unmodifiableMap(diagnostics);
    }
}
