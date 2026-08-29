package cn.myfrank.stationbuilder;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.Property;

public class BlockRotationUtil {

    // 1. 通用的方向旋转算法
    public static Direction rotateDirection(Direction dir, Rotation rotation) {
        if (dir.getAxis() == Direction.Axis.Y) return dir; // 垂直朝向不绕 Y 轴旋转
        return switch (rotation) {
            case CLOCKWISE_90 -> dir.getClockWise();
            case CLOCKWISE_180 -> dir.getClockWise().getClockWise();
            case COUNTERCLOCKWISE_90 -> dir.getCounterClockWise();
            default -> dir;
        };
    }

    // 2. 强行旋转状态应用
    public static BlockState forceRotateState(BlockState state, Rotation rotation) {
        if (rotation == Rotation.NONE) return state;

        // 尝试使用 vanilla 默认逻辑旋转（兼容绝大部分原版与第三方模组方块）
        BlockState rotated = state.rotate(rotation);
        boolean changed = !rotated.equals(state);

        // 如果 vanilla 无法旋转，则检索其属性进行强制纠正
        if (!changed) {
            for (Property<?> property : state.getProperties()) {

                // 情况 A：无 MTR / 纯通用 - 强行校正任意 EnumProperty<Direction> 类型的朝向属性
                if (property instanceof EnumProperty<?> enumProp) {
                    if (enumProp.getValueClass() == Direction.class) {
                        @SuppressWarnings("unchecked")
                        EnumProperty<Direction> dirProp = (EnumProperty<Direction>) enumProp;
                        Direction currentDir = state.getValue(dirProp);
                        Direction rotatedDir = rotateDirection(currentDir, rotation);
                        if (dirProp.getPossibleValues().contains(rotatedDir)) {
                            rotated = rotated.setValue(dirProp, rotatedDir);
                        }
                    }
                }

                // 情况 B：有 MTR - 特殊校正 MTR 轨边节点（BlockNode）特有的布尔类型 "facing"
                else if (StationBuilder.isMtrLoaded() && property instanceof BooleanProperty boolProp) {
                    if (property.getName().equals("facing") &&
                            (rotation == Rotation.CLOCKWISE_90 || rotation == Rotation.COUNTERCLOCKWISE_90)) {
                        rotated = rotated.setValue(boolProp, !state.getValue(boolProp));
                    }
                }
            }
        }
        return rotated;
    }
}
