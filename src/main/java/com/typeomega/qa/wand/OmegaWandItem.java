package com.typeomega.qa.wand;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;

import com.typeomega.qa.net.OmegaQaNetwork;
import com.typeomega.qa.net.SyncWandStatePacket;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Omega QA Wand ({@code typeomegalib:omega_wand}): left-click sets {@code pos1}, right-click block sets
 * {@code pos2} (hold Shift to offset along the hit face normal), and right-click air opens the visual editor.
 */
public final class OmegaWandItem extends Item {

    private static final AtomicReference<Consumer<InteractionHand>> CLIENT_EDITOR_OPENER = new AtomicReference<>(
            hand -> {});

    public OmegaWandItem(Properties properties) {
        super(properties);
    }

    public static void setClientEditorOpener(Consumer<InteractionHand> opener) {
        CLIENT_EDITOR_OPENER.set(opener == null ? h -> {} : opener);
    }

    public static void openClientEditor(InteractionHand hand) {
        CLIENT_EDITOR_OPENER.get().accept(hand);
    }

    @Override
    public String getDescriptionId() {
        // EN: Align item lang key with OmegaQA.gatherData prefix ("omegaqa.") so en_us/pt_br/zh_cn generate
        // cleanly.
        // PT: Alinha a chave do item com o prefixo de OmegaQA.gatherData ("omegaqa.") para gerar
        // en_us/pt_br/zh_cn.
        // ZH: 使物品语言键与 OmegaQA.gatherData 前缀（"omegaqa."）对齐，以自动生成三语本地化。
        return QaWandTexts.WAND_NAME.key();
    }

    @Override
    public boolean canAttackBlock(BlockState state, Level level, BlockPos pos, Player player) {
        return false;
    }

    @Override
    public boolean onBlockStartBreak(ItemStack stack, BlockPos pos, Player player) {
        return true;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        Player player = context.getPlayer();
        if (player == null) return InteractionResult.PASS;

        BlockPos target = player.isShiftKeyDown() ?
                context.getClickedPos().relative(context.getClickedFace()) :
                context.getClickedPos();

        if (!level.isClientSide) {
            ItemStack stack = context.getItemInHand();
            WandDataNbt.WandData updated = WandDataNbt.read(stack).withPos2(target);
            WandDataNbt.write(stack, updated);
            if (player instanceof ServerPlayer serverPlayer) {
                OmegaQaNetwork.sendToPlayer(serverPlayer,
                        new SyncWandStatePacket(context.getHand(), updated.toSelectionData(), updated.nextCorner()));
            }
            player.displayClientMessage(QaWandTexts.WAND_POS2_SET.component(formatPos(target))
                    .withStyle(ChatFormatting.AQUA), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) {
            openClientEditor(hand);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        Player player = event.getEntity();
        ItemStack stack = player.getItemInHand(event.getHand());
        if (!(stack.getItem() instanceof OmegaWandItem)) return;

        event.setCanceled(true);
        if (event.getLevel().isClientSide) return;

        BlockPos target = player.isShiftKeyDown() && event.getFace() != null ?
                event.getPos().relative(event.getFace()) :
                event.getPos();

        WandDataNbt.WandData updated = WandDataNbt.read(stack).withPos1(target);
        WandDataNbt.write(stack, updated);
        if (player instanceof ServerPlayer serverPlayer) {
            OmegaQaNetwork.sendToPlayer(serverPlayer,
                    new SyncWandStatePacket(event.getHand(), updated.toSelectionData(), updated.nextCorner()));
        }
        player.displayClientMessage(QaWandTexts.WAND_POS1_SET.component(formatPos(target))
                .withStyle(ChatFormatting.GOLD), true);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        WandDataNbt.WandData data = WandDataNbt.read(stack);
        if (data.hasBounds()) {
            Vec3i size = data.size();
            tooltip.add(QaWandTexts.WAND_TOOLTIP_BOUNDS.component(
                    formatPos(data.minPos()),
                    formatPos(data.maxPos()),
                    size.getX() + "x" + size.getY() + "x" + size.getZ())
                    .withStyle(ChatFormatting.AQUA));
        } else {
            tooltip.add(QaWandTexts.WAND_TOOLTIP_EMPTY.component().withStyle(ChatFormatting.GRAY));
        }
        String target = data.exportName().isBlank() ? data.exportNamespace() + ":<unset>" :
                data.exportNamespace() + ":" + data.exportName();
        tooltip.add(QaWandTexts.WAND_TOOLTIP_LABELS.component(data.labels().size(), target)
                .withStyle(ChatFormatting.DARK_GRAY));
    }

    public static String formatPos(@Nullable BlockPos pos) {
        if (pos == null) return "(-)";
        return pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }
}
