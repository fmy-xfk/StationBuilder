package cn.myfrank.stationbuilder;

import net.minecraft.registry.Registries;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import org.mtr.core.data.Position;
import org.mtr.mapping.holder.BlockState;
import org.mtr.mapping.holder.Property;
import org.mtr.mapping.mapper.DirectionHelper;
import org.mtr.mod.Init;
import top.mcmtr.core.data.Catenary;
import top.mcmtr.core.data.CatenaryType;
import top.mcmtr.core.data.OffsetPosition;
import top.mcmtr.mod.Blocks;
import top.mcmtr.mod.blocks.BlockNodeBase;
import top.mcmtr.mod.packet.MSDPacketDeleteData;
import top.mcmtr.mod.packet.MSDPacketUpdateData;

public class MSDIntegration {
    public static boolean isCatenaryNode(ServerWorld world, BlockPos pos) {
        return world.getBlockState(pos).getBlock() instanceof BlockNodeBase;
    }

    public static void clearCatenary(ServerWorld world, BlockPos pos) {
        if(MSDIntegration.isCatenaryNode(world, pos)) {
            MSDPacketDeleteData.sendDirectlyToServerCatenaryNodePosition(
                    new org.mtr.mapping.holder.ServerWorld(world),
                    Init.blockPosToPosition(new org.mtr.mapping.holder.BlockPos(pos))
            );
        }
    }

    public static void placeCatenaryNode(ServerWorld world, BlockPos pos, Direction direction, boolean top) {
        var state = (top ? Blocks.CATENARY_WITH_LONG_TOP : Blocks.CATENARY_WITH_LONG).get().getDefaultState();
        state = state.with(new Property<>(DirectionHelper.FACING.data), direction);
        world.setBlockState(pos, state.data, 3);
    }

    public static void placeCatenaryNode(ServerWorld world, BlockPos pos, Direction direction, Identifier block) {
        var state = new org.mtr.mapping.holder.BlockState(Registries.BLOCK.get(block).getDefaultState());
        state = state.with(new Property<>(DirectionHelper.FACING.data), direction);
        world.setBlockState(pos, state.data, 3);
    }

    private static boolean connectCatenary(ServerWorld world, BlockPos a, BlockPos b, CatenaryType c) {
        BlockNodeBase.BlockNodeBaseEntity startBlockEntity = (BlockNodeBase.BlockNodeBaseEntity)world.getBlockEntity(a);
        BlockNodeBase.BlockNodeBaseEntity endBlockEntity = (BlockNodeBase.BlockNodeBaseEntity)world.getBlockEntity(b);
        if (startBlockEntity == null || endBlockEntity == null) return false;
        OffsetPosition offsetPositionStart = startBlockEntity.getOffsetPosition();
        OffsetPosition offsetPositionEnd = endBlockEntity.getOffsetPosition();
        Position positionStart = Init.blockPosToPosition(new org.mtr.mapping.holder.BlockPos(a));
        var stateStart = new BlockState(world.getBlockState(a));
        var stateEnd = new BlockState(world.getBlockState(b));
        Position positionEnd = Init.blockPosToPosition(new org.mtr.mapping.holder.BlockPos(b));
        if (Catenary.verifyPosition(positionStart, positionEnd, offsetPositionStart, offsetPositionEnd)) {
            Catenary catenary = new Catenary(positionStart, positionEnd, offsetPositionStart, offsetPositionEnd, c);
            world.setBlockState(a, stateStart.with(new Property<>(BlockNodeBase.IS_CONNECTED.data), true).data);
            world.setBlockState(b, stateEnd.with(new Property<>(BlockNodeBase.IS_CONNECTED.data), true).data);
            MSDPacketUpdateData.sendDirectlyToServerCatenary(new org.mtr.mapping.holder.ServerWorld(world), catenary);
        } else {
            return false;
        }
        return true;
    }

    public static boolean connectCatenary(ServerWorld world, BlockPos a, BlockPos b, int cType) {
        var catenaryType = switch (cType) {
            case 1 -> CatenaryType.CATENARY;
            case 2 -> CatenaryType.ELECTRIC;
            case 3 -> CatenaryType.RIGID_CATENARY;
            case 4 -> CatenaryType.RIGID_SOFT_CATENARY;
            default -> CatenaryType.NONE;
        };
        return connectCatenary(world, a, b, catenaryType);
    }

    public static boolean connectCatenary(ServerWorld world, BlockPos a, BlockPos b, CatenaryTypeMapping type) {
        if (type == CatenaryTypeMapping.MinecraftBlock) return false;
        var catenaryType = switch (type) {
            case MSDCatenary -> CatenaryType.CATENARY;
            case MSDElectric -> CatenaryType.ELECTRIC;
            case MSDRigidCatenary -> CatenaryType.RIGID_CATENARY;
            case MSDRigidSoftCatenary -> CatenaryType.RIGID_SOFT_CATENARY;
            default -> CatenaryType.NONE;
        };
        return connectCatenary(world, a, b, catenaryType);
    }
}
