package cn.myfrank.stationbuilder;

import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.network.chat.Component;

public class BuildingSelectorItem extends Item {
    public BuildingSelectorItem(Properties properties) {
        super(properties);
    }

    public static BlockPos getPos1(ItemStack stack) {
        if (!stack.hasTag()) return null;
        CompoundTag nbt = stack.getTag();
        if (nbt != null && nbt.contains("pos1")) {
            CompoundTag pos = nbt.getCompound("pos1");
            return new BlockPos(pos.getInt("x"), pos.getInt("y"), pos.getInt("z"));
        }
        return null;
    }

    public static void setPos1(ItemStack stack, BlockPos pos) {
        CompoundTag nbt = stack.getOrCreateTag();
        if (pos == null) {
            nbt.remove("pos1");
        } else {
            CompoundTag p = new CompoundTag();
            p.putInt("x", pos.getX());
            p.putInt("y", pos.getY());
            p.putInt("z", pos.getZ());
            nbt.put("pos1", p);
        }
    }

    public static BlockPos getPos2(ItemStack stack) {
        if (!stack.hasTag()) return null;
        CompoundTag nbt = stack.getTag();
        if (nbt != null && nbt.contains("pos2")) {
            CompoundTag pos = nbt.getCompound("pos2");
            return new BlockPos(pos.getInt("x"), pos.getInt("y"), pos.getInt("z"));
        }
        return null;
    }

    public static void setPos2(ItemStack stack, BlockPos pos) {
        CompoundTag nbt = stack.getOrCreateTag();
        if (pos == null) {
            nbt.remove("pos2");
        } else {
            CompoundTag p = new CompoundTag();
            p.putInt("x", pos.getX());
            p.putInt("y", pos.getY());
            p.putInt("z", pos.getZ());
            nbt.put("pos2", p);
        }
    }

    private void openGui(ServerPlayer player, ItemStack stack) {
        BlockPos p1 = getPos1(stack);
        BlockPos p2 = getPos2(stack);

        PlatformServices.sendToPlayer(player, StationBuilder.PACKET_SYNC_OPEN_SELECTOR,
                StationBuilder.buf(buf -> {
                    buf.writeBoolean(p1 != null);
                    if (p1 != null) buf.writeBlockPos(p1);
                    buf.writeBoolean(p2 != null);
                    if (p2 != null) buf.writeBlockPos(p2);
                }));
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level world = context.getLevel();
        Player player = context.getPlayer();
        if (player == null) return InteractionResult.PASS;

        ItemStack stack = context.getItemInHand();
        if (player.isCrouching()) {
            if (!world.isClientSide && player instanceof ServerPlayer serverPlayer) {
                openGui(serverPlayer, stack);
            }
            return InteractionResult.SUCCESS;
        } else {
            if (!world.isClientSide) {
                BlockPos pos = context.getClickedPos();
                setPos2(stack, pos);
                player.sendSystemMessage(Component.translatable("message.stationbuilder.pos2_set_to:", pos.toShortString())
                        .withStyle(net.minecraft.ChatFormatting.GREEN));
            }
            return InteractionResult.SUCCESS;
        }
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level world, Player user, InteractionHand hand) {
        ItemStack stack = user.getItemInHand(hand);
        if (user.isCrouching()) {
            if (!world.isClientSide && user instanceof ServerPlayer serverPlayer) {
                openGui(serverPlayer, stack);
            }
            return InteractionResultHolder.success(stack);
        }
        return InteractionResultHolder.pass(stack);
    }
}
