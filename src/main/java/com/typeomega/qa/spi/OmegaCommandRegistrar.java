package com.typeomega.qa.spi;

import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;

/** SPI allowing core and visual QA modules to register subcommands under {@code /omegaqa} independently. */
public interface OmegaCommandRegistrar {

    void registerSubcommands(LiteralArgumentBuilder<CommandSourceStack> root, CommandBuildContext buildContext);
}
