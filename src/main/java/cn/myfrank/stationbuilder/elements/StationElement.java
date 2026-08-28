package cn.myfrank.stationbuilder.elements;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

public abstract class StationElement {
    public abstract CompoundTag toNbt();
    // 在 StationElement.java 中添加
    public static StationElement fromNbt(CompoundTag nbt) {
        String typeStr = nbt.getString("type");
        Type type = Type.valueOf(typeStr);

        switch (type) {
            case TRACK:
                TrackElement track = new TrackElement();
                if (nbt.contains("ballast")) {
                    track.ballastBlock = ResourceLocation.parse(nbt.getString("ballast"));
                    track.isMtrTrack = nbt.getBoolean("isMtrTrack");
                }
                return track;

            case PLATFORM:
                PlatformElement p = new PlatformElement();
                p.width = nbt.getInt("width");
                p.safetyBlock = ResourceLocation.parse(nbt.getString("safety"));

                if (nbt.contains("mix")) {
                    ListTag mixList = nbt.getList("mix", 10); // 10 是 COMPOUND 的类型 ID
                    for (int i = 0; i < Math.min(mixList.size(), 5); i++) {
                        CompoundTag slotNbt = mixList.getCompound(i);
                        p.mixSlots[i].blockId = ResourceLocation.parse(slotNbt.getString("id"));
                        p.mixSlots[i].weight = slotNbt.getDouble("weight");
                    }
                }
                p.hasCanopy = nbt.getBoolean("hasCanopy");
                p.canopyHeight = nbt.getInt("canopyHeight");
                p.canopySlabId = ResourceLocation.parse(nbt.getString("canopySlabId"));
                p.pillarBlockId = ResourceLocation.parse(nbt.getString("pillarBlockId"));
                p.canopyStyle = PlatformElement.CanopyStyle.valueOf(nbt.getString("canopyStyle"));
                p.pillarStyle = PlatformElement.PillarStyle.valueOf(nbt.getString("pillarStyle"));
                p.pillarSpacing = nbt.getInt("pillarSpacing");
                p.firstPillarOffset = nbt.getInt("firstPillarOffset");
                p.hasLighting = nbt.getBoolean("hasLighting");
                p.lightBlockId = ResourceLocation.parse(nbt.getString("lightBlockId"));
                p.hasShieldDoors = nbt.getBoolean("hasShieldDoors");
                p.doorStartOffset = nbt.getInt("doorStartOffset");
                p.doorSpacing = nbt.getInt("doorSpacing");
                p.psdEndId = ResourceLocation.parse(nbt.getString("psdEndId"));
                p.psdGlassId = ResourceLocation.parse(nbt.getString("psdGlassId"));
                p.psdDoorId = ResourceLocation.parse(nbt.getString("psdDoorId"));
                p.hasPids = nbt.getBoolean("hasPids");
                p.pidBlockId = ResourceLocation.parse(nbt.getString("pidBlockId"));
                if (nbt.contains("pidPoleId")) {
                    p.pidPoleId = ResourceLocation.parse(nbt.getString("pidPoleId"));
                }
                return p;

            case BUILDING:
                String preset = nbt.getString("preset");
                BuildingElement be = new BuildingElement(preset.isEmpty() ? "matchbox" : preset);
                if (nbt.contains("rotation")) {
                    be.rotation = net.minecraft.world.level.block.Rotation.valueOf(nbt.getString("rotation"));
                }
                if (nbt.contains("placeAir")) {
                    be.placeAir = nbt.getBoolean("placeAir");
                }
                return be;

            default:
                throw new IllegalArgumentException("Unknown element type_: " + typeStr);
        }
    }

    public enum Type { TRACK, PLATFORM, BUILDING }

    public abstract Type narrationPriority();
    public abstract int getWidth();

    // 将元素序列化到网络缓冲区
    public abstract void write(FriendlyByteBuf buf);

    // 从缓冲区读取元素
    public static StationElement read(FriendlyByteBuf buf) {
        Type type = buf.readEnum(Type.class);
        return switch (type) {
            case TRACK -> {
                TrackElement track = new TrackElement();
                track.ballastBlock = buf.readResourceLocation(); // 读取路基方块ID
                track.isMtrTrack = buf.readBoolean();
                yield track;
            }
            case PLATFORM -> {
                PlatformElement p = new PlatformElement();
                p.width = buf.readInt();
                p.safetyBlock = buf.readResourceLocation();
                for(int i = 0; i < PlatformElement.MAX_BLOCK_COUNT; i++) {
                    p.mixSlots[i] = new PlatformElement.MixSlot(
                            buf.readResourceLocation(),
                            buf.readDouble()
                    );
                }
                p.hasCanopy = buf.readBoolean();
                p.canopyHeight = buf.readInt();
                p.canopySlabId = buf.readResourceLocation();
                p.pillarBlockId = buf.readResourceLocation();
                p.canopyStyle = buf.readEnum(PlatformElement.CanopyStyle.class);
                p.pillarStyle = buf.readEnum(PlatformElement.PillarStyle.class);
                p.pillarSpacing = buf.readInt();
                p.firstPillarOffset = buf.readInt();
                p.hasLighting = buf.readBoolean();
                p.lightBlockId = buf.readResourceLocation();
                p.hasShieldDoors = buf.readBoolean();
                p.doorStartOffset = buf.readInt();
                p.doorSpacing = buf.readInt();
                p.psdEndId = buf.readResourceLocation();
                p.psdGlassId = buf.readResourceLocation();
                p.psdDoorId = buf.readResourceLocation();
                p.hasPids = buf.readBoolean();
                p.pidBlockId = buf.readResourceLocation();
                p.pidPoleId = buf.readResourceLocation();
                yield p;
            }
            case BUILDING -> {
                BuildingElement b = new BuildingElement(buf.readUtf());
                b.rotation = buf.readEnum(net.minecraft.world.level.block.Rotation.class);
                b.placeAir = buf.readBoolean();
                yield b;
            }
        };
    }
}