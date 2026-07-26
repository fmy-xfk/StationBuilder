package cn.myfrank.stationbuilder.network;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.network.RegistryByteBuf;

public record SaveDataRailPayload(NbtCompound nbt) implements CustomPayload {
    public static final Id<SaveDataRailPayload> ID = CustomPayload.id("stationbuilder:save_data_rail");

    public static final PacketCodec<RegistryByteBuf, SaveDataRailPayload> CODEC =
            PacketCodec.ofStatic(
                    (buf, payload) -> buf.writeNbt(payload.nbt()),
                    buf -> new SaveDataRailPayload(buf.readNbt())
            );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}