package cn.myfrank.stationbuilder;

import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class BuildingSelectorItem extends Item {
    public BuildingSelectorItem(Settings settings) {
        super(settings);
    }

    public static BlockPos getPos1(ItemStack stack) {
        if (!stack.hasNbt()) return null;
        NbtCompound nbt = stack.getNbt();
        if (nbt != null && nbt.contains("pos1")) {
            NbtCompound pos = nbt.getCompound("pos1");
            return new BlockPos(pos.getInt("x"), pos.getInt("y"), pos.getInt("z"));
        }
        return null;
    }

    public static void setPos1(ItemStack stack, BlockPos pos) {
        NbtCompound nbt = stack.getOrCreateNbt();
        if (pos == null) {
            nbt.remove("pos1");
        } else {
            NbtCompound p = new NbtCompound();
            p.putInt("x", pos.getX());
            p.putInt("y", pos.getY());
            p.putInt("z", pos.getZ());
            nbt.put("pos1", p);
        }
    }

    public static BlockPos getPos2(ItemStack stack) {
        if (!stack.hasNbt()) return null;
        NbtCompound nbt = stack.getNbt();
        if (nbt != null && nbt.contains("pos2")) {
            NbtCompound pos = nbt.getCompound("pos2");
            return new BlockPos(pos.getInt("x"), pos.getInt("y"), pos.getInt("z"));
        }
        return null;
    }

    public static void setPos2(ItemStack stack, BlockPos pos) {
        NbtCompound nbt = stack.getOrCreateNbt();
        if (pos == null) {
            nbt.remove("pos2");
        } else {
            NbtCompound p = new NbtCompound();
            p.putInt("x", pos.getX());
            p.putInt("y", pos.getY());
            p.putInt("z", pos.getZ());
            nbt.put("pos2", p);
        }
    }

    private void openGui(ServerPlayerEntity player, ItemStack stack) {
        PacketByteBuf buf = PacketByteBufs.create();
        BlockPos p1 = getPos1(stack);
        BlockPos p2 = getPos2(stack);

        buf.writeBoolean(p1 != null);
        if (p1 != null) buf.writeBlockPos(p1);
        buf.writeBoolean(p2 != null);
        if (p2 != null) buf.writeBlockPos(p2);

        ServerPlayNetworking.send(player, StationBuilder.SYNC_AND_OPEN_PACKET_SELECTOR, buf);
    }

    @Override
    public ActionResult useOnBlock(ItemUsageContext context) {
        World world = context.getWorld();
        PlayerEntity player = context.getPlayer();
        if (player == null) return ActionResult.PASS;

        ItemStack stack = context.getStack();
        if (player.isSneaking()) {
            if (!world.isClient) {
                openGui((ServerPlayerEntity) player, stack);
            }
            return ActionResult.SUCCESS;
        } else {
            if (!world.isClient) {
                BlockPos pos = context.getBlockPos();
                setPos2(stack, pos);
                player.sendMessage(net.minecraft.text.Text.translatable("message.stationbuilder.pos2_set_to:", pos.toShortString())
                        .formatted(net.minecraft.util.Formatting.GREEN), true);
            }
            return ActionResult.SUCCESS;
        }
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        if (user.isSneaking()) {
            if (!world.isClient) {
                openGui((ServerPlayerEntity) user, stack);
            }
            return TypedActionResult.success(stack);
        }
        return TypedActionResult.pass(stack);
    }
}