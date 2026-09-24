package cn.myfrank.stationbuilder;

import com.simibubi.create.content.trains.track.TrackBlock;
import com.simibubi.create.content.trains.track.TrackShape;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;

public class CreateIntegration {
    public static BlockState getTrackBlockState(Direction facing) {
        BlockState state = BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("create", "track")).defaultBlockState();
        TrackShape shape = switch (facing) {
            case NORTH, SOUTH -> TrackShape.ZO;
            case EAST, WEST -> TrackShape.XO;
            default -> TrackShape.NONE;
        };
        if (shape != TrackShape.NONE) {
            state = state.setValue(TrackBlock.SHAPE, shape);
        }

        return state;
    }
}