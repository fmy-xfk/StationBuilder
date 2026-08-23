package cn.myfrank.stationbuilder.elements;

import cn.myfrank.stationbuilder.BuildingTemplateManager;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.BlockRotation;

// 站房：增加自定义旋转及空气剔除设置
public class BuildingElement extends StationElement {
    public String presetName;
    public BlockRotation rotation = BlockRotation.NONE; // 新增：单体元素旋转
    public boolean placeAir = false;                    // 新增：默认不包含空气 (false)

    public BuildingElement(String name) { this.presetName = name; }

    @Override
    public NbtCompound toNbt() {
        NbtCompound nbt = new NbtCompound();
        nbt.putString("type", getType().name()); // BUILDING
        nbt.putString("preset", presetName);
        nbt.putString("rotation", rotation.name()); // 保存旋转角度
        nbt.putBoolean("placeAir", placeAir);       // 保存空气放置选项
        return nbt;
    }

    @Override public Type getType() { return Type.BUILDING; }
    @Override public int getWidth() {
        // 修改：根据当前旋转角度，自动返回正确的物理对齐宽度
        return BuildingTemplateManager.getTemplate(presetName)
                .map(t -> {
                    net.minecraft.util.math.Vec3i size = t.getSize();
                    if (rotation == BlockRotation.CLOCKWISE_90 || rotation == BlockRotation.COUNTERCLOCKWISE_90) {
                        return size.getZ(); // 旋转 90/270 度时，横向投影大小变为 Z 轴长度
                    } else {
                        return size.getX(); // 0/180 度时，横向投影大小为 X 轴长度
                    }
                })
                .orElse(8); // 如果没找到模板，默认 8 宽
    }
    @Override public void write(PacketByteBuf buf) {
        buf.writeEnumConstant(getType());
        buf.writeString(presetName);
        buf.writeEnumConstant(rotation);
        buf.writeBoolean(placeAir);
    }
}