package cn.myfrank.stationbuilder.elements;

import cn.myfrank.stationbuilder.utils.CatenaryTypeMapping;
import cn.myfrank.stationbuilder.utils.CommonUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

// 股道：固定3格宽
public class TrackElement extends StationElement {
    public static ResourceLocation getDefaultTrack() {
        if (CommonUtil.isCreateLoaded()) {
            return ResourceLocation.fromNamespaceAndPath("create", "track");
        } else if (CommonUtil.isMtrLoaded()) {
            return ResourceLocation.fromNamespaceAndPath("mtr", "rail_connector_platform");
        } else {
            return ResourceLocation.fromNamespaceAndPath("minecraft", "rail");
        }
    }

    public ResourceLocation track = getDefaultTrack();
    public ResourceLocation ballastBlock = ResourceLocation.fromNamespaceAndPath("minecraft", "andesite");

    // === 接触网配置 ===
    public boolean hasCatenary = true;
    public int catenaryState = CommonUtil.isMsdLoaded() ? 2 : 1; // 0=关闭, 1=原版, 2=MSD
    public int catenarySpacing = 50;                             // 默认间距 50
    public int catenaryHeight = 6;                              // 默认高度 6
    public int catenaryModeIndex = CatenaryTypeMapping.MSDCatenary.ordinal(); // 默认柔性接触网
    public ResourceLocation catenaryBlock = CommonUtil.isMsdLoaded() ?
            ResourceLocation.fromNamespaceAndPath("msd", "catenary_with_long_top") :
            ResourceLocation.fromNamespaceAndPath("minecraft", "iron_bars");
    public ResourceLocation catenaryPillarBlock = ResourceLocation.fromNamespaceAndPath("minecraft", "stone_brick_wall");

    @Override public Type getType() { return Type.TRACK; }
    @Override public int getWidth() { return 3; }

    @Override public void write(FriendlyByteBuf buf) {
        buf.writeEnum(getType());
        buf.writeResourceLocation(ballastBlock);
        buf.writeResourceLocation(track);
        buf.writeBoolean(hasCatenary);
        buf.writeInt(catenaryState);
        buf.writeInt(catenarySpacing);
        buf.writeInt(catenaryHeight);
        buf.writeInt(catenaryModeIndex);
        buf.writeResourceLocation(catenaryBlock);
        buf.writeResourceLocation(catenaryPillarBlock);
    }

    @Override
    public CompoundTag toNbt() {
        CompoundTag nbt = new CompoundTag();
        nbt.putString("type", getType().name());
        nbt.putString("ballast", ballastBlock.toString());
        nbt.putString("track", track.toString());
        nbt.putBoolean("hasCatenary", hasCatenary);
        nbt.putInt("catenaryState", catenaryState);
        nbt.putInt("catenarySpacing", catenarySpacing);
        nbt.putInt("catenaryHeight", catenaryHeight);
        nbt.putInt("catenaryModeIndex", catenaryModeIndex);
        nbt.putString("catenaryBlock", catenaryBlock.toString());
        nbt.putString("catenaryPillarBlock", catenaryPillarBlock.toString());
        return nbt;
    }
}