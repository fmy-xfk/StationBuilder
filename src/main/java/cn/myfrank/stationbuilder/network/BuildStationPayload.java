package cn.myfrank.stationbuilder.network;

import net.fabricmc.fabric.api.networking.v1.PacketType;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

public class BuildStationPayload {
    public static final Identifier ID = new Identifier("stationbuilder", "build_station");

    private final BlockPos pos;
    private final Direction facing;

    public BuildStationPayload(BlockPos pos, Direction facing) {
        this.pos = pos;
        this.facing = facing;
    }

    public BlockPos getPos() {
        return pos;
    }

    public Direction getFacing() {
        return facing;
    }

    // 写入数据
    public void write(PacketByteBuf buf) {
        buf.writeBlockPos(pos);
        buf.writeInt(facing.getHorizontal());
    }

    // 读取数据
    public static BuildStationPayload read(PacketByteBuf buf) {
        return new BuildStationPayload(
                buf.readBlockPos(),
                Direction.fromHorizontal(buf.readInt())
        );
    }
}