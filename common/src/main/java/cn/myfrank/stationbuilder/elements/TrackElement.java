package cn.myfrank.stationbuilder.elements;

import cn.myfrank.stationbuilder.utils.CatenaryTypeMapping;
import cn.myfrank.stationbuilder.utils.CommonUtil;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;

public class TrackElement extends StationElement {
    public static Identifier getDefaultTrack() {
        if (CommonUtil.isCreateLoaded()) {
            return CommonUtil.identifierOf("create", "track");
        } else if (CommonUtil.isMtrLoaded()) {
            return CommonUtil.identifierOf("mtr", "rail_connector_platform");
        } else {
            return CommonUtil.identifierOf("minecraft", "rail");
        }
    }

    public Identifier track = getDefaultTrack();
    public Identifier ballastBlock = CommonUtil.identifierOf("minecraft", "andesite");

    // === 接触网配置 ===
    public boolean hasCatenary = true;
    public int catenaryState = CommonUtil.isMsdLoaded() ? 2 : 1; // 0=关闭, 1=原版, 2=MSD
    public int catenarySpacing = 50;                             // 默认间距 50
    public int catenaryHeight = 6;                              // 默认高度 6
    public int catenaryModeIndex = CatenaryTypeMapping.MSDCatenary.ordinal(); // 默认柔性接触网
    public Identifier catenaryBlock = CommonUtil.isMsdLoaded() ?
            CommonUtil.identifierOf("msd", "catenary_with_long") :
            CommonUtil.identifierOf("minecraft", "iron_bars");
    public Identifier catenaryPillarBlock = CommonUtil.identifierOf("minecraft", "stone_brick_wall");

    @Override public Type getType() { return Type.TRACK; }
    @Override public int getWidth() { return 3; }

    @Override public void write(PacketByteBuf buf) {
        buf.writeEnumConstant(getType());
        buf.writeIdentifier(ballastBlock);
        buf.writeIdentifier(track);
        buf.writeBoolean(hasCatenary);
        buf.writeInt(catenaryState);
        buf.writeInt(catenarySpacing);
        buf.writeInt(catenaryHeight);
        buf.writeInt(catenaryModeIndex);
        buf.writeIdentifier(catenaryBlock);
        buf.writeIdentifier(catenaryPillarBlock);
    }

    @Override
    public NbtCompound toNbt() {
        NbtCompound nbt = new NbtCompound();
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