package cn.myfrank.stationbuilder.elements;

import cn.myfrank.stationbuilder.StationBuilder;
import cn.myfrank.stationbuilder.create.CreateIntegration;
import cn.myfrank.stationbuilder.utils.CommonUtil;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;

// 股道：固定3格宽
// --- TrackElement 类内部 ---
public class TrackElement extends StationElement {
    public static Identifier getDefaultTrack() {
        if (CommonUtil.isCreateLoaded()) {
            return Identifier.of("create", "track");
        } else if (CommonUtil.isMTRLoaded()) {
            return Identifier.of("mtr", "rail_connector_platform");
        } else {
            return Identifier.of("minecraft", "rail");
        }
    }

    public Identifier track = getDefaultTrack();
    public Identifier ballastBlock = Identifier.of("minecraft", "andesite"); // 默认路基为砾石

    @Override public Type getType() { return Type.TRACK; }
    @Override public int getWidth() { return 3; }

    @Override public void write(PacketByteBuf buf) {
        buf.writeEnumConstant(getType());
        buf.writeIdentifier(ballastBlock); // 写入路基方块ID
        buf.writeIdentifier(track); // 写入轨道方块ID
    }

    @Override
    public NbtCompound toNbt() {
        NbtCompound nbt = new NbtCompound();
        nbt.putString("type", getType().name()); // 存储枚举名：TRACK
        nbt.putString("ballast", ballastBlock.toString()); // 存储 Identifier 字符串
        nbt.putString("track", track.toString()); // 存储 Identifier 字符串
        return nbt;
    }
}