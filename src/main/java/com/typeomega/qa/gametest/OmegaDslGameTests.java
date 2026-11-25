package com.typeomega.qa.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import com.typeomega.qa.OmegaQA;
import com.typeomega.qa.dsl.FakePlayers;
import com.typeomega.qa.dsl.OmegaTestHelper;
import com.typeomega.qa.dsl.TickCallbackHandle;
import com.typeomega.qa.log.EventCategory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Self-tests for Omega-QA DSL primitives: {@code onEachTick}, {@code assertNever}, {@code succeedAtTimeout},
 * {@code afterTest} LIFO cleanup, {@code OmegaSequence} phases, and {@code assumeModLoaded} skip handling.
 */
@PrefixGameTestTemplate(false)
@GameTestHolder(OmegaQA.MOD_ID)
public final class OmegaDslGameTests {

    private static final String TEMPLATE = "qa_empty_5x5";

    private OmegaDslGameTests() {}

    @GameTest(template = TEMPLATE, batch = "omegaqa_dsl", timeoutTicks = 40)
    public static void onEachTickAndSucceedAtTimeoutPassesCleanly(GameTestHelper raw) {
        OmegaTestHelper h = OmegaTestHelper.wrap(raw);
        AtomicInteger ticks = new AtomicInteger();
        AtomicInteger disabledTicks = new AtomicInteger();

        TickCallbackHandle disabledHandle = h.onEachTickDisabled("initially_disabled",
                disabledTicks::incrementAndGet);
        h.assertFalse(disabledHandle.isEnabled(), "disabled handle should start disabled");

        h.onEachTick("count_ticks", () -> {
            int n = ticks.incrementAndGet();
            if (n == 3) disabledHandle.enable();
            if (n == 6) disabledHandle.disable();
        });
        h.assertNever("never_negative_ticks", () -> ticks.get() < 0, "ticks became negative");
        h.afterTest(() -> h.assertTrue(disabledTicks.get() == 3,
                "disabled handle should run exactly 3 ticks while enabled, ran " + disabledTicks.get()));
        h.succeedAtTimeout(10);
    }

    @GameTest(template = TEMPLATE, batch = "omegaqa_dsl", timeoutTicks = 40)
    public static void afterTestExecutesInLifoOrder(GameTestHelper raw) {
        OmegaTestHelper h = OmegaTestHelper.wrap(raw);
        List<String> order = new ArrayList<>();
        h.afterTest(() -> {
            order.add("first_registered");
            h.assertTrue(order.equals(List.of("second_registered", "first_registered")),
                    "afterTest must execute in LIFO order, got " + order);
        });
        h.afterTest(() -> order.add("second_registered"));
        h.log(EventCategory.LIFECYCLE, "registered LIFO cleanups");
        h.events().assertLogged(EventCategory.LIFECYCLE, "LIFO");
        h.succeed();
    }

    @GameTest(template = TEMPLATE, batch = "omegaqa_dsl", timeoutTicks = 40)
    public static void omegaSequenceExecutesStartEndAndWaitUntil(GameTestHelper raw) {
        OmegaTestHelper h = OmegaTestHelper.wrap(raw);
        List<String> trace = new ArrayList<>();
        AtomicInteger counter = new AtomicInteger();

        h.startSequence()
                .thenExecuteAtStart("start_phase", () -> trace.add("START"))
                .thenExecuteAtEnd("end_phase", () -> {
                    trace.add("END");
                    counter.set(1);
                })
                .thenWaitUntil(10, "counter_reaches_three", () -> {
                    if (counter.incrementAndGet() < 3) {
                        throw new AssertionError("still waiting at " + counter.get());
                    }
                })
                .thenExecuteAtEnd("verify_trace", () -> {
                    h.assertTrue(trace.equals(List.of("START", "END")), "unexpected sequence trace: " + trace);
                    var player = FakePlayers.survival(raw, "seq_tester");
                    h.assertTrue(player.isAlive() && !player.isCreative(), "fake player must be survival");
                    h.raw().setBlock(new BlockPos(2, 1, 2), net.minecraft.world.level.block.Blocks.STONE);
                })
                .thenSucceed();
    }

    @GameTest(template = TEMPLATE, batch = "omegaqa_dsl", timeoutTicks = 20)
    public static void assumeModLoadedSkipsCleanlyWhenMissing(GameTestHelper raw) {
        OmegaTestHelper h = OmegaTestHelper.wrap(raw);
        h.assumeModLoaded("omegaqa_nonexistent_optional_mod");
        h.fail("Execution must abort when assumeModLoaded fails");
    }
}
