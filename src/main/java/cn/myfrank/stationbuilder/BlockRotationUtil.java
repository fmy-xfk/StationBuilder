package cn.myfrank.stationbuilder;

import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.core.Direction;

public class BlockRotationUtil {
    public static Direction rotateDirection(Direction dir, Rotation rotation) {
        if (dir.getAxis() == Direction.Axis.Y) return dir;
        return switch (rotation) {
            case CLOCKWISE_90 -> dir.getClockWise();
            case CLOCKWISE_180 -> dir.getClockWise().getClockWise();
            case COUNTERCLOCKWISE_90 -> dir.getCounterClockWise();
            default -> dir;
        };
    }

    public static BlockState forceRotateState(BlockState state, Rotation rotation) {
        if (rotation == Rotation.NONE) return state;

        BlockState rotated = state.rotate(rotation);
        boolean changed = !rotated.equals(state);

        if (!changed) {
            for (Property<?> property : state.getProperties()) {
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
                } else if (StationBuilder.isMtrLoaded() && property instanceof BooleanProperty boolProp) {
                    if (property.getName().equals("facing") && (rotation == Rotation.CLOCKWISE_90 || rotation == Rotation.COUNTERCLOCKWISE_90)) {
                        rotated = rotated.setValue(boolProp, !state.getValue(boolProp));
                    }
                }
            }
        }
        return rotated;
    }
}
