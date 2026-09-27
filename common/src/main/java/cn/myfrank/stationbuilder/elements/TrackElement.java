package cn.myfrank.stationbuilder.elements;

import cn.myfrank.stationbuilder.create.CreateIntegration;
import cn.myfrank.stationbuilder.utils.CommonUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

// 股道：固定3格宽
public class TrackElement extends StationElement {
    public static ResourceLocation getDefaultTrack() {
        if (CreateIntegration.isAvailable()) {
            return ResourceLocation.fromNamespaceAndPath("create", "track");
        } else if (CommonUtil.isModLoaded("mtr")) {
            return ResourceLocation.fromNamespaceAndPath("mtr", "rail_connector_platform");
        } else {
            return ResourceLocation.fromNamespaceAndPath("minecraft", "rail");
        }
    }
    public ResourceLocation track = getDefaultTrack(); // 默认轨道为 Create 的轨道
    public ResourceLocation ballastBlock = ResourceLocation.fromNamespaceAndPath("minecraft", "andesite"); // 默认路基为安山岩

    @Override public Type getType() { return Type.TRACK; }
    @Override public int getWidth() { return 3; }

    @Override public void write(FriendlyByteBuf buf) {
        buf.writeEnum(getType());
        buf.writeResourceLocation(ballastBlock); // 写入路基方块ID
        buf.writeResourceLocation(track); // 写入轨道方块ID
    }

    @Override
    public CompoundTag toNbt() {
        CompoundTag nbt = new CompoundTag();
        nbt.putString("type", getType().name()); // 存储枚举名：TRACK
        nbt.putString("ballast", ballastBlock.toString()); // 存储 Identifier 字符串
        nbt.putString("track", track.toString()); // 存储 Identifier 字符串
        return nbt;
    }
}
