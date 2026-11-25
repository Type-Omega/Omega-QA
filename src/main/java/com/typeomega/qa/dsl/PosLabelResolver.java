package com.typeomega.qa.dsl;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.level.block.Rotation;

import com.typeomega.qa.spi.LabelSidecarCodec;
import com.typeomega.qa.spi.WandSelectionData;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Resolves semantic {@code .labels.json} labels to relative {@link BlockPos} coordinates with {@link Rotation} applied.
 */
public final class PosLabelResolver {

    private final Map<String, WandSelectionData.LabelEntry> labels;

    private PosLabelResolver(Map<String, WandSelectionData.LabelEntry> labels) {
        this.labels = new LinkedHashMap<>(labels);
    }

    public static PosLabelResolver empty() {
        return new PosLabelResolver(Map.of());
    }

    public static PosLabelResolver fromEntries(Map<String, WandSelectionData.LabelEntry> entries) {
        return new PosLabelResolver(entries);
    }

    public static PosLabelResolver fromSidecar(LabelSidecarCodec.SidecarDocument doc, Rotation rotation) {
        return new PosLabelResolver(LabelSidecarCodec.asMap(doc, rotation));
    }

    /**
     * Loads {@code <ns>:structures/<path>.labels.json} from the server {@link ResourceManager} or classpath, returning
     * an empty resolver when no sidecar exists.
     */
    public static PosLabelResolver load(ResourceManager manager, ResourceLocation templateId, Rotation rotation) {
        ResourceLocation sidecarId = ResourceLocation.tryBuild(templateId.getNamespace(),
                "structures/" + templateId.getPath() + ".labels.json");
        if (manager != null && sidecarId != null) {
            Optional<Resource> res = manager.getResource(sidecarId);
            if (res.isPresent()) {
                try (InputStream in = res.get().open()) {
                    String json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                    return fromSidecar(LabelSidecarCodec.decode(json), rotation);
                } catch (IOException ignored) {}
            }
        }
        String cp = "/data/" + templateId.getNamespace() + "/structures/" + templateId.getPath() + ".labels.json";
        try (InputStream in = PosLabelResolver.class.getResourceAsStream(cp)) {
            if (in != null) {
                String json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                return fromSidecar(LabelSidecarCodec.decode(json), rotation);
            }
        } catch (IOException ignored) {}
        return empty();
    }

    public BlockPos resolve(String label) {
        WandSelectionData.LabelEntry entry = labels.get(label);
        if (entry == null) {
            throw new AssertionError("LABEL_ERROR: unknown label '" + label + "', available=" + labels.keySet());
        }
        return entry.relativePos();
    }

    public Optional<WandSelectionData.LabelEntry> entry(String label) {
        return Optional.ofNullable(labels.get(label));
    }

    public Optional<Direction> facing(String label) {
        WandSelectionData.LabelEntry entry = labels.get(label);
        return entry == null ? Optional.empty() : Optional.ofNullable(entry.facing());
    }

    public boolean has(String label) {
        return labels.containsKey(label);
    }

    public Map<String, WandSelectionData.LabelEntry> all() {
        return Collections.unmodifiableMap(labels);
    }
}
