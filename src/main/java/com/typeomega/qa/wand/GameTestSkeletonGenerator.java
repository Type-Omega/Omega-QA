package com.typeomega.qa.wand;

import net.minecraft.core.Vec3i;
import net.minecraft.resources.ResourceLocation;

import com.typeomega.qa.spi.WandSelectionData;

import java.util.List;
import java.util.Locale;

/**
 * Generates compilable {@code @GameTestHolder} skeleton classes (positive formation/recipe test + negative
 * {@code assertNeverForms} test) from an exported Omega QA Wand structure and {@code .labels.json} sidecar.
 */
public final class GameTestSkeletonGenerator {

    private GameTestSkeletonGenerator() {}

    public static String toPascalCase(String raw) {
        if (raw == null || raw.isBlank()) return "ExportedStructure";
        StringBuilder sb = new StringBuilder();
        boolean upperNext = true;
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (Character.isLetterOrDigit(c)) {
                if (sb.isEmpty() && Character.isDigit(c)) {
                    sb.append('T');
                }
                sb.append(upperNext ? Character.toUpperCase(c) : Character.toLowerCase(c));
                upperNext = false;
            } else {
                upperNext = true;
            }
        }
        return sb.isEmpty() ? "ExportedStructure" : sb.toString();
    }

    public static String className(String structureName) {
        return toPascalCase(structureName) + "GameTests";
    }

    public static String packageForNamespace(String namespace) {
        String clean = namespace == null ? "typeomega" :
                namespace.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
        if ("typeomega".equals(clean)) return "com.typeomega.core.gametest.generated";
        if ("typeomegalib".equals(clean)) return "com.typeomega.lib.gametest.generated";
        if (clean.isEmpty()) clean = "custom";
        return "com.typeomega.qa.generated." + clean;
    }

    public static String generate(ResourceLocation templateId, Vec3i size,
                                  List<WandSelectionData.LabelEntry> labels) {
        String ns = templateId.getNamespace();
        String path = templateId.getPath();
        String cls = className(path);
        String pkg = packageForNamespace(ns);
        String pascal = toPascalCase(path);
        String methodPrefix = Character.toLowerCase(pascal.charAt(0)) + pascal.substring(1);

        boolean hasEnergy = hasRole(labels, LabelRole.ENERGY_HATCH);
        boolean hasInputBus = hasRole(labels, LabelRole.INPUT_BUS);
        boolean hasInputHatch = hasRole(labels, LabelRole.INPUT_HATCH);
        boolean hasOutputBus = hasRole(labels, LabelRole.OUTPUT_BUS);
        boolean hasOutputHatch = hasRole(labels, LabelRole.OUTPUT_HATCH);
        String breakLabel = findBreakableLabel(labels);

        StringBuilder sb = new StringBuilder();
        sb.append("package ").append(pkg).append(";\n\n");
        sb.append("import net.minecraft.gametest.framework.GameTest;\n");
        sb.append("import net.minecraft.gametest.framework.GameTestHelper;\n");
        sb.append("import net.minecraft.world.level.block.Blocks;\n");
        sb.append("import net.minecraftforge.gametest.GameTestHolder;\n");
        sb.append("import net.minecraftforge.gametest.PrefixGameTestTemplate;\n\n");
        sb.append("import com.typeomega.qa.dsl.OmegaTestHelper;\n");
        sb.append("import com.typeomega.qa.gt.MultiblockRig;\n");
        sb.append("import com.typeomega.qa.gt.PlacedStructure;\n");
        sb.append("import com.typeomega.qa.gt.StructureRig;\n\n");
        sb.append("/**\n");
        sb.append(" * Generated Omega-QA GameTest skeleton for {@code ").append(templateId)
                .append("} (size ").append(size.getX()).append('x').append(size.getY()).append('x')
                .append(size.getZ()).append(").\n");
        sb.append(" */\n");
        sb.append("@PrefixGameTestTemplate(false)\n");
        sb.append("@GameTestHolder(\"").append(ns).append("\")\n");
        sb.append("public final class ").append(cls).append(" {\n\n");
        sb.append("    private static final String TEMPLATE = \"").append(templateId).append("\";\n\n");
        sb.append("    private ").append(cls).append("() {}\n\n");

        // Positive test
        sb.append(
                "    // EN: Positive test verifying multiblock formation, maintenance, and deterministic TimeWarp.\n");
        sb.append(
                "    // PT: Teste positivo verificando formação do multibloco, manutenção e TimeWarp determinístico.\n");
        sb.append("    // ZH: 正向测试：验证多方块结构成型、维护状态与确定性时间加速运行。\n");
        sb.append("    @GameTest(template = \"qa_empty_5x5\", batch = \"omegaqa_").append(ns)
                .append("\", timeoutTicks = 200)\n");
        sb.append("    public static void ").append(methodPrefix).append("FormsAndRuns(GameTestHelper raw) {\n");
        sb.append("        OmegaTestHelper h = OmegaTestHelper.wrap(raw);\n");
        sb.append("        PlacedStructure placed = StructureRig.fromTemplate(raw, TEMPLATE)\n");
        sb.append("                .atFarSlot(0, 64, 3200, 200)\n");
        sb.append("                .place();\n");
        sb.append("        MultiblockRig rig = placed.multiblock()\n");
        sb.append("                .assertFormed()\n");
        sb.append("                .fixMaintenance();\n");
        if (hasEnergy) {
            sb.append("        rig.fillEnergyHatches();\n");
        }
        if (hasInputBus) {
            sb.append("        // Insert recipe items into input_bus before warping:\n");
            sb.append("        // rig.insertItem(new ItemStack(...));\n");
        }
        if (hasInputHatch) {
            sb.append("        // Insert recipe fluids into input_hatch before warping:\n");
            sb.append("        // rig.insertFluid(new FluidStack(...));\n");
        }
        sb.append("        rig.timeWarp()\n");
        sb.append("                .keepEnergyFull()\n");
        sb.append("                .warpUntilIdle(120);\n");
        if (hasOutputBus) {
            sb.append("        // Assert expected output items in output_bus:\n");
            sb.append("        // rig.assertOutputItem(stack -> !stack.isEmpty(), 1);\n");
        }
        if (hasOutputHatch) {
            sb.append("        // Assert expected output fluids in output_hatch:\n");
            sb.append("        // rig.assertOutputFluid(new FluidStack(...));\n");
        }
        sb.append("        h.succeed();\n");
        sb.append("    }\n\n");

        // Negative test
        sb.append("    // EN: Negative test confirming a missing structural block prevents formation on every tick.\n");
        sb.append(
                "    // PT: Teste negativo confirmando que remover um bloco estrutural impede a formação em todo tick.\n");
        sb.append("    // ZH: 反向测试：确认缺少结构方块后在每个 tick 都不会成型。\n");
        sb.append("    @GameTest(template = \"qa_empty_5x5\", batch = \"omegaqa_").append(ns)
                .append("\", timeoutTicks = 80)\n");
        sb.append("    public static void ").append(methodPrefix)
                .append("BrokenNeverForms(GameTestHelper raw) {\n");
        sb.append("        PlacedStructure placed = StructureRig.fromTemplate(raw, TEMPLATE)\n");
        sb.append("                .atFarSlot(1, 64, 3200, 200)\n");
        sb.append("                .place();\n");
        if (breakLabel != null) {
            sb.append("        placed.replaceBlock(placed.pos(\"").append(escapeJava(breakLabel))
                    .append("\"), Blocks.AIR.defaultBlockState());\n");
        } else {
            sb.append("        placed.replaceBlock(placed.at(0, 0, 0), Blocks.AIR.defaultBlockState());\n");
        }
        sb.append("        placed.multiblock().assertNeverForms(40, \"").append(path)
                .append("_broken_never_forms\");\n");
        sb.append("    }\n");
        sb.append("}\n");
        return sb.toString();
    }

    private static boolean hasRole(List<WandSelectionData.LabelEntry> labels, LabelRole role) {
        return labels.stream().anyMatch(l -> role.id().equals(l.role()));
    }

    private static String findBreakableLabel(List<WandSelectionData.LabelEntry> labels) {
        for (WandSelectionData.LabelEntry entry : labels) {
            if (!LabelRole.CONTROLLER.id().equals(entry.role()) && !"controller".equals(entry.name())) {
                return entry.name();
            }
        }
        return null;
    }

    private static String escapeJava(String raw) {
        return raw.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
