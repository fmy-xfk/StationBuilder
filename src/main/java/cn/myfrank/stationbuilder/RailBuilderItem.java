package cn.myfrank.stationbuilder;

import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import static cn.myfrank.stationbuilder.StationBuilder.SYNC_AND_OPEN_PACKET_RAIL;
import static cn.myfrank.stationbuilder.StationBuilder.isMtrLoaded;

import java.util.List;

public class RailBuilderItem extends Item {
    public RailBuilderItem(Settings settings) {
        super(settings);
    }

    private void openGui(ServerPlayerEntity player, ItemStack stack) {
        RailBuilderConfig cfg = RailBuilderConfig.fromItem(stack);
        cfg.saveToItem(stack);

        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeNbt(cfg.toNbt());

        ServerPlayNetworking.send(player, SYNC_AND_OPEN_PACKET_RAIL, buf);
    }

    @Override
    public ActionResult useOnBlock(ItemUsageContext context) {
        World world = context.getWorld();
        PlayerEntity player = context.getPlayer();
        if (player == null) return ActionResult.PASS;

        if (!isMtrLoaded()) {
            if(world.isClient) {
                player.sendMessage(Text.translatable("message.stationbuilder.rail_builder.no_mtr"), true);
            }
            return ActionResult.FAIL;
        }

        ItemStack stack = context.getStack();
        RailBuilderConfig cfg = RailBuilderConfig.fromItem(stack);

        if (player.isSneaking()) {
            if (!world.isClient) {
                openGui((ServerPlayerEntity) player, stack);
            }
            return ActionResult.SUCCESS;
        }

        if (!world.isClient) {
            BlockPos pos = context.getBlockPos();
            var serverWorld = ((ServerPlayerEntity) player).getServerWorld();

            if (!MTRIntegration.isRailNode(serverWorld, pos) && !world.getBlockState(pos).isReplaceable()) {
                pos = pos.offset(context.getSide());
            }

            var last = RailBuilderState.getLastNodesAndAngle(stack);

            if (last == null) {
                var nodes = RailGenerator.placeFirstRailNodes(serverWorld, pos, player, cfg);
                RailBuilderState.setLastNodesAndAngle(stack, nodes, player.getYaw());
                stack.getOrCreateNbt().putInt("CustomModelData", 1);
            } else {
                var nodes = RailGenerator.buildRails(serverWorld, last.left(), pos, cfg, player);
                RailBuilderState.setLastNodesAndAngle(stack, nodes, player.getYaw());
                stack.getOrCreateNbt().putInt("CustomModelData", 1);
            }
        }

        return ActionResult.SUCCESS;
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        if (user.isSneaking()) { // Shift
            if (!world.isClient) {
                openGui((ServerPlayerEntity) user, stack);
            }
            return TypedActionResult.success(stack);
        }

        return TypedActionResult.pass(stack);
    }

    @Override
    public void appendTooltip(
            ItemStack stack,
            World world,
            List<Text> tooltip,
            TooltipContext context
    ) {
        var lastPair = RailBuilderState.getLastNodesAndAngle(stack);
        if (lastPair == null) return;
        var lastNodes = lastPair.left();
        if (lastNodes == null) return;
        BlockPos last = lastNodes.get(0);
        tooltip.add(
            Text.translatable(
                "tooltip.stationbuilder.rail_builder.start_pos",
                last.getX(),
                last.getY(),
                last.getZ()
            ).formatted(Formatting.GREEN)
        );
    }
}
