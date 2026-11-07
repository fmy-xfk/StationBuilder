package cn.myfrank.stationbuilder.cee;

import cn.myfrank.stationbuilder.utils.CommonUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

public class CEEIntegration {
    public static final ResourceLocation CATENARY_HOLDER = ResourceLocation.fromNamespaceAndPath("electroenergetics", "catenary_holder");

    public static boolean isCatenaryHolder(Level world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        return CommonUtil.getBlockId(state.getBlock()).equals(CATENARY_HOLDER);
    }

    public static boolean placeCatenaryHolder(ServerLevel world, BlockPos pos) {
        if (!CommonUtil.hasBlock(CATENARY_HOLDER)) return false;
        var state = CommonUtil.getBlockState(CATENARY_HOLDER);
        world.setBlock(pos, state, 3);
        return true;
    }

    public static void connectWire(ServerLevel world, Player player, BlockPos from, BlockPos to, ResourceLocation spoolId) {
        ItemStack spoolStack = CommonUtil.getItemStack(spoolId);
        if (spoolStack.isEmpty()) return;

        BlockHitResult hitFrom = new BlockHitResult(Vec3.atCenterOf(from), Direction.UP, from, false);
        UseOnContext contextFrom = new UseOnContext(world, player, InteractionHand.MAIN_HAND, spoolStack, hitFrom);
        spoolStack.useOn(contextFrom);

        BlockHitResult hitTo = new BlockHitResult(Vec3.atCenterOf(to), Direction.UP, to, false);
        UseOnContext contextTo = new UseOnContext(world, player, InteractionHand.MAIN_HAND, spoolStack, hitTo);
        spoolStack.useOn(contextTo);
    }
}