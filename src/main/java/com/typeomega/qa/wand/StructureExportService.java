package com.typeomega.qa.wand;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

import com.typeomega.qa.dsl.PosLabelResolver;
import com.typeomega.qa.spi.LabelSidecarCodec;
import com.typeomega.qa.spi.WandSelectionData;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Exports MC 1.20.1 {@link StructureTemplate} ({@code .nbt}), {@link LabelSidecarCodec} ({@code .labels.json}),
 * and {@link GameTestSkeletonGenerator} Java skeletons, and loads templates back into the world + Wand state.
 */
public final class StructureExportService {

    private StructureExportService() {}

    public record ExportResult(
                               ResourceLocation templateId,
                               Vec3i size,
                               int blockCount,
                               int labelCount,
                               Path nbtPath,
                               Path labelsPath,
                               Path javaTestPath) {}

    public record LoadResult(
                             ResourceLocation templateId,
                             Vec3i size,
                             WandDataNbt.WandData wandData) {}

    public static Path defaultExportRoot() {
        // EN: Server/GameTest runs execute inside <project>/run, so "omegaqa_exports" resolves to run/omegaqa_exports.
        // PT: Execuções server/GameTest rodam dentro de <projeto>/run, resolvendo para run/omegaqa_exports.
        // ZH: 服务端与 GameTest 在 <project>/run 目录运行，因此解析为 run/omegaqa_exports。
        return Paths.get("omegaqa_exports").toAbsolutePath().normalize();
    }

    public static ExportResult exportStructure(ServerLevel level, WandDataNbt.WandData data,
                                               String rawName, String rawNamespace, Path exportRoot)
                                                                                                     throws IOException {
        if (!data.hasBounds()) {
            throw new IllegalArgumentException("Wand selection requires both pos1 and pos2");
        }
        String ns = sanitizeIdPart(rawNamespace, WandDataNbt.DEFAULT_NAMESPACE);
        String name = sanitizeIdPart(rawName, "exported_structure");
        ResourceLocation templateId = ResourceLocation.tryBuild(ns, name);
        if (templateId == null) {
            throw new IllegalArgumentException("Invalid template id: " + ns + ":" + name);
        }

        BlockPos min = data.minPos();
        Vec3i size = data.size();
        StructureTemplate template = new StructureTemplate();
        template.fillFromWorld(level, min, size, false, Blocks.STRUCTURE_VOID);

        int nonAir = 0;
        for (int x = 0; x < size.getX(); x++) {
            for (int y = 0; y < size.getY(); y++) {
                for (int z = 0; z < size.getZ(); z++) {
                    if (!level.getBlockState(min.offset(x, y, z)).isAir()) {
                        nonAir++;
                    }
                }
            }
        }

        Path nsDir = exportRoot.resolve(ns);
        Files.createDirectories(nsDir);
        Path nbtPath = nsDir.resolve(name + ".nbt");
        Path labelsPath = nsDir.resolve(name + ".labels.json");
        Path javaPath = nsDir.resolve(GameTestSkeletonGenerator.className(name) + ".java");

        CompoundTag nbt = template.save(new CompoundTag());
        try (OutputStream out = Files.newOutputStream(nbtPath)) {
            NbtIo.writeCompressed(nbt, out);
        }
        LabelSidecarCodec.write(labelsPath, templateId, size, data.labels());
        String skeleton = GameTestSkeletonGenerator.generate(templateId, size, data.labels());
        Files.writeString(javaPath, skeleton);

        return new ExportResult(templateId, size, nonAir, data.labels().size(), nbtPath, labelsPath, javaPath);
    }

    public static LoadResult loadStructure(ServerLevel level, ResourceLocation templateId, BlockPos origin,
                                           Rotation rotation, Path exportRoot)
                                                                               throws IOException {
        StructureTemplate template = findTemplate(level, templateId, exportRoot)
                .orElseThrow(() -> new IOException("Template not found: " + templateId));

        Vec3i rotatedSize = template.getSize(rotation);
        BlockPos anchor = template.getZeroPositionWithTransform(origin, Mirror.NONE, rotation);
        StructurePlaceSettings settings = new StructurePlaceSettings().setRotation(rotation).setMirror(Mirror.NONE);
        template.placeInWorld(level, anchor, anchor, settings, level.random, 2 | 16);

        List<WandSelectionData.LabelEntry> labels = loadLabels(level, templateId, rotation, exportRoot);
        BlockPos pos2 = origin.offset(
                Math.max(0, rotatedSize.getX() - 1),
                Math.max(0, rotatedSize.getY() - 1),
                Math.max(0, rotatedSize.getZ() - 1));
        WandDataNbt.WandData wandData = new WandDataNbt.WandData(
                origin, pos2, WandDataNbt.NextCorner.POS1,
                templateId.getNamespace(), templateId.getPath(), labels);
        return new LoadResult(templateId, rotatedSize, wandData);
    }

    private static Optional<StructureTemplate> findTemplate(ServerLevel level, ResourceLocation id, Path exportRoot)
                                                                                                                     throws IOException {
        Optional<StructureTemplate> builtIn = level.getStructureManager().get(id);
        if (builtIn.isPresent()) return builtIn;

        Path diskNbt = exportRoot.resolve(id.getNamespace()).resolve(id.getPath() + ".nbt");
        if (Files.exists(diskNbt)) {
            try (InputStream in = Files.newInputStream(diskNbt)) {
                CompoundTag tag = NbtIo.readCompressed(in);
                StructureTemplate template = new StructureTemplate();
                template.load(level.holderLookup(Registries.BLOCK), tag);
                return Optional.of(template);
            }
        }
        return Optional.empty();
    }

    private static List<WandSelectionData.LabelEntry> loadLabels(ServerLevel level, ResourceLocation id,
                                                                 Rotation rotation, Path exportRoot)
                                                                                                     throws IOException {
        PosLabelResolver resolver = PosLabelResolver.load(level.getServer().getResourceManager(), id, rotation);
        if (!resolver.all().isEmpty()) {
            return new ArrayList<>(resolver.all().values());
        }
        Path diskLabels = exportRoot.resolve(id.getNamespace()).resolve(id.getPath() + ".labels.json");
        if (Files.exists(diskLabels)) {
            LabelSidecarCodec.SidecarDocument doc = LabelSidecarCodec.read(diskLabels);
            return new ArrayList<>(LabelSidecarCodec.asMap(doc, rotation).values());
        }
        return List.of();
    }

    public static String sanitizeIdPart(String raw, String fallback) {
        if (raw == null || raw.isBlank()) return fallback;
        String clean = raw.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_/-]", "_");
        return clean.isBlank() ? fallback : clean;
    }
}
