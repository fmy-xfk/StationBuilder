package cn.myfrank.stationbuilder.network;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.math.BlockPos;

public record SyncOpenRailPayload(BlockPos pos, int facing, NbtCompound nbt) implements CustomPayload {
    public static final Id<SyncOpenRailPayload> ID = CustomPayload.id("stationbuilder:sync_open_rail");

    public static final PacketCodec<RegistryByteBuf, SyncOpenRailPayload> CODEC =
            PacketCodec.ofStatic(
                    (buf, payload) -> {
                        buf.writeBlockPos(payload.pos());
                        buf.writeInt(payload.facing());
                        buf.writeNbt(payload.nbt());
                    },
                    buf -> new SyncOpenRailPayload(buf.readBlockPos(), buf.readInt(), buf.readNbt())
            );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}