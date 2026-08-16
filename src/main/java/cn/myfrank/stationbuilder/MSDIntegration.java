package cn.myfrank.stationbuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import org.mtr.core.data.Position;
import org.mtr.core.tool.Angle;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectObjectImmutablePair;
import org.mtr.mapping.holder.BlockState;
import org.mtr.mapping.mapper.DirectionHelper;
import org.mtr.mod.Init;
import top.mcmtr.core.data.Catenary;
import top.mcmtr.core.data.CatenaryType;
import top.mcmtr.core.data.OffsetPosition;
import top.mcmtr.core.data.RigidCatenary;
import top.mcmtr.mod.blocks.BlockCatenaryWithModel;
import top.mcmtr.mod.blocks.BlockNodeBase;
import top.mcmtr.mod.blocks.BlockRigidCatenaryNode;
import top.mcmtr.mod.packet.MSDPacketDeleteData;
import top.mcmtr.mod.packet.MSDPacketUpdateData;

import static top.mcmtr.mod.items.ItemRigidCatenaryConnector.getAngles;

public class MSDIntegration {
    public static boolean isCatenaryNode(ServerLevel world, BlockPos pos) {
        return world.getBlockState(pos).getBlock() instanceof BlockNodeBase;
    }

    public static void clearCatenary(ServerLevel world, BlockPos pos) {
        if (MSDIntegration.isCatenaryNode(world, pos)) {
            MSDPacketDeleteData.sendDirectlyToServerCatenaryNodePosition(
                    new org.mtr.mapping.holder.ServerWorld(world),
                    Init.blockPosToPosition(new org.mtr.mapping.holder.BlockPos(pos))
            );
        }
    }

    public static boolean placeCatenaryNode(ServerLevel world, BlockPos pos, Direction direction, double dirAngle, ResourceLocation blockId) {
        var b = BuiltInRegistries.BLOCK.get(blockId);
        var state = new org.mtr.mapping.holder.BlockState(b.defaultBlockState()).data;
        if (b instanceof BlockRigidCatenaryNode) {
            var quadrant = Angle.getQuadrant((float) dirAngle, true);
            state = state.setValue(BlockRigidCatenaryNode.FACING.data, quadrant % 8 >= 4)
                    .setValue(BlockRigidCatenaryNode.IS_45.data, quadrant % 4 >= 2)
                    .setValue(BlockRigidCatenaryNode.IS_22_5.data, quadrant % 2 == 1);
        } else if (b instanceof BlockCatenaryWithModel) {
            state = state.setValue(DirectionHelper.FACING.data, direction);
        } else {
            return false;
        }
        world.setBlock(pos, state, 3);
        return true;
    }

    private static boolean connectCatenary(ServerLevel world, BlockPos a, BlockPos b, CatenaryType c) {
        BlockNodeBase.BlockNodeBaseEntity startBlockEntity = (BlockNodeBase.BlockNodeBaseEntity) world.getBlockEntity(a);
        BlockNodeBase.BlockNodeBaseEntity endBlockEntity = (BlockNodeBase.BlockNodeBaseEntity) world.getBlockEntity(b);
        if (startBlockEntity == null || endBlockEntity == null) return false;

        var a2 = new org.mtr.mapping.holder.BlockPos(a);
        var b2 = new org.mtr.mapping.holder.BlockPos(b);
        OffsetPosition offsetPositionStart = startBlockEntity.getOffsetPosition();
        OffsetPosition offsetPositionEnd = endBlockEntity.getOffsetPosition();
        Position positionStart = Init.blockPosToPosition(a2);
        Position positionEnd = Init.blockPosToPosition(b2);
        var stateStart = world.getBlockState(a);
        var stateEnd = world.getBlockState(b);

        if (c == CatenaryType.RIGID_CATENARY) {
            if (RigidCatenary.verifyPosition(positionStart, positionEnd)) {
                ObjectObjectImmutablePair<Angle, Angle> angles = getAngles(
                        a2, BlockRigidCatenaryNode.getAngle(new BlockState(stateStart)),
                        b2, BlockRigidCatenaryNode.getAngle(new BlockState(stateEnd))
                );
                RigidCatenary rigidCatenary = new RigidCatenary(
                        positionStart, angles.left(), positionEnd, angles.right(), RigidCatenary.Shape.QUADRATIC, 0.0F
                );
                world.setBlock(a, stateStart.setValue(BlockNodeBase.IS_CONNECTED.data, true), 3);
                world.setBlock(b, stateEnd.setValue(BlockNodeBase.IS_CONNECTED.data, true), 3);
                MSDPacketUpdateData.sendDirectlyToServerRigidCatenary(new org.mtr.mapping.holder.ServerWorld(world), rigidCatenary);
            }
        } else {
            if (Catenary.verifyPosition(positionStart, positionEnd, offsetPositionStart, offsetPositionEnd)) {
                System.out.println(c);
                Catenary catenary = new Catenary(positionStart, positionEnd, offsetPositionStart, offsetPositionEnd, c);
                world.setBlock(a, stateStart.setValue(BlockNodeBase.IS_CONNECTED.data, true), 3);
                world.setBlock(b, stateEnd.setValue(BlockNodeBase.IS_CONNECTED.data, true), 3);
                MSDPacketUpdateData.sendDirectlyToServerCatenary(new org.mtr.mapping.holder.ServerWorld(world), catenary);
            } else {
                return false;
            }
        }
        return true;
    }

    public static boolean connectCatenary(ServerLevel world, BlockPos a, BlockPos b, int cType) {
        var catenaryType = switch (cType) {
            case 1 -> CatenaryType.CATENARY;
            case 2 -> CatenaryType.ELECTRIC;
            case 3 -> CatenaryType.RIGID_CATENARY;
            case 4 -> CatenaryType.RIGID_SOFT_CATENARY;
            default -> CatenaryType.NONE;
        };
        return connectCatenary(world, a, b, catenaryType);
    }

    public static boolean connectCatenary(ServerLevel world, BlockPos a, BlockPos b, CatenaryTypeMapping type) {
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