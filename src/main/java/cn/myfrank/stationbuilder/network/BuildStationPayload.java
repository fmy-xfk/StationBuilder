package cn.myfrank.stationbuilder.network;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

public record BuildStationPayload(BlockPos pos, Direction facing) implements CustomPayload {
    public static final Identifier ID = new Identifier("stationbuilder", "build_station");

    @Override
    public Identifier id() { return ID; }

    // 写入数据
    public void write(PacketByteBuf buf) {
        buf.writeBlockPos(pos);
        buf.writeInt(facing.getHorizontal());
    }

    // 读取数据
    public static BuildStationPayload read(PacketByteBuf buf) {
        return new BuildStationPayload(buf.readBlockPos(), Direction.fromHorizontal(buf.readInt()));
    }
}