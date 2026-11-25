package com.typeomega.qa.dsl;

import net.minecraftforge.event.TickEvent;

import com.typeomega.qa.log.EventCategory;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;

/**
 * Tick-phased sequence DSL supporting explicit {@code START}/{@code END} tick hooks and strict {@code AssertionError}
 * polling.
 */
public final class OmegaSequence {

    private sealed interface Step permits StartStep, EndStep, IdleStep, WaitUntilStep, SucceedStep, FailStep {}

    private record StartStep(String label, Runnable action) implements Step {}

    private record EndStep(String label, Runnable action) implements Step {}

    private record IdleStep(int ticks) implements Step {}

    private record WaitUntilStep(int maxTicks, String label, Runnable assertion) implements Step {}

    private record SucceedStep() implements Step {}

    private record FailStep(String message) implements Step {}

    private final OmegaTestSession session;
    private final Deque<Step> steps = new ArrayDeque<>();
    private int waitTicksRemaining = -1;
    private boolean finished;

    OmegaSequence(OmegaTestSession session) {
        this.session = Objects.requireNonNull(session, "session");
    }

    /** Executes before {@code BlockEntity} ticks in {@link TickEvent.Phase#START}. */
    public OmegaSequence thenExecuteAtStart(String stepLabel, Runnable action) {
        steps.addLast(new StartStep(nonBlank(stepLabel, "start"), Objects.requireNonNull(action, "action")));
        return this;
    }

    /** Executes after world ticks in {@link TickEvent.Phase#END}. */
    public OmegaSequence thenExecuteAtEnd(String stepLabel, Runnable action) {
        steps.addLast(new EndStep(nonBlank(stepLabel, "end"), Objects.requireNonNull(action, "action")));
        return this;
    }

    public OmegaSequence thenIdle(int ticks) {
        if (ticks < 0) throw new IllegalArgumentException("ticks must be >= 0");
        steps.addLast(new IdleStep(ticks));
        return this;
    }

    /**
     * Polls {@code assertion} once per tick up to {@code maxTicks}; retries ONLY on {@link AssertionError} so real
     * exceptions (NPE, IllegalStateException) fail immediately.
     */
    public OmegaSequence thenWaitUntil(int maxTicks, String stepLabel, Runnable assertion) {
        if (maxTicks < 1) throw new IllegalArgumentException("maxTicks must be >= 1");
        steps.addLast(new WaitUntilStep(maxTicks, nonBlank(stepLabel, "waitUntil"),
                Objects.requireNonNull(assertion, "assertion")));
        return this;
    }

    public void thenSucceed() {
        steps.addLast(new SucceedStep());
    }

    public void thenFail(String message) {
        steps.addLast(new FailStep(message == null ? "sequence failed" : message));
    }

    public boolean isFinished() {
        return finished || steps.isEmpty();
    }

    void tick(TickEvent.Phase phase) {
        if (finished || !session.isRunning()) return;
        try {
            while (!steps.isEmpty() && !finished) {
                Step current = steps.peekFirst();
                if (current instanceof StartStep start) {
                    if (phase != TickEvent.Phase.START) return;
                    steps.removeFirst();
                    session.events().record(session.currentTick(), EventCategory.SEQUENCE, "START: " + start.label());
                    start.action().run();
                } else if (current instanceof EndStep end) {
                    if (phase != TickEvent.Phase.END) return;
                    steps.removeFirst();
                    session.events().record(session.currentTick(), EventCategory.SEQUENCE, "END: " + end.label());
                    end.action().run();
                } else if (current instanceof IdleStep idle) {
                    if (phase != TickEvent.Phase.END) return;
                    if (waitTicksRemaining < 0) waitTicksRemaining = idle.ticks();
                    if (waitTicksRemaining > 0) {
                        waitTicksRemaining--;
                        return;
                    }
                    waitTicksRemaining = -1;
                    steps.removeFirst();
                } else if (current instanceof WaitUntilStep wait) {
                    if (phase != TickEvent.Phase.END) return;
                    if (waitTicksRemaining < 0) waitTicksRemaining = wait.maxTicks();
                    try {
                        wait.assertion().run();
                        session.events().record(session.currentTick(), EventCategory.SEQUENCE,
                                "WAIT_OK: " + wait.label());
                        waitTicksRemaining = -1;
                        steps.removeFirst();
                    } catch (AssertionError failure) {
                        waitTicksRemaining--;
                        if (waitTicksRemaining <= 0) {
                            waitTicksRemaining = -1;
                            steps.removeFirst();
                            throw new AssertionError("Timed out after " + wait.maxTicks() + "t waiting for '" +
                                    wait.label() + "': " + failure.getMessage(), failure);
                        }
                        return;
                    }
                } else if (current instanceof SucceedStep) {
                    steps.removeFirst();
                    finished = true;
                    session.events().record(session.currentTick(), EventCategory.SEQUENCE, "SUCCEED");
                    session.succeed();
                    return;
                } else if (current instanceof FailStep fail) {
                    steps.removeFirst();
                    finished = true;
                    throw new AssertionError(fail.message());
                }
            }
            if (steps.isEmpty()) finished = true;
        } catch (OmegaAssumptionException skip) {
            finished = true;
            session.markSkipped(skip.getMessage());
            session.failWith(skip, null);
        } catch (Throwable t) {
            finished = true;
            session.failWith(t, "sequence");
        }
    }

    private static String nonBlank(String s, String fallback) {
        return s == null || s.isBlank() ? fallback : s;
    }
}
