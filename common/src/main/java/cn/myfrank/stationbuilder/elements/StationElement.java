package cn.myfrank.stationbuilder.elements;

import cn.myfrank.stationbuilder.utils.CommonUtil;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.Identifier;

public abstract class StationElement {
    public abstract NbtCompound toNbt();

    public static StationElement fromNbt(NbtCompound nbt) {
        String typeStr = nbt.getString("type");
        Type type = Type.valueOf(typeStr);

        switch (type) {
            case TRACK:
                TrackElement track = new TrackElement();
                if (nbt.contains("ballast")) {
                    track.ballastBlock = new Identifier(nbt.getString("ballast"));
                }
                if (nbt.contains("track")) {
                    track.track = new Identifier(nbt.getString("track"));
                } else if (nbt.getBoolean("isMtrTrack") && CommonUtil.isMtrLoaded()) {
                    // Backwards compatibility with pre-track-id station presets.
                    track.track = new Identifier("mtr", "rail_connector_platform");
                }
                return track;

            case PLATFORM:
                PlatformElement p = new PlatformElement();
                p.width = nbt.getInt("width");
                p.safetyBlock = new Identifier(nbt.getString("safety"));
                if (nbt.contains("mix")) {
                    NbtList mixList = nbt.getList("mix", 10);
                    for (int i = 0; i < Math.min(mixList.size(), 5); i++) {
                        NbtCompound slotNbt = mixList.getCompound(i);
                        p.mixSlots[i].blockId = new Identifier(slotNbt.getString("id"));
                        p.mixSlots[i].weight = slotNbt.getDouble("weight");
                    }
                }
                p.hasCanopy = nbt.getBoolean("hasCanopy");
                p.canopyHeight = nbt.getInt("canopyHeight");
                p.canopySlabId = new Identifier(nbt.getString("canopySlabId"));
                p.pillarBlockId = new Identifier(nbt.getString("pillarBlockId"));
                p.canopyStyle = PlatformElement.CanopyStyle.valueOf(nbt.getString("canopyStyle"));
                p.pillarStyle = PlatformElement.PillarStyle.valueOf(nbt.getString("pillarStyle"));
                p.pillarSpacing = nbt.getInt("pillarSpacing");
                p.firstPillarOffset = nbt.getInt("firstPillarOffset");
                p.hasLighting = nbt.getBoolean("hasLighting");
                p.lightBlockId = new Identifier(nbt.getString("lightBlockId"));
                p.hasShieldDoors = nbt.getBoolean("hasShieldDoors");
                p.doorStartOffset = nbt.getInt("doorStartOffset");
                p.doorSpacing = nbt.getInt("doorSpacing");
                p.psdEndId = new Identifier(nbt.getString("psdEndId"));
                p.psdGlassId = new Identifier(nbt.getString("psdGlassId"));
                p.psdDoorId = new Identifier(nbt.getString("psdDoorId"));
                p.hasPids = nbt.getBoolean("hasPids");
                p.pidBlockId = new Identifier(nbt.getString("pidBlockId"));
                if (nbt.contains("pidPoleId")) {
                    p.pidPoleId = new Identifier(nbt.getString("pidPoleId"));
                }
                return p;

            case BUILDING:
                String preset = nbt.getString("preset");
                BuildingElement buildingElement = new BuildingElement(preset.isEmpty() ? "matchbox" : preset);
                if (nbt.contains("rotation")) {
                    buildingElement.rotation = BlockRotation.valueOf(nbt.getString("rotation"));
                }
                if (nbt.contains("placeAir")) {
                    buildingElement.placeAir = nbt.getBoolean("placeAir");
                }
                return buildingElement;

            default:
                throw new IllegalArgumentException("Unknown element type_: " + typeStr);
        }
    }

    public enum Type { TRACK, PLATFORM, BUILDING }

    public abstract Type getType();
    public abstract int getWidth();
    public abstract void write(PacketByteBuf buf);

    public static StationElement read(PacketByteBuf buf) {
        Type type = buf.readEnumConstant(Type.class);
        return switch (type) {
            case TRACK -> {
                TrackElement track = new TrackElement();
                track.ballastBlock = buf.readIdentifier();
                track.track = buf.readIdentifier();
                yield track;
            }
            case PLATFORM -> {
                PlatformElement p = new PlatformElement();
                p.width = buf.readInt();
                p.safetyBlock = buf.readIdentifier();
                for (int i = 0; i < PlatformElement.MAX_BLOCK_COUNT; i++) {
                    p.mixSlots[i] = new PlatformElement.MixSlot(buf.readIdentifier(), buf.readDouble());
                }
                p.hasCanopy = buf.readBoolean();
                p.canopyHeight = buf.readInt();
                p.canopySlabId = buf.readIdentifier();
                p.pillarBlockId = buf.readIdentifier();
                p.canopyStyle = buf.readEnumConstant(PlatformElement.CanopyStyle.class);
                p.pillarStyle = buf.readEnumConstant(PlatformElement.PillarStyle.class);
                p.pillarSpacing = buf.readInt();
                p.firstPillarOffset = buf.readInt();
                p.hasLighting = buf.readBoolean();
                p.lightBlockId = buf.readIdentifier();
                p.hasShieldDoors = buf.readBoolean();
                p.doorStartOffset = buf.readInt();
                p.doorSpacing = buf.readInt();
                p.psdEndId = buf.readIdentifier();
                p.psdGlassId = buf.readIdentifier();
                p.psdDoorId = buf.readIdentifier();
                p.hasPids = buf.readBoolean();
                p.pidBlockId = buf.readIdentifier();
                p.pidPoleId = buf.readIdentifier();
                yield p;
            }
            case BUILDING -> {
                BuildingElement buildingElement = new BuildingElement(buf.readString());
                buildingElement.rotation = buf.readEnumConstant(BlockRotation.class);
                buildingElement.placeAir = buf.readBoolean();
                yield buildingElement;
            }
        };
    }
}
