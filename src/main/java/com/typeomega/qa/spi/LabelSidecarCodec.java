package com.typeomega.qa.spi;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Rotation;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * JSON codec for {@code <name>.labels.json} sidecars paired with MC {@code .nbt} structure templates.
 * <p>
 * // EN: Relative coordinates are stored unrotated; {@link #rotatePos} transforms them when a test uses a Rotation.
 * // PT: Coordenadas relativas são gravadas sem rotação; {@link #rotatePos} transforma ao rodar um teste com Rotation.
 * // ZH: 相对坐标以未旋转形式存储；当测试使用 Rotation 时，由 {@link #rotatePos} 进行坐标变换。
 */
public final class LabelSidecarCodec {

    public static final int SCHEMA_VERSION = 1;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private LabelSidecarCodec() {}

    public record SidecarDocument(int schemaVersion, String template, Vec3i size,
                                  List<WandSelectionData.LabelEntry> labels) {}

    public static String encode(ResourceLocation templateId, Vec3i size, List<WandSelectionData.LabelEntry> labels) {
        JsonObject root = new JsonObject();
        root.addProperty("schemaVersion", SCHEMA_VERSION);
        root.addProperty("template", templateId.toString());

        JsonArray sizeArr = new JsonArray();
        sizeArr.add(size.getX());
        sizeArr.add(size.getY());
        sizeArr.add(size.getZ());
        root.add("size", sizeArr);

        JsonObject labelsObj = new JsonObject();
        for (WandSelectionData.LabelEntry entry : labels) {
            JsonObject entryObj = new JsonObject();
            JsonArray posArr = new JsonArray();
            posArr.add(entry.relativePos().getX());
            posArr.add(entry.relativePos().getY());
            posArr.add(entry.relativePos().getZ());
            entryObj.add("pos", posArr);
            entryObj.addProperty("role", entry.role());
            if (entry.facing() != null) {
                entryObj.addProperty("facing", entry.facing().getName());
            }
            labelsObj.add(entry.name(), entryObj);
        }
        root.add("labels", labelsObj);
        return GSON.toJson(root) + "\n";
    }

    public static SidecarDocument decode(String json) {
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        int schemaVersion = root.has("schemaVersion") ? root.get("schemaVersion").getAsInt() : SCHEMA_VERSION;
        String template = root.has("template") ? root.get("template").getAsString() : "";
        Vec3i size = readVec3i(root.getAsJsonArray("size"));
        List<WandSelectionData.LabelEntry> entries = new ArrayList<>();
        if (root.has("labels") && root.get("labels").isJsonObject()) {
            JsonObject labelsObj = root.getAsJsonObject("labels");
            for (Map.Entry<String, JsonElement> e : labelsObj.entrySet()) {
                JsonObject obj = e.getValue().getAsJsonObject();
                BlockPos rel = readBlockPos(obj.getAsJsonArray("pos"));
                String role = obj.has("role") ? obj.get("role").getAsString() : "custom";
                Direction facing = obj.has("facing") ? Direction.byName(obj.get("facing").getAsString()
                        .toLowerCase(Locale.ROOT)) : null;
                entries.add(new WandSelectionData.LabelEntry(e.getKey(), role, rel, facing));
            }
        }
        return new SidecarDocument(schemaVersion, template, size, List.copyOf(entries));
    }

    public static SidecarDocument read(Path path) throws IOException {
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            return decode(JsonParser.parseReader(reader).toString());
        }
    }

    public static void write(Path path, ResourceLocation templateId, Vec3i size,
                             List<WandSelectionData.LabelEntry> labels) throws IOException {
        if (path.getParent() != null) Files.createDirectories(path.getParent());
        Files.writeString(path, encode(templateId, size, labels), StandardCharsets.UTF_8);
    }

    public static Map<String, WandSelectionData.LabelEntry> asMap(SidecarDocument doc, Rotation rotation) {
        Map<String, WandSelectionData.LabelEntry> map = new LinkedHashMap<>();
        for (WandSelectionData.LabelEntry entry : doc.labels()) {
            BlockPos rotatedPos = rotatePos(entry.relativePos(), doc.size(), rotation);
            Direction rotatedFacing = entry.facing() == null ? null : rotation.rotate(entry.facing());
            map.put(entry.name(), new WandSelectionData.LabelEntry(entry.name(), entry.role(), rotatedPos,
                    rotatedFacing));
        }
        return map;
    }

    /** Rotates a zero-based relative coordinate inside {@code size} around the origin like {@link Rotation}. */
    public static BlockPos rotatePos(BlockPos rel, Vec3i size, Rotation rotation) {
        int maxX = Math.max(0, size.getX() - 1);
        int maxZ = Math.max(0, size.getZ() - 1);
        return switch (rotation) {
            case NONE -> rel;
            case CLOCKWISE_90 -> new BlockPos(maxZ - rel.getZ(), rel.getY(), rel.getX());
            case CLOCKWISE_180 -> new BlockPos(maxX - rel.getX(), rel.getY(), maxZ - rel.getZ());
            case COUNTERCLOCKWISE_90 -> new BlockPos(rel.getZ(), rel.getY(), maxX - rel.getX());
        };
    }

    public static Vec3i rotatedSize(Vec3i size, Rotation rotation) {
        return switch (rotation) {
            case NONE, CLOCKWISE_180 -> size;
            case CLOCKWISE_90, COUNTERCLOCKWISE_90 -> new Vec3i(size.getZ(), size.getY(), size.getX());
        };
    }

    private static Vec3i readVec3i(JsonArray arr) {
        if (arr == null || arr.size() < 3) return Vec3i.ZERO;
        return new Vec3i(arr.get(0).getAsInt(), arr.get(1).getAsInt(), arr.get(2).getAsInt());
    }

    private static BlockPos readBlockPos(JsonArray arr) {
        if (arr == null || arr.size() < 3) return BlockPos.ZERO;
        return new BlockPos(arr.get(0).getAsInt(), arr.get(1).getAsInt(), arr.get(2).getAsInt());
    }
}
