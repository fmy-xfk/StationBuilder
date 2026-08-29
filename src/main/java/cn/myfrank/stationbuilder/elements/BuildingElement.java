package cn.myfrank.stationbuilder.elements;

import cn.myfrank.stationbuilder.BuildingTemplateManager;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.level.block.Rotation;

// 站房：使用预设名称
public class BuildingElement extends StationElement {
    public String presetName;
    public Rotation rotation = Rotation.NONE;
    public boolean placeAir = false;
    public BuildingElement(String name) { this.presetName = name; }

    @Override
    public CompoundTag toNbt() {
        CompoundTag nbt = new CompoundTag();
        nbt.putString("type", narrationPriority().name()); // BUILDING
        nbt.putString("preset", presetName);
        nbt.putString("rotation", rotation.name());
        nbt.putBoolean("placeAir", placeAir);
        return nbt;
    }

    @Override public Type narrationPriority() { return Type.BUILDING; }
    @Override public int getWidth() {
        return BuildingTemplateManager.getTemplate(presetName)
                .map(t -> {
                    net.minecraft.core.Vec3i size = t.getSize();
                    if (rotation == Rotation.CLOCKWISE_90 || rotation == Rotation.COUNTERCLOCKWISE_90) {
                        return size.getZ();
                    } else {
                        return size.getX();
                    }
                })
                .orElse(8); // 如果没找到模板，默认8宽
    }
    @Override public void write(FriendlyByteBuf buf) {
        buf.writeEnum(narrationPriority());
        buf.writeUtf(presetName);
        buf.writeEnum(rotation);
        buf.writeBoolean(placeAir);
    }
}
