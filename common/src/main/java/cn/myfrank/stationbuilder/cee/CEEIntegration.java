package cn.myfrank.stationbuilder.cee;

import cn.myfrank.stationbuilder.utils.CommonUtil;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.registry.Registries;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

public class CEEIntegration {
    public static final Identifier CATENARY_HOLDER = Identifier.of("electroenergetics", "catenary_holder");

    /**
     * 判断指定位置是否已是 CEE 接触网悬挂点
     */
    public static boolean isCatenaryHolder(World world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        return Registries.BLOCK.getId(state.getBlock()).equals(CATENARY_HOLDER);
    }

    /**
     * 放置接触网节点方块
     */
    public static boolean placeCatenaryHolder(ServerWorld world, BlockPos pos) {
        if (!CommonUtil.hasBlock(CATENARY_HOLDER)) return false;
        BlockState state = CommonUtil.getBlockState(CATENARY_HOLDER);
        world.setBlockState(pos, state, Block.NOTIFY_ALL);
        return true;
    }

    /**
     * 模拟手持线轴（Spool）依次右键两个接触网节点建立导线连接
     */
    public static void connectWire(ServerWorld world, PlayerEntity player, BlockPos from, BlockPos to, Identifier spoolId) {
        ItemStack spoolStack = CommonUtil.getItemStack(spoolId);
        if (spoolStack.isEmpty()) return;

        // 第一次点击起点节点
        BlockHitResult hitFrom = new BlockHitResult(Vec3d.ofCenter(from), Direction.UP, from, false);
        ItemUsageContext contextFrom = new ItemUsageContext(world, player, Hand.MAIN_HAND, spoolStack, hitFrom);
        spoolStack.useOnBlock(contextFrom);

        // 第二次点击终点节点完成接线
        BlockHitResult hitTo = new BlockHitResult(Vec3d.ofCenter(to), Direction.UP, to, false);
        ItemUsageContext contextTo = new ItemUsageContext(world, player, Hand.MAIN_HAND, spoolStack, hitTo);
        spoolStack.useOnBlock(contextTo);
    }
}