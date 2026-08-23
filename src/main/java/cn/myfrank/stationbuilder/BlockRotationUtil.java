package cn.myfrank.stationbuilder;

import net.minecraft.block.BlockState;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.state.property.EnumProperty;
import net.minecraft.state.property.Property;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.math.Direction;

public class BlockRotationUtil {
    public static Direction rotateDirection(Direction dir, BlockRotation rotation) {
        if (dir.getAxis() == Direction.Axis.Y) return dir;
        return switch (rotation) {
            case CLOCKWISE_90 -> dir.rotateYClockwise();
            case CLOCKWISE_180 -> dir.rotateYClockwise().rotateYClockwise();
            case COUNTERCLOCKWISE_90 -> dir.rotateYCounterclockwise();
            default -> dir;
        };
    }

    public static BlockState forceRotateState(BlockState state, BlockRotation rotation) {
        if (rotation == BlockRotation.NONE) return state;

        BlockState rotated = state.rotate(rotation);
        boolean changed = !rotated.equals(state);

        if (!changed) {
            for (Property<?> property : state.getProperties()) {
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
                } else if (StationBuilder.isMtrLoaded() && property instanceof BooleanProperty boolProp) {
                    // 兼容 MTR 专属 Boolean 型 "facing" 的 90° / 270° 偏转
                    if (property.getName().equals("facing") && (rotation == BlockRotation.CLOCKWISE_90 || rotation == BlockRotation.COUNTERCLOCKWISE_90)) {
                        rotated = rotated.with(boolProp, !state.get(boolProp));
                    }
                }
            }
        }
        return rotated;
    }
}