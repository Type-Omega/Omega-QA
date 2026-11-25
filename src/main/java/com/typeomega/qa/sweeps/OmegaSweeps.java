package com.typeomega.qa.sweeps;

import com.gregtechceu.gtceu.api.capability.recipe.FluidRecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.ItemRecipeCapability;
import com.gregtechceu.gtceu.api.machine.MachineDefinition;
import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.api.pattern.error.PatternError;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.registry.GTRegistries;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.Vec3i;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.typeomega.lib.lang.Tr;
import com.typeomega.lib.test.Previews;
import com.typeomega.lib.test.SweepResult;
import com.typeomega.qa.spi.FailureDiagnostic;
import com.typeomega.qa.spi.OmegaQaBootstrap;

import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Unified ecosystem sweeps for multiblock previews, GT + vanilla recipes, lookup collisions, trilingual parity,
 * D-432 tooltip contracts, and branding terms.
 */
public final class OmegaSweeps {

    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("%(?:\\d+\\$)?[sd]");

    private OmegaSweeps() {}

    public static SweepResult multiblocksFormFromPreview(GameTestHelper helper, String namespace, int maxSide) {
        SweepResult result = new SweepResult(namespace + " multiblocks forming from their preview");
        BlockPos origin = new BlockPos(0, 1, 0);
        for (MachineDefinition machine : GTRegistries.MACHINES) {
            if (!(machine instanceof MultiblockMachineDefinition def) ||
                    !namespace.equals(def.getId().getNamespace()))
                continue;
            int[] size = Previews.size(def);
            String id = def.getId().getPath();
            if (size == null) {
                result.fail(id + " (no preview)");
                continue;
            }
            if (size[0] > maxSide || size[1] > maxSide || size[2] > maxSide) continue;
            result.checked();
            try (var placed = Previews.place(helper, def, origin)) {
                if (!placed.forms()) {
                    String err = placed.error();
                    result.fail(id + " (" + err + ")");
                    publishPreviewFailure(helper, def, origin, size, placed.controller(), err);
                }
            } catch (RuntimeException e) {
                result.fail(id + " (preview threw " + e + ")");
            }
        }
        return result;
    }

    public static SweepResult recipesHaveRealContents(GameTestHelper helper, String namespace) {
        SweepResult result = new SweepResult(namespace + " recipes (GT + vanilla)");
        RegistryAccess access = helper.getLevel().registryAccess();
        for (Recipe<?> recipe : helper.getLevel().getRecipeManager().getRecipes()) {
            if (!namespace.equals(recipe.getId().getNamespace())) continue;
            if (recipe instanceof GTRecipe gt) {
                result.checked();
                checkGtRecipe(gt, result);
            } else if (!recipe.isSpecial() && (recipe.getType() == RecipeType.CRAFTING ||
                    recipe.getType() == RecipeType.SMELTING ||
                    recipe.getType() == RecipeType.BLASTING || recipe.getType() == RecipeType.SMOKING)) {
                        result.checked();
                        checkVanillaRecipe(recipe, access, result);
                    }
        }
        return result;
    }

    public static SweepResult recipeLookupConflicts(GameTestHelper helper, String namespace) {
        return RecipeConflictDetector.detect(helper.getLevel().getRecipeManager(), namespace);
    }

    public static SweepResult machineTooltipsBuild(String namespace) {
        SweepResult result = new SweepResult(namespace + " machine tooltips");
        for (MachineDefinition definition : GTRegistries.MACHINES) {
            if (!namespace.equals(definition.getId().getNamespace())) continue;
            BiConsumer<ItemStack, List<Component>> builder = definition.getTooltipBuilder();
            if (builder == null) continue;
            result.checked();
            List<Component> lines = new ArrayList<>();
            try {
                builder.accept(ItemStack.EMPTY, lines);
            } catch (RuntimeException e) {
                result.fail(definition.getId().getPath() + " threw " + e);
                continue;
            }
            for (Component line : lines) {
                String text = line.getString();
                if (text.startsWith(namespace + ".")) result.fail(definition.getId().getPath() + ": raw key " + text);
            }
        }
        return result;
    }

    public static SweepResult multiblockTooltipsContract(String namespace, String expectedModName,
                                                         TooltipContractConfig config) {
        SweepResult result = new SweepResult(namespace + " multiblock tooltip contract");
        TextColor gray = TextColor.fromLegacyFormat(ChatFormatting.GRAY);
        TextColor red = TextColor.fromLegacyFormat(ChatFormatting.RED);
        for (MachineDefinition def : GTRegistries.MACHINES) {
            if (!namespace.equals(def.getId().getNamespace()) || !(def instanceof MultiblockMachineDefinition)) {
                continue;
            }
            String id = def.getId().getPath();
            BiConsumer<ItemStack, List<Component>> builder = def.getTooltipBuilder();
            if (builder == null) {
                result.checked();
                result.fail(id + ": missing tooltip builder");
                continue;
            }
            result.checked();
            List<Component> lines = new ArrayList<>();
            builder.accept(ItemStack.EMPTY, lines);
            if (lines.isEmpty()) {
                result.fail(id + ": empty tooltip");
                continue;
            }
            boolean hasMachineType = lines.stream().anyMatch(l -> l.getContents() instanceof TranslatableContents t &&
                    "typeomegalib.tooltip.machine_type".equals(t.getKey()));
            if (!hasMachineType) result.fail(id + ": missing Machine Type line");

            if (config.forbidStructureAndPollution()) {
                boolean forbidden = lines.stream().anyMatch(l -> l.getContents() instanceof TranslatableContents t &&
                        (t.getKey().startsWith("typeomegalib.tooltip.structure") ||
                                "typeomegalib.tooltip.pollution".equals(t.getKey())));
                if (forbidden) result.fail(id + ": contains forbidden structure/pollution section (D-431)");
            }

            if (config.requireHumor()) {
                boolean hasHumor = lines.stream().anyMatch(l -> l.getStyle().isItalic() &&
                        l.getContents() instanceof TranslatableContents t && t.getKey().endsWith(".humor"));
                if (!hasHumor) result.fail(id + ": missing italic .humor line");
            }

            long mechanicLines = Math.max(0L, lines.stream()
                    .filter(l -> l.getStyle().getColor() != null &&
                            (l.getStyle().getColor().equals(gray) || l.getStyle().getColor().equals(red)))
                    .count() - 1L);
            boolean isMega = config.megaMarkerKey() != null && lines.stream()
                    .anyMatch(l -> l.getContents() instanceof TranslatableContents t &&
                            config.megaMarkerKey().equals(t.getKey()));
            int minLines = isMega ? config.minMegaLines() : config.minFactoryLines();
            if (mechanicLines < minLines) {
                result.fail(id + ": only " + mechanicLines + " mechanic lines (expected >= " + minLines + ")");
            }

            for (Component line : lines) {
                String text = line.getString();
                if (text.contains("%s") || text.contains("%%") || text.contains(namespace + ".machine.") ||
                        text.contains(namespace + ".tooltip.")) {
                    result.fail(id + ": unformatted text '" + text + "'");
                }
            }

            List<Component> checkLines = new ArrayList<>(lines);
            if (config.simulateEmiModAppend()) {
                // EN: Mirror EMI's trailing mod name append when checking that no duplicate mod line leaks.
                // PT: Espelha o apêndice de nome do EMI ao validar que não há linha duplicada de mod.
                // ZH: 模拟 EMI 在末尾追加模组名，校验不会出现重复的模组名称行。
                long existing = checkLines.stream().filter(l -> expectedModName.equals(l.getString())).count();
                if (existing != 1) {
                    result.fail(id + ": expected 1 mod line '" + expectedModName + "', found " + existing);
                }
            }
            if (!expectedModName.equals(checkLines.get(checkLines.size() - 1).getString())) {
                result.fail(id + ": last line is not '" + expectedModName + "'");
            }
        }
        return result;
    }

    public static SweepResult namesInAllLanguages(String modid) {
        return namesInAllLanguages(modid, LangSweepOptions.defaults());
    }

    public static SweepResult namesInAllLanguages(String modid, LangSweepOptions options) {
        SweepResult result = new SweepResult(modid + " translations");
        List<JsonObject> langs = new ArrayList<>();
        for (String locale : Tr.LOCALES) {
            JsonObject lang = readLang(modid, locale);
            if (lang == null) {
                result.fail("missing assets/" + modid + "/lang/" + locale + ".json");
                return result;
            }
            langs.add(lang);
        }
        List<String> keys = new ArrayList<>();
        ForgeRegistries.ITEMS.getEntries().forEach(e -> {
            if (modid.equals(e.getKey().location().getNamespace())) keys.add(e.getValue().getDescriptionId());
        });
        ForgeRegistries.BLOCKS.getEntries().forEach(e -> {
            if (modid.equals(e.getKey().location().getNamespace())) keys.add(e.getValue().getDescriptionId());
        });
        for (JsonObject lang : langs) {
            keys.addAll(lang.keySet());
        }
        for (String key : keys.stream().distinct().sorted().toList()) {
            if (!matchesLangScope(modid, key, options)) continue;
            result.checked();
            int expectedPlaceholders = -1;
            for (int i = 0; i < langs.size(); i++) {
                JsonObject lang = langs.get(i);
                if (!lang.has(key)) {
                    result.fail(Tr.LOCALES.get(i) + ":" + key);
                    continue;
                }
                String value = lang.get(key).isJsonNull() ? "" : lang.get(key).getAsString();
                if (value.isBlank()) {
                    result.fail(Tr.LOCALES.get(i) + ":" + key + " (blank)");
                    continue;
                }
                if (options.checkPlaceholderParity()) {
                    int count = countPlaceholders(value);
                    if (expectedPlaceholders < 0) expectedPlaceholders = count;
                    else if (count != expectedPlaceholders) {
                        result.fail(Tr.LOCALES.get(i) + ":" + key + " (placeholder count " + count + " != " +
                                expectedPlaceholders + ")");
                    }
                }
            }
        }
        return result;
    }

    public static SweepResult noForbiddenBrandTerms(String modid, Pattern forbidden) {
        SweepResult result = new SweepResult(modid + " branding terms");
        ModList.get().getModContainerById(modid).ifPresent(container -> {
            result.checked();
            String display = container.getModInfo().getDisplayName();
            if (forbidden.matcher(display).find()) {
                result.fail("mods.toml displayName contains forbidden term: " + display);
            }
        });
        for (String locale : Tr.LOCALES) {
            JsonObject lang = readLang(modid, locale);
            if (lang == null) {
                result.fail("missing assets/" + modid + "/lang/" + locale + ".json");
                continue;
            }
            for (var entry : lang.entrySet()) {
                result.checked();
                String val = entry.getValue().isJsonNull() ? "" : entry.getValue().getAsString();
                if (forbidden.matcher(val).find()) {
                    result.fail(locale + ":" + entry.getKey() + " -> '" + val + "'");
                }
            }
        }
        return result;
    }

    private static boolean matchesLangScope(String modid, String key, LangSweepOptions options) {
        if (key.contains(modid + ".")) return true;
        if (options.includeMaterials() && key.startsWith("material." + modid + ".")) return true;
        if (options.includeDeposits() && key.startsWith(modid + ":")) return true;
        if (options.includeTooltips() && key.startsWith("tooltip." + modid + ".")) return true;
        for (String prefix : options.extraKeyPrefixes()) {
            if (key.startsWith(prefix)) return true;
        }
        return false;
    }

    private static int countPlaceholders(String value) {
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(value);
        int count = 0;
        while (matcher.find()) count++;
        return count;
    }

    private static void checkGtRecipe(GTRecipe gt, SweepResult result) {
        String id = gt.id.toString();
        for (var content : gt.getInputContents(ItemRecipeCapability.CAP)) {
            ItemStack[] items = ItemRecipeCapability.CAP.of(content.content).getItems();
            if (!hasRealItem(items)) result.fail(id + ": empty or barrier item input");
        }
        for (var content : gt.getInputContents(FluidRecipeCapability.CAP)) {
            var fluids = FluidRecipeCapability.CAP.of(content.content).getStacks();
            if (fluids.length == 0 || fluids[0].isEmpty()) result.fail(id + ": empty fluid input");
        }
        for (var content : gt.getOutputContents(ItemRecipeCapability.CAP)) {
            ItemStack[] items = ItemRecipeCapability.CAP.of(content.content).getItems();
            if (!hasRealItem(items)) result.fail(id + ": empty or barrier item output");
        }
        if (gt.duration <= 0) result.fail(id + ": duration " + gt.duration);
    }

    private static void checkVanillaRecipe(Recipe<?> recipe, RegistryAccess access, SweepResult result) {
        String id = recipe.getId().toString();
        boolean anyIngredient = false;
        for (Ingredient ing : recipe.getIngredients()) {
            if (ing.isEmpty()) continue;
            anyIngredient = true;
            if (!hasRealItem(ing.getItems())) {
                result.fail(id + ": empty or barrier vanilla ingredient");
                break;
            }
        }
        if (!anyIngredient) result.fail(id + ": no non-empty ingredients");
        ItemStack out = recipe.getResultItem(access);
        if (out.isEmpty() || out.is(Items.BARRIER)) {
            result.fail(id + ": empty or barrier vanilla output");
        }
    }

    private static boolean hasRealItem(ItemStack[] stacks) {
        if (stacks == null || stacks.length == 0) return false;
        for (ItemStack stack : stacks) {
            if (!stack.isEmpty() && !stack.is(Items.BARRIER)) return true;
        }
        return false;
    }

    private static void publishPreviewFailure(GameTestHelper helper, MultiblockMachineDefinition def,
                                              BlockPos relOrigin, int[] size,
                                              com.gregtechceu.gtceu.api.machine.multiblock.MultiblockControllerMachine ctrl,
                                              String err) {
        BlockPos absOrigin = helper.absolutePos(relOrigin);
        BlockPos failPos = absOrigin;
        BlockState expected = null;
        if (ctrl != null && ctrl.getMultiblockState().error != null) {
            PatternError pe = ctrl.getMultiblockState().error;
            if (pe.getPos() != null) failPos = pe.getPos();
            var candidates = pe.getCandidates();
            if (candidates != null && !candidates.isEmpty() && !candidates.get(0).isEmpty() &&
                    candidates.get(0).get(0).getItem() instanceof BlockItem bi) {
                expected = bi.getBlock().defaultBlockState();
            }
        }
        BlockState actual = helper.getLevel().getBlockState(failPos);
        OmegaQaBootstrap.overlaySink().publish(new FailureDiagnostic(
                "preview:" + def.getId(), absOrigin, new Vec3i(size[0], size[1], size[2]),
                failPos, expected, actual, err, FailureDiagnostic.TestStatus.FAILED));
    }

    private static JsonObject readLang(String modid, String locale) {
        var container = ModList.get().getModFileById(modid);
        if (container == null) return null;
        Path path = container.getFile().findResource("assets", modid, "lang", locale + ".json");
        if (!Files.exists(path)) return null;
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        } catch (Exception e) {
            return null;
        }
    }
}
