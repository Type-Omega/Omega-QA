package com.typeomega.qa.wand;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

import com.typeomega.qa.spi.WandSelectionData;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Immutable NBT model and geometry transforms for the Omega QA Wand stack tag ({@code WandData}).
 */
public final class WandDataNbt {

    public static final String ROOT_TAG = "OmegaWandData";
    public static final String DEFAULT_NAMESPACE = "typeomega";

    private static final String TAG_POS1 = "Pos1";
    private static final String TAG_POS2 = "Pos2";
    private static final String TAG_NEXT_CORNER = "NextCorner";
    private static final String TAG_NAMESPACE = "ExportNamespace";
    private static final String TAG_NAME = "ExportName";
    private static final String TAG_LABELS = "Labels";

    private WandDataNbt() {}

    public enum NextCorner {

        POS1,
        POS2;

        public NextCorner toggle() {
            return this == POS1 ? POS2 : POS1;
        }
    }

    public record WandData(
                           @Nullable BlockPos pos1,
                           @Nullable BlockPos pos2,
                           NextCorner nextCorner,
                           String exportNamespace,
                           String exportName,
                           List<WandSelectionData.LabelEntry> labels) {

        public WandData {
            nextCorner = nextCorner == null ? NextCorner.POS1 : nextCorner;
            exportNamespace = sanitizeNamespace(exportNamespace);
            exportName = exportName == null ? "" : exportName;
            labels = labels == null ? List.of() : List.copyOf(labels);
        }

        public static WandData empty() {
            return new WandData(null, null, NextCorner.POS1, DEFAULT_NAMESPACE, "", List.of());
        }

        public boolean hasBounds() {
            return pos1 != null && pos2 != null;
        }

        public @Nullable BlockPos minPos() {
            if (!hasBounds()) return null;
            return new BlockPos(
                    Math.min(pos1.getX(), pos2.getX()),
                    Math.min(pos1.getY(), pos2.getY()),
                    Math.min(pos1.getZ(), pos2.getZ()));
        }

        public @Nullable BlockPos maxPos() {
            if (!hasBounds()) return null;
            return new BlockPos(
                    Math.max(pos1.getX(), pos2.getX()),
                    Math.max(pos1.getY(), pos2.getY()),
                    Math.max(pos1.getZ(), pos2.getZ()));
        }

        public Vec3i size() {
            if (!hasBounds()) return Vec3i.ZERO;
            BlockPos min = minPos();
            BlockPos max = maxPos();
            return new Vec3i(
                    max.getX() - min.getX() + 1,
                    max.getY() - min.getY() + 1,
                    max.getZ() - min.getZ() + 1);
        }

        public boolean isInsideSelection(BlockPos relativePos) {
            if (!hasBounds()) return false;
            Vec3i s = size();
            return relativePos.getX() >= 0 && relativePos.getX() < s.getX() &&
                    relativePos.getY() >= 0 && relativePos.getY() < s.getY() &&
                    relativePos.getZ() >= 0 && relativePos.getZ() < s.getZ();
        }

        public WandData withPos1(BlockPos newPos1) {
            return new WandData(newPos1, pos2, NextCorner.POS2, exportNamespace, exportName, labels);
        }

        public WandData withPos2(BlockPos newPos2) {
            return new WandData(pos1, newPos2, NextCorner.POS1, exportNamespace, exportName, labels);
        }

        public WandData withExportInfo(String namespace, String name) {
            return new WandData(pos1, pos2, nextCorner, namespace, name, labels);
        }

        public WandData withMovedSelection(int dx, int dy, int dz) {
            if (!hasBounds()) return this;
            return new WandData(pos1.offset(dx, dy, dz), pos2.offset(dx, dy, dz),
                    nextCorner, exportNamespace, exportName, labels);
        }

        public WandData withResizedFace(Direction face, int delta) {
            if (!hasBounds() || delta == 0) return this;
            BlockPos min = minPos();
            BlockPos max = maxPos();
            BlockPos newMin = min;
            BlockPos newMax = max;
            switch (face) {
                case WEST -> newMin = min.offset(-delta, 0, 0);
                case DOWN -> newMin = min.offset(0, -delta, 0);
                case NORTH -> newMin = min.offset(0, 0, -delta);
                case EAST -> newMax = max.offset(delta, 0, 0);
                case UP -> newMax = max.offset(0, delta, 0);
                case SOUTH -> newMax = max.offset(0, 0, delta);
            }
            // EN: Keep normalized min <= max and compensate label relative coordinates when min corner moves.
            // PT: Mantém min <= max normalizado e compensa coordenadas relativas dos labels se o canto min mover.
            // ZH: 保持规范化的 min <= max，并在最小角移动时补偿标记的相对坐标。
            BlockPos normMin = new BlockPos(
                    Math.min(newMin.getX(), newMax.getX()),
                    Math.min(newMin.getY(), newMax.getY()),
                    Math.min(newMin.getZ(), newMax.getZ()));
            BlockPos normMax = new BlockPos(
                    Math.max(newMin.getX(), newMax.getX()),
                    Math.max(newMin.getY(), newMax.getY()),
                    Math.max(newMin.getZ(), newMax.getZ()));
            BlockPos shift = min.subtract(normMin);
            List<WandSelectionData.LabelEntry> shiftedLabels = new ArrayList<>(labels.size());
            for (WandSelectionData.LabelEntry entry : labels) {
                shiftedLabels.add(new WandSelectionData.LabelEntry(
                        entry.name(), entry.role(), entry.relativePos().offset(shift), entry.facing()));
            }
            return new WandData(normMin, normMax, nextCorner, exportNamespace, exportName, shiftedLabels);
        }

        public WandData withUpsertedLabel(WandSelectionData.LabelEntry entry) {
            List<WandSelectionData.LabelEntry> next = new ArrayList<>(labels.size() + 1);
            boolean replaced = false;
            for (WandSelectionData.LabelEntry existing : labels) {
                if (existing.name().equals(entry.name())) {
                    next.add(entry);
                    replaced = true;
                } else {
                    next.add(existing);
                }
            }
            if (!replaced) next.add(entry);
            return new WandData(pos1, pos2, nextCorner, exportNamespace, exportName, next);
        }

        public WandData withRemovedLabel(String name) {
            List<WandSelectionData.LabelEntry> next = labels.stream()
                    .filter(e -> !e.name().equals(name))
                    .toList();
            return new WandData(pos1, pos2, nextCorner, exportNamespace, exportName, next);
        }

        public WandData withRenamedLabel(String oldName, String newName) {
            if (newName == null || newName.isBlank() || oldName.equals(newName)) return this;
            String clean = newName.trim();
            List<WandSelectionData.LabelEntry> next = new ArrayList<>(labels.size());
            for (WandSelectionData.LabelEntry existing : labels) {
                if (existing.name().equals(clean)) continue;
                if (existing.name().equals(oldName)) {
                    next.add(new WandSelectionData.LabelEntry(clean, existing.role(), existing.relativePos(),
                            existing.facing()));
                } else {
                    next.add(existing);
                }
            }
            return new WandData(pos1, pos2, nextCorner, exportNamespace, exportName, next);
        }

        public WandData withMovedLabel(String name, BlockPos newRelPos) {
            List<WandSelectionData.LabelEntry> next = new ArrayList<>(labels.size());
            for (WandSelectionData.LabelEntry existing : labels) {
                if (existing.name().equals(name)) {
                    next.add(new WandSelectionData.LabelEntry(existing.name(), existing.role(), newRelPos,
                            existing.facing()));
                } else {
                    next.add(existing);
                }
            }
            return new WandData(pos1, pos2, nextCorner, exportNamespace, exportName, next);
        }

        public WandSelectionData toSelectionData() {
            return new WandSelectionData(pos1, pos2, exportNamespace, exportName, labels);
        }

        public static WandData fromSelectionData(WandSelectionData data, NextCorner corner) {
            return new WandData(data.pos1(), data.pos2(), corner, data.exportNamespace(), data.exportName(),
                    data.labels());
        }
    }

    public static WandData read(ItemStack stack) {
        if (stack.isEmpty()) return WandData.empty();
        CompoundTag root = stack.getTag();
        if (root == null || !root.contains(ROOT_TAG, Tag.TAG_COMPOUND)) return WandData.empty();
        return read(root.getCompound(ROOT_TAG));
    }

    public static void write(ItemStack stack, WandData data) {
        if (stack.isEmpty()) return;
        stack.getOrCreateTag().put(ROOT_TAG, write(new CompoundTag(), data));
    }

    public static WandData read(CompoundTag tag) {
        if (tag == null) return WandData.empty();
        BlockPos pos1 = readBlockPos(tag, TAG_POS1);
        BlockPos pos2 = readBlockPos(tag, TAG_POS2);
        NextCorner corner = NextCorner.POS1;
        if (tag.contains(TAG_NEXT_CORNER, Tag.TAG_STRING)) {
            try {
                corner = NextCorner.valueOf(tag.getString(TAG_NEXT_CORNER));
            } catch (IllegalArgumentException ignored) {}
        }
        String ns = tag.contains(TAG_NAMESPACE, Tag.TAG_STRING) ? tag.getString(TAG_NAMESPACE) : DEFAULT_NAMESPACE;
        String name = tag.contains(TAG_NAME, Tag.TAG_STRING) ? tag.getString(TAG_NAME) : "";

        List<WandSelectionData.LabelEntry> labels = new ArrayList<>();
        if (tag.contains(TAG_LABELS, Tag.TAG_LIST)) {
            ListTag list = tag.getList(TAG_LABELS, Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                CompoundTag item = list.getCompound(i);
                String lName = item.getString("Name");
                if (lName.isBlank()) continue;
                String role = item.contains("Role", Tag.TAG_STRING) ? item.getString("Role") : LabelRole.CUSTOM.id();
                BlockPos rel = new BlockPos(item.getInt("X"), item.getInt("Y"), item.getInt("Z"));
                Direction facing = item.contains("Facing", Tag.TAG_STRING) ?
                        Direction.byName(item.getString("Facing").toLowerCase(Locale.ROOT)) : null;
                labels.add(new WandSelectionData.LabelEntry(lName, role, rel, facing));
            }
        }
        return new WandData(pos1, pos2, corner, ns, name, labels);
    }

    public static CompoundTag write(CompoundTag tag, WandData data) {
        if (data.pos1() != null) writeBlockPos(tag, TAG_POS1, data.pos1());
        if (data.pos2() != null) writeBlockPos(tag, TAG_POS2, data.pos2());
        tag.putString(TAG_NEXT_CORNER, data.nextCorner().name());
        tag.putString(TAG_NAMESPACE, data.exportNamespace());
        tag.putString(TAG_NAME, data.exportName());

        ListTag list = new ListTag();
        for (WandSelectionData.LabelEntry entry : data.labels()) {
            CompoundTag item = new CompoundTag();
            item.putString("Name", entry.name());
            item.putString("Role", entry.role());
            item.putInt("X", entry.relativePos().getX());
            item.putInt("Y", entry.relativePos().getY());
            item.putInt("Z", entry.relativePos().getZ());
            if (entry.facing() != null) {
                item.putString("Facing", entry.facing().getName());
            }
            list.add(item);
        }
        tag.put(TAG_LABELS, list);
        return tag;
    }

    private static @Nullable BlockPos readBlockPos(CompoundTag parent, String key) {
        if (!parent.contains(key, Tag.TAG_COMPOUND)) return null;
        CompoundTag p = parent.getCompound(key);
        return new BlockPos(p.getInt("X"), p.getInt("Y"), p.getInt("Z"));
    }

    private static void writeBlockPos(CompoundTag parent, String key, BlockPos pos) {
        CompoundTag p = new CompoundTag();
        p.putInt("X", pos.getX());
        p.putInt("Y", pos.getY());
        p.putInt("Z", pos.getZ());
        parent.put(key, p);
    }

    private static String sanitizeNamespace(@Nullable String ns) {
        if (ns == null || ns.isBlank()) return DEFAULT_NAMESPACE;
        return ns.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_.-]", "_");
    }
}
