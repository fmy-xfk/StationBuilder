package cn.myfrank.stationbuilder.elements;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.PacketByteBuf;

// 站房：使用预设名称
public class BuildingElement extends StationElement {
    public String presetName;
    public BuildingElement(String name) { this.presetName = name; }

    @Override
    public NbtCompound toNbt() {
        NbtCompound nbt = new NbtCompound();
        nbt.putString("type", getType().name()); // BUILDING
        nbt.putString("preset", presetName);
        return nbt;
    }

    @Override public Type getType() { return Type.BUILDING; }
    @Override public int getWidth() { return 8; } // 简易站房设为8宽
    @Override public void write(PacketByteBuf buf) {
        buf.writeEnumConstant(getType());
        buf.writeString(presetName);
    }
}