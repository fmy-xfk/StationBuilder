package cn.myfrank.stationbuilder.cpw;

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
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

public class CPWIntegration {
    public static final ResourceLocation WIRE_COIL = ResourceLocation.fromNamespaceAndPath("pantographsandwires", "wire_coil");
    public static final ResourceLocation DEFAULT_MAST = ResourceLocation.fromNamespaceAndPath("pantographsandwires", "lattice_mast");
    public static final ResourceLocation DEFAULT_CANTILEVER = ResourceLocation.fromNamespaceAndPath("pantographsandwires", "cantilever_brown");

    /**
     * 将任意水平方向向量精确转换为对应的 CPWAngle 实例
     */
    public static CPWAngle getCPWAngles(Vec3 dir) {
        return CPWAngle.fromVector(dir);
    }

    public static BlockState applyMastProperties(BlockState state, CPWAngle angle, String part) {
        return applyMastProperties(state, angle.getFacing(), angle.getRotation(), part);
    }

    public static BlockState applyMastProperties(BlockState state, Direction facing, int rotation, String part) {
        for (Property<?> prop : state.getProperties()) {
            if (prop.getName().equals("facing") && prop.getValueClass() == Direction.class) {
                @SuppressWarnings("unchecked")
                Property<Direction> dirProp = (Property<Direction>) prop;
                if (dirProp.getPossibleValues().contains(facing)) {
                    state = state.setValue(dirProp, facing);
                }
            } else if (prop.getName().equals("rotation") && prop.getValueClass() == Integer.class) {
                @SuppressWarnings("unchecked")
                Property<Integer> intProp = (Property<Integer>) prop;
                if (intProp.getPossibleValues().contains(rotation)) {
                    state = state.setValue(intProp, rotation);
                }
            } else if (part != null && prop.getName().equals("part")) {
                for (Comparable<?> val : prop.getPossibleValues()) {
                    if (val.toString().equalsIgnoreCase(part)) {
                        state = setUnchecked(state, prop, val);
                        break;
                    }
                }
            }
        }
        return state;
    }

    @SuppressWarnings("unchecked")
    private static <T extends Comparable<T>> BlockState setUnchecked(BlockState state, Property<?> prop, Comparable<?> val) {
        return state.setValue((Property<T>) prop, (T) val);
    }

    public static void placeMastBlock(ServerLevel world, BlockPos pos, CPWAngle angle, String part, ResourceLocation mastId) {
        BlockState base = CommonUtil.getBlockState(mastId);
        BlockState configured = applyMastProperties(base, angle, part);
        world.setBlock(pos, configured, 3);
    }

    public static void buildMastColumn(ServerLevel world, int x, int z, int minY, int maxY, CPWAngle angle, ResourceLocation mastId) {
        if (minY > maxY) return;
        for (int y = minY; y <= maxY; y++) {
            String part = "in_between";
            if (y == minY && minY != maxY) part = "bottom";
            else if (y == maxY) part = "top";
            placeMastBlock(world, new BlockPos(x, y, z), angle, part, mastId);
        }
    }

    public static boolean placeCantilever(ServerLevel world, BlockPos cantileverPos, CPWAngle angle, ResourceLocation cantileverId) {
        BlockState base = CommonUtil.getBlockState(cantileverId);
        BlockState configured = applyMastProperties(base, angle, null);
        world.setBlock(cantileverPos, configured, 3);
        return true;
    }

    public static void connectWire(ServerLevel world, Player player, BlockPos from, BlockPos to, ResourceLocation wireCoilId) {
        if (from == null || to == null || from.equals(to)) return;

        ItemStack coilStack = ItemStack.EMPTY;
        if (player != null) {
            for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                ItemStack invStack = player.getInventory().getItem(i);
                if (!invStack.isEmpty() && CommonUtil.getItemId(invStack.getItem()).equals(wireCoilId)) {
                    coilStack = invStack;
                    break;
                }
            }
        }
        if (coilStack.isEmpty()) {
            coilStack = CommonUtil.getItemStack(wireCoilId);
        }
        if (coilStack.isEmpty()) return;

        ItemStack originalHeld = player != null ? player.getItemInHand(InteractionHand.MAIN_HAND) : ItemStack.EMPTY;
        if (player != null) {
            player.setItemInHand(InteractionHand.MAIN_HAND, coilStack);
        }

        try {
            ItemStack activeStack = player != null ? player.getItemInHand(InteractionHand.MAIN_HAND) : coilStack;
            BlockHitResult hitFrom = new BlockHitResult(Vec3.atCenterOf(from), Direction.UP, from, false);
            UseOnContext contextFrom = new UseOnContext(world, player, InteractionHand.MAIN_HAND, activeStack, hitFrom);
            activeStack.useOn(contextFrom);

            activeStack = player != null ? player.getItemInHand(InteractionHand.MAIN_HAND) : activeStack;
            BlockHitResult hitTo = new BlockHitResult(Vec3.atCenterOf(to), Direction.UP, to, false);
            UseOnContext contextTo = new UseOnContext(world, player, InteractionHand.MAIN_HAND, activeStack, hitTo);
            activeStack.useOn(contextTo);
        } finally {
            if (player != null) {
                player.setItemInHand(InteractionHand.MAIN_HAND, originalHeld);
            }
        }
    }

    public static boolean isCPWBlock(Level world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        if (state.isAir()) return false;
        ResourceLocation id = CommonUtil.getBlockId(state.getBlock());
        return id != null && id.getNamespace().equals("pantographsandwires");
    }
}