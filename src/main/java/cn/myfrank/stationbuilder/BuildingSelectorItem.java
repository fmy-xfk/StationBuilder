package cn.myfrank.stationbuilder;

import java.util.List;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class BuildingSelectorItem extends Item {
    public BuildingSelectorItem(Settings settings) {
        super(settings);
    }

    public static BlockPos getPos1(ItemStack stack) {
        NbtComponent comp = stack.get(ModComponents.SELECTOR_DATA);
        if (comp == null) return null;
        NbtCompound nbt = comp.copyNbt();
        if (nbt.contains("pos1")) {
            NbtCompound pos = nbt.getCompound("pos1");
            return new BlockPos(pos.getInt("x"), pos.getInt("y"), pos.getInt("z"));
        }
        return null;
    }

    public static void setPos1(ItemStack stack, BlockPos pos) {
        NbtComponent comp = stack.getOrDefault(ModComponents.SELECTOR_DATA, NbtComponent.DEFAULT);
        NbtCompound nbt = comp.copyNbt();
        if (pos == null) {
            nbt.remove("pos1");
        } else {
            NbtCompound p = new NbtCompound();
            p.putInt("x", pos.getX());
            p.putInt("y", pos.getY());
            p.putInt("z", pos.getZ());
            nbt.put("pos1", p);
        }
        stack.set(ModComponents.SELECTOR_DATA, NbtComponent.of(nbt));
    }

    public static BlockPos getPos2(ItemStack stack) {
        NbtComponent comp = stack.get(ModComponents.SELECTOR_DATA);
        if (comp == null) return null;
        NbtCompound nbt = comp.copyNbt();
        if (nbt.contains("pos2")) {
            NbtCompound pos = nbt.getCompound("pos2");
            return new BlockPos(pos.getInt("x"), pos.getInt("y"), pos.getInt("z"));
        }
        return null;
    }

    public static void setPos2(ItemStack stack, BlockPos pos) {
        NbtComponent comp = stack.getOrDefault(ModComponents.SELECTOR_DATA, NbtComponent.DEFAULT);
        NbtCompound nbt = comp.copyNbt();
        if (pos == null) {
            nbt.remove("pos2");
        } else {
            NbtCompound p = new NbtCompound();
            p.putInt("x", pos.getX());
            p.putInt("y", pos.getY());
            p.putInt("z", pos.getZ());
            nbt.put("pos2", p);
        }
        stack.set(ModComponents.SELECTOR_DATA, NbtComponent.of(nbt));
    }

    private void openGui(ServerPlayerEntity player, ItemStack stack) {
        ServerPlayNetworking.send(
                player,
                new StationBuilder.SyncOpenSelectorPayload(getPos1(stack), getPos2(stack))
        );
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
    public ActionResult use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        if (user.isSneaking()) {
            if (!world.isClient) {
                openGui((ServerPlayerEntity) user, stack);
            }
            return ActionResult.SUCCESS;
        }
        return ActionResult.PASS;
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        tooltip.add(Text.translatable("tooltip.stationbuilder.building_selector_line1"));
        tooltip.add(Text.translatable("tooltip.stationbuilder.building_selector_line2"));
        tooltip.add(Text.translatable("tooltip.stationbuilder.building_selector_line3"));
    }
}