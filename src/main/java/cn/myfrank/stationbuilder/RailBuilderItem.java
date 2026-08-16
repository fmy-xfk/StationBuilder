package cn.myfrank.stationbuilder;

import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

import java.util.List;

public class RailBuilderItem extends Item {
    public RailBuilderItem(Properties properties) { super(properties); }

    private void openGui(ServerPlayer player, ItemStack stack) {
        RailBuilderConfig cfg = RailBuilderConfig.fromItem(stack);
        cfg.saveToItem(stack);
        StationBuilder.LOGGER.info("[Item] Preparing to send PACKET_SYNC_OPEN_RAIL to player");
        PlatformServices.sendToPlayer(player, StationBuilder.PACKET_SYNC_OPEN_RAIL,
                StationBuilder.buf(buf -> buf.writeNbt(cfg.toNbt())));
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level world = context.getLevel();
        Player player = context.getPlayer();
        if (player == null) return InteractionResult.PASS;

        StationBuilder.LOGGER.info("[Item] useOn called. isClient: {}, crouching: {}", world.isClientSide, player.isCrouching());

        if (!StationBuilder.isMtrLoaded()) {
            if (world.isClientSide) player.displayClientMessage(Component.translatable("message.stationbuilder.rail_builder.no_mtr"), true);
            return InteractionResult.FAIL;
        }
        ItemStack stack = context.getItemInHand();
        RailBuilderConfig cfg = RailBuilderConfig.fromItem(stack);
        if (player.isCrouching()) {
            if (!world.isClientSide && player instanceof ServerPlayer serverPlayer) {
                openGui(serverPlayer, stack);
            }
            return InteractionResult.SUCCESS;
        }
        if (!world.isClientSide && player instanceof ServerPlayer serverPlayer) {
            BlockPos pos = context.getClickedPos();
            var serverWorld = serverPlayer.serverLevel();
            if (!MTRIntegration.isRailNode(serverWorld, pos) && !world.getBlockState(pos).canBeReplaced()) pos = pos.relative(context.getClickedFace());
            var last = RailBuilderState.getLastNodesAndAngle(stack);
            if (last == null) {
                var nodes = RailGenerator.placeFirstRailNodes(serverWorld, pos, player, cfg);
                RailBuilderState.setLastNodesAndAngle(stack, nodes, player.getYRot());
            } else {
                var nodes = RailGenerator.buildRails(serverWorld, last.left(), pos, cfg, player);
                RailBuilderState.setLastNodesAndAngle(stack, nodes, player.getYRot());
            }
            stack.getOrCreateTag().putInt("CustomModelData", 1);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level world, Player user, InteractionHand hand) {
        ItemStack stack = user.getItemInHand(hand);
        StationBuilder.LOGGER.info("[Item] use (air right click) called. isClient: {}, crouching: {}", world.isClientSide, user.isCrouching());
        if (user.isCrouching()) {
            if (!world.isClientSide && user instanceof ServerPlayer serverPlayer) openGui(serverPlayer, stack);
            return InteractionResultHolder.success(stack);
        }
        return InteractionResultHolder.pass(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, Level world, List<Component> tooltip, TooltipFlag context) {
        tooltip.add(Component.translatable("tooltip.stationbuilder.rail_builder.line1"));
        tooltip.add(Component.translatable("tooltip.stationbuilder.rail_builder.line2"));
        tooltip.add(Component.translatable("tooltip.stationbuilder.rail_builder.line3"));
        var lastPair = RailBuilderState.getLastNodesAndAngle(stack);
        if (lastPair == null || lastPair.left() == null || lastPair.left().isEmpty()) return;
        BlockPos last = lastPair.left().get(0);
        tooltip.add(Component.translatable("tooltip.stationbuilder.start_pos", last.getX(), last.getY(), last.getZ()).withStyle(ChatFormatting.GREEN));
    }
}