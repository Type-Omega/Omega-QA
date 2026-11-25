package com.typeomega.qa.spi;

import com.gregtechceu.gtceu.api.machine.MetaMachine;

import java.util.OptionalDouble;
import java.util.OptionalInt;

/** SPI for inspecting addon-specific hatches (Parallel, Thread, Accelerate, Overclock, Output Boost). */
public interface SpecialHatchInspector {

    OptionalInt parallelLimit(MetaMachine partMachine);

    OptionalInt threadBonus(MetaMachine partMachine);

    default OptionalDouble speedMultiplier(MetaMachine partMachine) {
        return OptionalDouble.empty();
    }

    default OptionalDouble outputMultiplier(MetaMachine partMachine) {
        return OptionalDouble.empty();
    }
}
