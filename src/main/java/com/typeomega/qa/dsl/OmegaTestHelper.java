package com.typeomega.qa.dsl;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fml.ModList;

import com.typeomega.qa.log.EventCategory;
import com.typeomega.qa.log.EventLog;
import com.typeomega.qa.spi.FailureDiagnostic;
import com.typeomega.qa.spi.OmegaQaBootstrap;

import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;

/**
 * Fluent Omega-QA wrapper over Minecraft's {@link GameTestHelper} providing per-tick invariants,
 * {@code succeedAtTimeout}, assumption-based skips, LIFO {@code afterTest} cleanup, tick-phased sequences,
 * semantic labels, and {@link EventLog} flight recording.
 */
public final class OmegaTestHelper {

    private final GameTestHelper raw;
    private final OmegaTestSession session;

    private OmegaTestHelper(GameTestHelper raw, OmegaTestSession session) {
        this.raw = raw;
        this.session = session;
    }

    public static OmegaTestHelper wrap(GameTestHelper helper) {
        Objects.requireNonNull(helper, "helper");
        return new OmegaTestHelper(helper, OmegaTestSession.of(helper));
    }

    public static EventLog events(GameTestHelper helper) {
        return OmegaTestSession.of(helper).events();
    }

    public GameTestHelper raw() {
        return raw;
    }

    public OmegaTestSession session() {
        return session;
    }

    public ServerLevel level() {
        return raw.getLevel();
    }

    public long getTick() {
        return session.currentTick();
    }

    public EventLog events() {
        return session.events();
    }

    public void log(EventCategory category, String message) {
        events().record(getTick(), category, message);
    }

    public TickCallbackHandle onEachTick(String invariantName, Runnable check) {
        return session.registerTickCallback(invariantName, Objects.requireNonNull(check, "check"), true);
    }

    public TickCallbackHandle onEachTickDisabled(String invariantName, Runnable check) {
        return session.registerTickCallback(invariantName, Objects.requireNonNull(check, "check"), false);
    }

    public TickCallbackHandle assertNever(String invariantName, BooleanSupplier forbiddenCondition, String message) {
        Objects.requireNonNull(forbiddenCondition, "forbiddenCondition");
        return onEachTick(invariantName, () -> {
            if (forbiddenCondition.getAsBoolean()) {
                throw new AssertionError(message == null ? "Forbidden condition triggered" : message);
            }
        });
    }

    public void succeedAtTimeout() {
        session.succeedAtTimeout(-1);
    }

    public void succeedAtTimeout(int expectedTicks) {
        session.succeedAtTimeout(expectedTicks);
    }

    public void assumeTrue(boolean condition, String reason) {
        if (!condition) {
            session.markSkipped(reason);
            throw new OmegaAssumptionException(reason);
        }
    }

    public void assumeFalse(boolean condition, String reason) {
        assumeTrue(!condition, reason);
    }

    public void assumeModLoaded(String modid) {
        assumeTrue(ModList.get().isLoaded(modid), "Required mod not loaded: " + modid);
    }

    public static void assumeModLoaded(GameTestHelper helper, String modid) {
        wrap(helper).assumeModLoaded(modid);
    }

    public void afterTest(Runnable cleanup) {
        session.afterTest(cleanup);
    }

    public static void afterTest(GameTestHelper helper, Runnable cleanup) {
        wrap(helper).afterTest(cleanup);
    }

    public OmegaSequence startSequence() {
        return session.newSequence();
    }

    public OmegaTestHelper setLabelResolver(PosLabelResolver resolver) {
        session.setLabelResolver(resolver);
        return this;
    }

    public BlockPos pos(String label) {
        return session.labelResolver().resolve(label);
    }

    public BlockPos absPos(String label) {
        return raw.absolutePos(pos(label));
    }

    public void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    public void assertFalse(boolean condition, String message) {
        if (condition) {
            throw new AssertionError(message);
        }
    }

    public void assertItemEqual(ItemStack expected, ItemStack actual) {
        OmegaAssertions.assertItemEqual(expected, actual);
    }

    public void assertFluidEqual(FluidStack expected, FluidStack actual) {
        OmegaAssertions.assertFluidEqual(expected, actual);
    }

    public void assertBlockEntityNbt(BlockPos relPos, String nbtPath, Predicate<Tag> check, String desc) {
        OmegaAssertions.assertBlockEntityNbt(level(), raw.absolutePos(relPos), nbtPath, check, desc);
    }

    public ServerPlayer fakePlayer(String name) {
        return FakePlayers.survival(raw, name);
    }

    public void succeed() {
        session.succeed();
    }

    public void fail(String message) {
        session.failWith(new AssertionError(message), null);
    }

    public void fail(String message, BlockPos relPos) {
        BlockPos abs = raw.absolutePos(relPos);
        OmegaQaBootstrap.overlaySink().publish(new FailureDiagnostic(
                session.testId(), session.cellOrigin(), session.cellSize(), abs, null,
                level().getBlockState(abs), message, FailureDiagnostic.TestStatus.FAILED));
        events().record(getTick(), EventCategory.ASSERTION, message + " at " + abs);
        raw.fail(message, relPos);
    }
}
