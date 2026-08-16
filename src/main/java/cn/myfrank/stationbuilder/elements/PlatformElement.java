package cn.myfrank.stationbuilder.elements;

import cn.myfrank.stationbuilder.StationBuilder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

public class PlatformElement extends StationElement {
    public static final int MAX_BLOCK_COUNT = 5;
    public int width = 9;
    public ResourceLocation safetyBlock = StationBuilder.isMtrLoaded() ?
            ResourceLocation.fromNamespaceAndPath("mtr", "platform") :
            ResourceLocation.fromNamespaceAndPath("minecraft", "yellow_concrete");

    public static class MixSlot {
        public ResourceLocation blockId;
        public double weight;
        public MixSlot() {
            blockId = ResourceLocation.fromNamespaceAndPath("minecraft", "smooth_stone");
            weight = 1.0;
        }
        public MixSlot(ResourceLocation blockId, double weight) {
            this.blockId = blockId;
            this.weight = weight;
        }
    }

    public MixSlot[] mixSlots = new MixSlot[5];

    // 雨棚开关与基础属性
    public boolean hasCanopy = true;
    public int canopyHeight = 4; // 距离站台地面的高度
    public ResourceLocation canopySlabId = ResourceLocation.fromNamespaceAndPath("minecraft", "smooth_stone_slab");
    public ResourceLocation pillarBlockId = ResourceLocation.fromNamespaceAndPath("minecraft", "stone_brick_wall");

    public enum CanopyStyle { FLAT, INVERTED_V, V_SHAPE, SLANT_RIGHT, SLANT_LEFT, PILLAR_ONLY }
    public CanopyStyle canopyStyle = CanopyStyle.V_SHAPE;

    public enum PillarStyle { SINGLE, DOUBLE, NONE }
    public PillarStyle pillarStyle = PillarStyle.SINGLE;

    public int pillarSpacing = 9;     // 支柱间距
    public int firstPillarOffset = 0; // 首个支柱偏移量

    public boolean hasLighting = true;
    public ResourceLocation lightBlockId = ResourceLocation.fromNamespaceAndPath("minecraft", "sea_lantern");

    public boolean hasShieldDoors = true;
    public int doorStartOffset = 2;
    public int doorSpacing = 3;
    public ResourceLocation psdEndId = ResourceLocation.fromNamespaceAndPath("mtr", "apg_glass_end");
    public ResourceLocation psdGlassId = ResourceLocation.fromNamespaceAndPath("mtr", "apg_glass"); // 示例
    public ResourceLocation psdDoorId = ResourceLocation.fromNamespaceAndPath("mtr", "apg_door");

    public boolean hasPids = true;
    public ResourceLocation pidBlockId = ResourceLocation.fromNamespaceAndPath("mtr", "pids_1");
    public ResourceLocation pidPoleId = ResourceLocation.fromNamespaceAndPath("mtr", "pids_pole");

    public PlatformElement() {
        for (int i = 0; i < MAX_BLOCK_COUNT; i++) mixSlots[i] = new MixSlot();
        // 默认只有第一个槽位有方块，其余设为空气或默认值
        for (int i = 1; i < MAX_BLOCK_COUNT; i++) {
            mixSlots[i].blockId = ResourceLocation.fromNamespaceAndPath("minecraft", "air");
            mixSlots[i].weight = 0.0;
        }
    }

    @Override
    public CompoundTag toNbt() {
        CompoundTag nbt = new CompoundTag();
        nbt.putString("type", narrationPriority().name()); // PLATFORM
        nbt.putInt("width", width);
        nbt.putString("safety", safetyBlock.toString());

        // 创建一个列表来存储混合槽位
        ListTag mixList = new ListTag();
        for (MixSlot slot : mixSlots) {
            CompoundTag slotNbt = new CompoundTag();
            slotNbt.putString("id", slot.blockId.toString());
            slotNbt.putDouble("weight", slot.weight);
            mixList.add(slotNbt);
        }
        nbt.put("mix", mixList);
        nbt.putBoolean("hasCanopy", hasCanopy);
        nbt.putInt("canopyHeight", canopyHeight);
        nbt.putString("canopySlabId", canopySlabId.toString());
        nbt.putString("pillarBlockId", pillarBlockId.toString());
        nbt.putString("canopyStyle", canopyStyle.name());
        nbt.putString("pillarStyle", pillarStyle.name());
        nbt.putInt("pillarSpacing", pillarSpacing);
        nbt.putInt("firstPillarOffset", firstPillarOffset);
        nbt.putBoolean("hasLighting", hasLighting);
        nbt.putString("lightBlockId", lightBlockId.toString());
        nbt.putBoolean("hasShieldDoors", hasShieldDoors);
        nbt.putInt("doorStartOffset", doorStartOffset);
        nbt.putInt("doorSpacing", doorSpacing);
        nbt.putString("psdEndId", psdEndId.toString());
        nbt.putString("psdGlassId", psdGlassId.toString());
        nbt.putString("psdDoorId", psdDoorId.toString());
        nbt.putBoolean("hasPids", hasPids);
        nbt.putString("pidBlockId", pidBlockId.toString());
        nbt.putString("pidPoleId", pidPoleId.toString());
        return nbt;
    }

    @Override public Type narrationPriority() { return Type.PLATFORM; }
    @Override public int getWidth() { return width; }
    @Override public void write(FriendlyByteBuf buf) {
        buf.writeEnum(narrationPriority());
        buf.writeInt(width);
        buf.writeResourceLocation(safetyBlock);
        for (MixSlot slot : mixSlots) {
            buf.writeResourceLocation(slot.blockId);
            buf.writeDouble(slot.weight);
        }
        buf.writeBoolean(hasCanopy);
        buf.writeInt(canopyHeight);
        buf.writeResourceLocation(canopySlabId);
        buf.writeResourceLocation(pillarBlockId);
        buf.writeEnum(canopyStyle);
        buf.writeEnum(pillarStyle);
        buf.writeInt(pillarSpacing);
        buf.writeInt(firstPillarOffset);
        buf.writeBoolean(hasLighting);
        buf.writeResourceLocation(lightBlockId);
        buf.writeBoolean(hasShieldDoors);
        buf.writeInt(doorStartOffset);
        buf.writeInt(doorSpacing);
        buf.writeResourceLocation(psdEndId);
        buf.writeResourceLocation(psdGlassId);
        buf.writeResourceLocation(psdDoorId);
        buf.writeBoolean(hasPids);
        buf.writeResourceLocation(pidBlockId);
        buf.writeResourceLocation(pidPoleId);
    }
}