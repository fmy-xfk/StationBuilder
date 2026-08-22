package cn.myfrank.stationbuilder;

import net.minecraft.block.BlockState;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.state.property.EnumProperty;
import net.minecraft.state.property.Property;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.math.Direction;

public class BlockRotationUtil {

    // 1. 通用的方向旋转算法
    public static Direction rotateDirection(Direction dir, BlockRotation rotation) {
        if (dir.getAxis() == Direction.Axis.Y) return dir; // 垂直朝向不绕 Y 轴旋转
        return switch (rotation) {
            case CLOCKWISE_90 -> dir.rotateYClockwise();
            case CLOCKWISE_180 -> dir.rotateYClockwise().rotateYClockwise();
            case COUNTERCLOCKWISE_90 -> dir.rotateYCounterclockwise();
            default -> dir;
        };
    }

    // 2. 强行旋转状态应用
    public static BlockState forceRotateState(BlockState state, BlockRotation rotation) {
        if (rotation == BlockRotation.NONE) return state;

        // 尝试使用 vanilla 默认逻辑旋转（兼容绝大部分原版与第三方模组方块）
        BlockState rotated = state.rotate(rotation);
        boolean changed = !rotated.equals(state);

        // 如果 vanilla 无法旋转，则检索其属性进行强制纠正
        if (!changed) {
            for (Property<?> property : state.getProperties()) {

                // 情况 A：无 MTR / 纯通用 - 强行校正任意 EnumProperty<Direction> 类型的朝向属性
                if (property instanceof EnumProperty<?> enumProp) {
                    if (enumProp.getType() == Direction.class) {
                        @SuppressWarnings("unchecked")
                        EnumProperty<Direction> dirProp = (EnumProperty<Direction>) enumProp;
                        Direction currentDir = state.get(dirProp);
                        Direction rotatedDir = rotateDirection(currentDir, rotation);
                        if (dirProp.getValues().contains(rotatedDir)) {
                            rotated = rotated.with(dirProp, rotatedDir);
                        }
                    }
                }

                // 情况 B：有 MTR - 特殊校正 MTR 轨边节点（BlockNode）特有的布尔类型 "facing"
                else if (StationBuilder.isMtrLoaded() && property instanceof BooleanProperty boolProp) {
                    if (property.getName().equals("facing") &&
                            (rotation == BlockRotation.CLOCKWISE_90 || rotation == BlockRotation.COUNTERCLOCKWISE_90)) {
                        rotated = rotated.with(boolProp, !state.get(boolProp));
                    }
                }
            }
        }
        return rotated;
    }
}