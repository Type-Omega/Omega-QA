package com.typeomega.qa.dsl;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.world.level.block.Rotation;
import net.minecraftforge.event.TickEvent;

import com.typeomega.qa.OmegaQA;
import com.typeomega.qa.log.EventCategory;
import com.typeomega.qa.log.EventLog;
import com.typeomega.qa.spi.FailureDiagnostic;
import com.typeomega.qa.spi.OmegaQaBootstrap;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Per-test execution session attaching {@link EventLog}, LIFO {@code afterTest} cleanups, {@code onEachTick}
 * invariants, {@link OmegaSequence} runners, and assumption/skip handling to a {@link GameTestInfo}.
 */
public final class OmegaTestSession {

    private static final Map<GameTestInfo, OmegaTestSession> SESSIONS = new ConcurrentHashMap<>();
    private static final @Nullable Field TEST_INFO_FIELD = findField(GameTestHelper.class, GameTestInfo.class);
    private static final @Nullable Field ERROR_FIELD = findField(GameTestInfo.class, Throwable.class);

    private static final class TickCallback implements TickCallbackHandle {

        private final String name;
        private final Runnable check;
        private final List<TickCallback> owner;
        private volatile boolean enabled;
        private volatile boolean removed;

        private TickCallback(String name, Runnable check, List<TickCallback> owner, boolean enabled) {
            this.name = name;
            this.check = check;
            this.owner = owner;
            this.enabled = enabled;
        }

        @Override
        public void enable() {
            if (!removed) enabled = true;
        }

        @Override
        public void disable() {
            enabled = false;
        }

        @Override
        public void remove() {
            removed = true;
            enabled = false;
            owner.remove(this);
        }

        @Override
        public boolean isEnabled() {
            return enabled && !removed;
        }

        @Override
        public String name() {
            return name;
        }
    }

    private final GameTestHelper helper;
    private final @Nullable GameTestInfo info;
    private final EventLog events = new EventLog();
    private final Deque<Runnable> cleanupStack = new ArrayDeque<>();
    private final List<TickCallback> tickCallbacks = new CopyOnWriteArrayList<>();
    private final List<OmegaSequence> sequences = new CopyOnWriteArrayList<>();
    private final AtomicBoolean tickHookInstalled = new AtomicBoolean(false);
    private final AtomicBoolean cleanedUp = new AtomicBoolean(false);

    private PosLabelResolver labelResolver = PosLabelResolver.empty();
    private boolean expectTimeoutSuccess;
    private long timeoutSuccessTick = -1;
    private boolean skipped;
    private @Nullable String skipReason;
    private @Nullable Integer failTick;
    private @Nullable String failInvariant;

    private OmegaTestSession(GameTestHelper helper, @Nullable GameTestInfo info) {
        this.helper = helper;
        this.info = info;
        if (info != null) {
            attachListener(info);
            String tpl = info.getStructureName();
            if (tpl != null && !tpl.isBlank()) {
                var loc = net.minecraft.resources.ResourceLocation.tryParse(tpl);
                if (loc != null && info.getLevel() != null) {
                    this.labelResolver = PosLabelResolver.load(info.getLevel().getServer().getResourceManager(), loc,
                            info.getRotation());
                }
            }
        }
    }

    public static OmegaTestSession of(GameTestHelper helper) {
        Objects.requireNonNull(helper, "helper");
        GameTestInfo info = extractInfo(helper);
        if (info != null) {
            return SESSIONS.computeIfAbsent(info, key -> new OmegaTestSession(helper, key));
        }
        return new OmegaTestSession(helper, null);
    }

    public static @Nullable OmegaTestSession find(GameTestInfo info) {
        return info == null ? null : SESSIONS.get(info);
    }

    public static void forget(GameTestInfo info) {
        if (info != null) SESSIONS.remove(info);
    }

    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        for (OmegaTestSession session : SESSIONS.values()) {
            if (session.isRunning()) {
                for (OmegaSequence seq : session.sequences) {
                    seq.tick(TickEvent.Phase.START);
                }
            }
        }
    }

    /** Clears {@code GameTestInfo.error} when a test was skipped via {@link OmegaAssumptionException}. */
    public static void clearAssumptionError(GameTestInfo info) {
        if (info == null || ERROR_FIELD == null) return;
        if (info.getError() instanceof OmegaAssumptionException) {
            try {
                ERROR_FIELD.set(info, null);
            } catch (IllegalAccessException e) {
                OmegaQA.LOGGER.warn("Unable to clear assumption error on {}", info.getTestName(), e);
            }
        }
    }

    private void attachListener(GameTestInfo testInfo) {
        testInfo.addListener(new GameTestListener() {

            @Override
            public void testStructureLoaded(GameTestInfo test) {
                events.record(0, EventCategory.LIFECYCLE, "Structure loaded: " + test.getStructureName());
            }

            @Override
            public void testPassed(GameTestInfo test) {
                events.record(currentTick(), EventCategory.LIFECYCLE, "Test passed");
                runCleanup();
            }

            @Override
            public void testFailed(GameTestInfo test) {
                if (test.getError() instanceof OmegaAssumptionException skip) {
                    markSkipped(skip.getMessage());
                    // EN: Clear error after GlobalTestReporter runs so GameTestServer does not exit non-zero on a
                    // skip.
                    // PT: Limpa o erro após o GlobalTestReporter rodar para o GameTestServer não falhar por skip.
                    // ZH: 在 GlobalTestReporter 运行后清除错误，避免 GameTestServer 因跳过测试而返回非零退出码。
                    clearAssumptionError(test);
                } else {
                    events.record(currentTick(), EventCategory.LIFECYCLE,
                            "Test failed: " + (test.getError() == null ? "unknown" : test.getError().getMessage()));
                }
                runCleanup();
            }
        });
    }

    public GameTestHelper helper() {
        return helper;
    }

    public @Nullable GameTestInfo info() {
        return info;
    }

    public EventLog events() {
        return events;
    }

    public String testId() {
        return info == null ? "unknown" : info.getTestName();
    }

    public BlockPos cellOrigin() {
        if (info != null && info.getStructureBlockPos() != null) return info.getStructureBlockPos();
        return helper.absolutePos(BlockPos.ZERO);
    }

    public Vec3i cellSize() {
        return info != null ? info.getStructureSize() : new Vec3i(5, 5, 5);
    }

    public Rotation rotation() {
        return info != null ? info.getRotation() : Rotation.NONE;
    }

    public long currentTick() {
        try {
            return helper.getTick();
        } catch (RuntimeException e) {
            return 0L;
        }
    }

    public boolean isRunning() {
        return info == null || (info.hasStarted() && !info.isDone());
    }

    public TickCallbackHandle registerTickCallback(String name, Runnable check, boolean startEnabled) {
        ensureTickHook();
        TickCallback handle = new TickCallback(name, check, tickCallbacks, startEnabled);
        tickCallbacks.add(handle);
        return handle;
    }

    public void succeedAtTimeout(int expectedTicks) {
        ensureTickHook();
        int max = info != null ? Math.max(1, info.getTimeoutTicks() - 1) : Math.max(1, expectedTicks);
        this.expectTimeoutSuccess = true;
        this.timeoutSuccessTick = expectedTicks > 0 ? Math.min(expectedTicks, max) : max;
        events.record(currentTick(), EventCategory.LIFECYCLE,
                "Scheduled succeedAtTimeout at tick " + this.timeoutSuccessTick);
    }

    public void afterTest(Runnable cleanup) {
        Objects.requireNonNull(cleanup, "cleanup");
        synchronized (cleanupStack) {
            cleanupStack.push(cleanup);
        }
    }

    public OmegaSequence newSequence() {
        ensureTickHook();
        OmegaSequence seq = new OmegaSequence(this);
        sequences.add(seq);
        return seq;
    }

    public void setLabelResolver(PosLabelResolver resolver) {
        this.labelResolver = Objects.requireNonNull(resolver, "resolver");
    }

    public PosLabelResolver labelResolver() {
        return labelResolver;
    }

    public void markSkipped(String reason) {
        this.skipped = true;
        this.skipReason = reason;
        events.record(currentTick(), EventCategory.LIFECYCLE, "SKIPPED: " + reason);
        OmegaQaBootstrap.overlaySink().publish(new FailureDiagnostic(
                testId(), cellOrigin(), cellSize(), null, null, null, reason,
                FailureDiagnostic.TestStatus.SKIPPED));
    }

    public boolean isSkipped() {
        return skipped;
    }

    public @Nullable String skipReason() {
        return skipReason;
    }

    public @Nullable Integer failTick() {
        return failTick;
    }

    public @Nullable String failInvariant() {
        return failInvariant;
    }

    public void succeed() {
        if (!isRunning()) return;
        helper.succeed();
    }

    public void failWith(Throwable cause, @Nullable String invariantName) {
        long tick = currentTick();
        if (this.failTick == null) {
            this.failTick = (int) tick;
            this.failInvariant = invariantName;
        }
        String prefix = invariantName == null ? "[tick=" + tick + "] " :
                "[tick=" + tick + "] [invariant=" + invariantName + "] ";
        String msg = prefix + (cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage());
        events.record(tick, EventCategory.ASSERTION, msg);

        if (!(cause instanceof OmegaAssumptionException)) {
            OmegaQaBootstrap.overlaySink().publish(new FailureDiagnostic(
                    testId(), cellOrigin(), cellSize(), null, null, null, msg,
                    FailureDiagnostic.TestStatus.FAILED));
        }

        AssertionError wrapped = new AssertionError(msg, cause);
        wrapped.setStackTrace(cause.getStackTrace());
        if (info != null) {
            info.fail(wrapped);
        } else {
            throw wrapped;
        }
    }

    public void runCleanup() {
        if (!cleanedUp.compareAndSet(false, true)) return;
        synchronized (cleanupStack) {
            while (!cleanupStack.isEmpty()) {
                Runnable task = cleanupStack.pop();
                try {
                    task.run();
                } catch (Throwable t) {
                    OmegaQA.LOGGER.error("afterTest cleanup threw in {}", testId(), t);
                }
            }
        }
    }

    private void ensureTickHook() {
        if (!tickHookInstalled.compareAndSet(false, true)) return;
        helper.onEachTick(() -> {
            long tick = currentTick();
            for (TickCallback cb : tickCallbacks) {
                if (!cb.isEnabled()) continue;
                try {
                    cb.check.run();
                } catch (OmegaAssumptionException skip) {
                    markSkipped(skip.getMessage());
                    if (info != null) info.fail(skip);
                    return;
                } catch (Throwable t) {
                    failWith(t, cb.name());
                    return;
                }
            }
            for (OmegaSequence seq : sequences) {
                seq.tick(TickEvent.Phase.END);
                if (!isRunning()) return;
            }
            if (expectTimeoutSuccess && tick >= timeoutSuccessTick && isRunning()) {
                events.record(tick, EventCategory.LIFECYCLE, "Reached succeedAtTimeout cleanly");
                helper.succeed();
            }
        });
    }

    private static @Nullable GameTestInfo extractInfo(GameTestHelper helper) {
        if (TEST_INFO_FIELD == null) return null;
        try {
            return (GameTestInfo) TEST_INFO_FIELD.get(helper);
        } catch (IllegalAccessException e) {
            return null;
        }
    }

    private static @Nullable Field findField(Class<?> owner, Class<?> targetType) {
        for (Field field : owner.getDeclaredFields()) {
            if (field.getType() == targetType) {
                field.setAccessible(true);
                return field;
            }
        }
        return null;
    }
}
