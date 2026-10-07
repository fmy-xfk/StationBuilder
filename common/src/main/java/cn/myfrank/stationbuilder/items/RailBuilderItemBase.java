package cn.myfrank.stationbuilder.items;

import cn.myfrank.stationbuilder.utils.CommonUtil;
import cn.myfrank.stationbuilder.generator.RailGenerator;
import cn.myfrank.stationbuilder.StationBuilder;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.List;

public class RailBuilderItemBase extends Item {
    public RailBuilderItemBase(Settings settings) {
        super(settings);
    }

    private static void updateCustomModelData(ItemStack stack) {
        if (RailBuilderState.isBuilding(stack)) {
            stack.set(
                    DataComponentTypes.CUSTOM_MODEL_DATA,
                    CommonUtil.customModelData(1)
            );
        } else {
            stack.remove(DataComponentTypes.CUSTOM_MODEL_DATA);
        }
    }

    private void openGui(ServerPlayerEntity player, ItemStack stack) {
        RailBuilderConfig cfg = RailBuilderConfig.fromItem(stack);
        cfg.saveToItem(stack);

        ServerPlayNetworking.send(
                player,
                new StationBuilder.SyncOpenRailPayload(cfg.toNbt())
        );
    }

    @Override
    public ActionResult useOnBlock(ItemUsageContext context) {
        World world = context.getWorld();
        PlayerEntity player = context.getPlayer();
        if (player == null) return ActionResult.PASS;

        ItemStack stack = context.getStack();
        RailBuilderConfig cfg = RailBuilderConfig.fromItem(stack);

        if (player.isSneaking()) {
            if (!world.isClient) {
                if (!(player instanceof ServerPlayerEntity serverPlayer)) return ActionResult.PASS;
                openGui(serverPlayer, stack);
            }
            return ActionResult.SUCCESS;
        }

        if (!world.isClient && player instanceof ServerPlayerEntity serverPlayer) {
            BlockPos pos = context.getBlockPos();
            var serverWorld = serverPlayer.getServerWorld();
            if (!RailGenerator.isRailNode(serverWorld, pos) && !world.getBlockState(pos).isReplaceable()) {
                pos = pos.offset(context.getSide());
            }

            var last = RailBuilderState.getLastNodesAndAngle(stack);

            if (last == null) {
                var nodes = RailGenerator.placeFirstRailNodes(serverWorld, pos, player, cfg);
                RailBuilderState.setLastNodesAndAngle(stack, nodes, player.getYaw());
            } else {
                var nodes = RailGenerator.buildRails(serverWorld, last.left(), pos, cfg, player);
                RailBuilderState.setLastNodesAndAngle(stack, nodes, player.getYaw());
            }

            updateCustomModelData(stack);
        }

        return ActionResult.SUCCESS;
    }

    protected ActionResult handleUse(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        if (user.isSneaking()) {
            if (!world.isClient() && user instanceof ServerPlayerEntity serverPlayer) {
                openGui(serverPlayer, stack);
            }
            return ActionResult.SUCCESS;
        }
        return ActionResult.PASS;
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        tooltip.add(Text.translatable("tooltip.stationbuilder.rail_builder.line1"));
        tooltip.add(Text.translatable("tooltip.stationbuilder.rail_builder.line2"));
        tooltip.add(Text.translatable("tooltip.stationbuilder.rail_builder.line3"));
        var lastPair = RailBuilderState.getLastNodesAndAngle(stack);
        if (lastPair == null) return;
        var lastNodes = lastPair.left();
        if (lastNodes == null || lastNodes.isEmpty()) return;
        BlockPos last = lastNodes.get(0);
        tooltip.add(
            Text.translatable(
                "tooltip.stationbuilder.start_pos",
                last.getX(),
                last.getY(),
                last.getZ()
            ).formatted(Formatting.GREEN)
        );
    }

}