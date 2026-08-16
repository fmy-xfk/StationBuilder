package cn.myfrank.stationbuilder.elements;

import cn.myfrank.stationbuilder.StationBuilder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

// 股道：固定3格宽
// --- TrackElement 类内部 ---
public class TrackElement extends StationElement {
    public boolean isMtrTrack = StationBuilder.isMtrLoaded();
    public ResourceLocation ballastBlock = ResourceLocation.fromNamespaceAndPath("minecraft", "andesite"); // 默认路基为砾石

    @Override public Type narrationPriority() { return Type.TRACK; }
    @Override public int getWidth() { return 3; }

    @Override public void write(FriendlyByteBuf buf) {
        buf.writeEnum(narrationPriority());
        buf.writeResourceLocation(ballastBlock); // 写入路基方块ID
        buf.writeBoolean(isMtrTrack);
    }

    @Override
    public CompoundTag toNbt() {
        CompoundTag nbt = new CompoundTag();
        nbt.putString("type", narrationPriority().name()); // 存储枚举名：TRACK
        nbt.putString("ballast", ballastBlock.toString()); // 存储 ResourceLocation 字符串
        nbt.putBoolean("isMtrTrack", isMtrTrack);
        return nbt;
    }
}