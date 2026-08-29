package cn.myfrank.stationbuilder;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.neoforged.neoforge.network.PacketDistributor;

public class BuildingSelectorItem extends Item {
    public BuildingSelectorItem(Item.Properties properties) {
        super(properties);
    }

    public static BlockPos getPos1(ItemStack stack) {
        CustomData comp = stack.get(ModComponents.SELECTOR_DATA.get());
        if (comp == null) return null;
        CompoundTag nbt = comp.copyTag();
        if (nbt.contains("pos1")) {
            CompoundTag pos = nbt.getCompound("pos1");
            return new BlockPos(pos.getInt("x"), pos.getInt("y"), pos.getInt("z"));
        }
        return null;
    }

    public static void setPos1(ItemStack stack, BlockPos pos) {
        CustomData comp = stack.getOrDefault(ModComponents.SELECTOR_DATA.get(), CustomData.EMPTY);
        CompoundTag nbt = comp.copyTag();
        if (pos == null) {
            nbt.remove("pos1");
        } else {
            CompoundTag p = new CompoundTag();
            p.putInt("x", pos.getX());
            p.putInt("y", pos.getY());
            p.putInt("z", pos.getZ());
            nbt.put("pos1", p);
        }
        stack.set(ModComponents.SELECTOR_DATA.get(), CustomData.of(nbt));
    }

    public static BlockPos getPos2(ItemStack stack) {
        CustomData comp = stack.get(ModComponents.SELECTOR_DATA.get());
        if (comp == null) return null;
        CompoundTag nbt = comp.copyTag();
        if (nbt.contains("pos2")) {
            CompoundTag pos = nbt.getCompound("pos2");
            return new BlockPos(pos.getInt("x"), pos.getInt("y"), pos.getInt("z"));
        }
        return null;
    }

    public static void setPos2(ItemStack stack, BlockPos pos) {
        CustomData comp = stack.getOrDefault(ModComponents.SELECTOR_DATA.get(), CustomData.EMPTY);
        CompoundTag nbt = comp.copyTag();
        if (pos == null) {
            nbt.remove("pos2");
        } else {
            CompoundTag p = new CompoundTag();
            p.putInt("x", pos.getX());
            p.putInt("y", pos.getY());
            p.putInt("z", pos.getZ());
            nbt.put("pos2", p);
        }
        stack.set(ModComponents.SELECTOR_DATA.get(), CustomData.of(nbt));
    }

    private void openGui(ServerPlayer player, ItemStack stack) {
        PacketDistributor.sendToPlayer(
                player,
                new StationBuilder.SyncOpenSelectorPayload(getPos1(stack), getPos2(stack))
        );
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        Player player = context.getPlayer();
        if (player == null) return InteractionResult.PASS;

        ItemStack stack = context.getItemInHand();
        if (player.isShiftKeyDown()) {
            if (!level.isClientSide) {
                openGui((ServerPlayer) player, stack);
            }
            return InteractionResult.SUCCESS;
        } else {
            if (!level.isClientSide) {
                BlockPos pos = context.getClickedPos();
                setPos2(stack, pos);
                player.displayClientMessage(
                        Component.translatable("message.stationbuilder.pos2_set_to:", pos.toShortString())
                                .withStyle(ChatFormatting.GREEN),
                        true
                );
            }
            return InteractionResult.SUCCESS;
        }
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
        tooltip.add(Component.translatable("tooltip.stationbuilder.building_selector_line1"));
        tooltip.add(Component.translatable("tooltip.stationbuilder.building_selector_line2"));
        tooltip.add(Component.translatable("tooltip.stationbuilder.building_selector_line3"));
    }
}
