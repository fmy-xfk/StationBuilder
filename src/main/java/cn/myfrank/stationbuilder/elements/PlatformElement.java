package cn.myfrank.stationbuilder.elements;

import cn.myfrank.stationbuilder.StationBuilder;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;

public class PlatformElement extends StationElement {
    public static final int MAX_BLOCK_COUNT = 5;
    public int width = 9;
    public Identifier safetyBlock = StationBuilder.isMtrLoaded() ?
            new Identifier("mtr", "platform") :
            new Identifier("minecraft", "yellow_concrete");

    public static class MixSlot {
        public Identifier blockId;
        public double weight;
        public MixSlot() {
            blockId = new Identifier("minecraft", "smooth_stone");
            weight = 1.0;
        }
        public MixSlot(Identifier blockId, double weight) {
            this.blockId = blockId;
            this.weight = weight;
        }
    }

    public MixSlot[] mixSlots = new MixSlot[5];

    // 雨棚开关与基础属性
    public boolean hasCanopy = true;
    public int canopyHeight = 4; // 距离站台地面的高度
    public Identifier canopySlabId = new Identifier("minecraft", "smooth_stone_slab");
    public Identifier pillarBlockId = new Identifier("minecraft", "stone_brick_wall");

    public enum CanopyStyle { FLAT, INVERTED_V, V_SHAPE, SLANT_RIGHT, SLANT_LEFT, PILLAR_ONLY }
    public CanopyStyle canopyStyle = CanopyStyle.V_SHAPE;

    public enum PillarStyle { SINGLE, DOUBLE, NONE }
    public PillarStyle pillarStyle = PillarStyle.SINGLE;

    public int pillarSpacing = 9;     // 支柱间距
    public int firstPillarOffset = 0; // 首个支柱偏移量

    public boolean hasLighting = true;
    public Identifier lightBlockId = new Identifier("minecraft", "sea_lantern");

    public boolean hasShieldDoors = true;
    public int doorStartOffset = 2;
    public int doorSpacing = 3;
    public Identifier psdEndId = new Identifier("mtr", "apg_glass_end");
    public Identifier psdGlassId = new Identifier("mtr", "apg_glass"); // 示例
    public Identifier psdDoorId = new Identifier("mtr", "apg_door");

    public boolean hasPids = true;
    public Identifier pidBlockId = new Identifier("mtr", "pids_1");

    public PlatformElement() {
        for (int i = 0; i < MAX_BLOCK_COUNT; i++) mixSlots[i] = new MixSlot();
        // 默认只有第一个槽位有方块，其余设为空气或默认值
        for (int i = 1; i < MAX_BLOCK_COUNT; i++) {
            mixSlots[i].blockId = new Identifier("minecraft", "air");
            mixSlots[i].weight = 0.0;
        }
    }

    @Override
    public NbtCompound toNbt() {
        NbtCompound nbt = new NbtCompound();
        nbt.putString("type", getType().name()); // PLATFORM
        nbt.putInt("width", width);
        nbt.putString("safety", safetyBlock.toString());

        // 创建一个列表来存储混合槽位
        NbtList mixList = new NbtList();
        for (MixSlot slot : mixSlots) {
            NbtCompound slotNbt = new NbtCompound();
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
        return nbt;
    }

    @Override public Type getType() { return Type.PLATFORM; }
    @Override public int getWidth() { return width; }
    @Override public void write(PacketByteBuf buf) {
        buf.writeEnumConstant(getType());
        buf.writeInt(width);
        buf.writeIdentifier(safetyBlock);
        for (MixSlot slot : mixSlots) {
            buf.writeIdentifier(slot.blockId);
            buf.writeDouble(slot.weight);
        }
        buf.writeBoolean(hasCanopy);
        buf.writeInt(canopyHeight);
        buf.writeIdentifier(canopySlabId);
        buf.writeIdentifier(pillarBlockId);
        buf.writeEnumConstant(canopyStyle);
        buf.writeEnumConstant(pillarStyle);
        buf.writeInt(pillarSpacing);
        buf.writeInt(firstPillarOffset);
        buf.writeBoolean(hasLighting);
        buf.writeIdentifier(lightBlockId);
        buf.writeBoolean(hasShieldDoors);
        buf.writeInt(doorStartOffset);
        buf.writeInt(doorSpacing);
        buf.writeIdentifier(psdEndId);
        buf.writeIdentifier(psdGlassId);
        buf.writeIdentifier(psdDoorId);
        buf.writeBoolean(hasPids);
        buf.writeIdentifier(pidBlockId);
    }
}