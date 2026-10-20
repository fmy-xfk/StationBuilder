package cn.myfrank.stationbuilder.elements;

import cn.myfrank.stationbuilder.utils.CommonUtil;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;

/** A 3-block-wide track element. */
public class TrackElement extends StationElement {
    public static Identifier getDefaultTrack() {
        if (CommonUtil.isMtrLoaded()) {
            return new Identifier("mtr", "rail_connector_platform");
        }
        return new Identifier("minecraft", "rail");
    }

    /** Track block/item identifier. MTR tracks use an MTR rail connector item id. */
    public Identifier track = getDefaultTrack();
    public Identifier ballastBlock = new Identifier("minecraft", "andesite");

    @Override public Type getType() { return Type.TRACK; }
    @Override public int getWidth() { return 3; }

    @Override public void write(PacketByteBuf buf) {
        buf.writeEnumConstant(getType());
        buf.writeIdentifier(ballastBlock);
        buf.writeIdentifier(track);
    }

    @Override public NbtCompound toNbt() {
        NbtCompound nbt = new NbtCompound();
        nbt.putString("type", getType().name());
        nbt.putString("ballast", ballastBlock.toString());
        nbt.putString("track", track.toString());
        return nbt;
    }
}
