package cn.myfrank.stationbuilder.network;

import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.network.RegistryByteBuf;

public record ClearRailStatePayload() implements CustomPayload {
    public static final Id<ClearRailStatePayload> ID = CustomPayload.id("stationbuilder:clear_rail_state");

    public static final PacketCodec<RegistryByteBuf, ClearRailStatePayload> CODEC =
            PacketCodec.unit(new ClearRailStatePayload());

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}