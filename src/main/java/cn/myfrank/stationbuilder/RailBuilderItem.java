package cn.myfrank.stationbuilder;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

import static cn.myfrank.stationbuilder.StationBuilder.isMtrLoaded;

public class RailBuilderItem extends Item {
    public RailBuilderItem(Item.Properties properties) {
        super(properties);
    }

    private static void updateCustomModelData(ItemStack stack) {
        if (RailBuilderState.isBuilding(stack)) {
            stack.set(
                    DataComponents.CUSTOM_MODEL_DATA,
                    new CustomModelData(1)
            );
        } else {
            stack.remove(DataComponents.CUSTOM_MODEL_DATA);
        }
    }

    private void openGui(ServerPlayer player, ItemStack stack) {
        RailBuilderConfig cfg = RailBuilderConfig.fromItem(stack);
        cfg.saveToItem(stack);

        PacketDistributor.sendToPlayer(
                player,
                new StationBuilder.SyncOpenRailPayload(cfg.toNbt())
        );
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        Player player = context.getPlayer();
        if (player == null) return InteractionResult.PASS;

        if (!isMtrLoaded()) {
            if (level.isClientSide) {
                player.displayClientMessage(Component.translatable("message.stationbuilder.rail_builder.no_mtr"), true);
            }
            return InteractionResult.FAIL;
        }

        ItemStack stack = context.getItemInHand();
        RailBuilderConfig cfg = RailBuilderConfig.fromItem(stack);

        if (player.isShiftKeyDown()) {
            if (!level.isClientSide) {
                openGui((ServerPlayer) player, stack);
            }
            return InteractionResult.SUCCESS;
        }

        if (!level.isClientSide) {
            BlockPos pos = context.getClickedPos();
            var serverWorld = ((ServerPlayer) player).serverLevel();

            if (!MTRIntegration.isRailNode(serverWorld, pos) && !level.getBlockState(pos).canBeReplaced()) {
                pos = pos.relative(context.getClickedFace());
            }

            var last = RailBuilderState.getLastNodesAndAngle(stack);

            if (last == null) {
                var nodes = RailGenerator.placeFirstRailNodes(serverWorld, pos, player, cfg);
                RailBuilderState.setLastNodesAndAngle(stack, nodes, player.getYRot());
            } else {
                var nodes = RailGenerator.buildRails(serverWorld, last.left(), pos, cfg, player);
                RailBuilderState.setLastNodesAndAngle(stack, nodes, player.getYRot());
            }

            updateCustomModelData(stack);
        }

        return InteractionResult.SUCCESS;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.isShiftKeyDown()) {
            if (!level.isClientSide) {
                openGui((ServerPlayer) player, stack);
            }
            return InteractionResultHolder.success(stack);
        }

        return InteractionResultHolder.pass(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.stationbuilder.rail_builder.line1"));
        tooltip.add(Component.translatable("tooltip.stationbuilder.rail_builder.line2"));
        tooltip.add(Component.translatable("tooltip.stationbuilder.rail_builder.line3"));
        var lastPair = RailBuilderState.getLastNodesAndAngle(stack);
        if (lastPair == null) return;
        var lastNodes = lastPair.left();
        if (lastNodes == null || lastNodes.isEmpty()) return;
        BlockPos last = lastNodes.get(0);
        tooltip.add(
                Component.translatable(
                        "tooltip.stationbuilder.start_pos",
                        last.getX(),
                        last.getY(),
                        last.getZ()
                ).withStyle(ChatFormatting.GREEN)
        );
    }
}
