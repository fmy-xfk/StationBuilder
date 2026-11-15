package cn.myfrank.stationbuilder.elements;

import cn.myfrank.stationbuilder.utils.CommonUtil;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;

/**
 * Station-level configuration for the corridor that connects all platforms.
 *
 * <p>The corridor is built across the station (perpendicular to the tracks) from the
 * left edge of the leftmost platform to the right edge of the rightmost platform.
 * It can either be an underground tunnel or an overpass above the catenary.</p>
 */
public class PassageConfig {
    public enum Mode {
        NONE,
        UNDERGROUND,
        SKYBRIDGE;

        public String getName() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    public static final int DEFAULT_WIDTH = 5;
    public static final int DEFAULT_HEIGHT = 4;
    public static final int MAX_HEIGHT = 16;
    public static final int MAX_WIDTH = 128;

    public Mode mode = Mode.NONE;
    /** Interior width of the corridor (along the station length direction). */
    public int width = DEFAULT_WIDTH;
    /** Interior clear height of the corridor. */
    public int height = DEFAULT_HEIGHT;

    // Underground palette.
    public Identifier undergroundFloorBlock = CommonUtil.identifierOf("minecraft", "smooth_stone");
    public Identifier undergroundCeilingBlock = CommonUtil.identifierOf("minecraft", "smooth_quartz");
    public Identifier undergroundLightBlock = CommonUtil.identifierOf("minecraft", "sea_lantern");
    public Identifier undergroundWallBlock = CommonUtil.identifierOf("minecraft", "quartz_block");
    public Identifier undergroundStairBlock = CommonUtil.identifierOf("minecraft", "smooth_quartz_stairs");
    public Identifier undergroundStairWallBlock = CommonUtil.identifierOf("minecraft", "quartz_block");
    public Identifier undergroundStairRailBlock = CommonUtil.identifierOf("minecraft", "stone_brick_wall");

    // Skybridge palette.
    public Identifier skybridgeFloorBlock = CommonUtil.identifierOf("minecraft", "smooth_stone");
    public Identifier skybridgeCeilingBlock = CommonUtil.identifierOf("minecraft", "smooth_stone_slab");
    public Identifier skybridgeLightBlock = CommonUtil.identifierOf("minecraft", "sea_lantern");
    public Identifier skybridgeWallBlock = CommonUtil.identifierOf("minecraft", "glass_pane");
    public Identifier skybridgeStairBlock = CommonUtil.identifierOf("minecraft", "stone_brick_stairs");
    public Identifier skybridgeStairWallBlock = CommonUtil.identifierOf("minecraft", "stone_bricks");

    public PassageConfig() {
    }

    public PassageConfig(Mode mode, int width, int height) {
        this.mode = mode;
        this.width = width;
        this.height = height;
    }

    public boolean isEnabled() {
        return mode != Mode.NONE;
    }

    /** Cycles through the three modes, used by the mode button in the GUI. */
    public void nextMode() {
        Mode[] modes = Mode.values();
        mode = modes[(mode.ordinal() + 1) % modes.length];
    }

    public void sanitize(int stationLength) {
        int maxWidth = Math.max(1, Math.min(MAX_WIDTH, stationLength));
        width = Math.max(1, Math.min(width, maxWidth));
        height = Math.max(1, Math.min(height, MAX_HEIGHT));
    }

    public void copyFrom(PassageConfig other) {
        if (other == null) {
            return;
        }
        this.mode = other.mode;
        this.width = other.width;
        this.height = other.height;
        this.undergroundFloorBlock = other.undergroundFloorBlock;
        this.undergroundCeilingBlock = other.undergroundCeilingBlock;
        this.undergroundLightBlock = other.undergroundLightBlock;
        this.undergroundWallBlock = other.undergroundWallBlock;
        this.undergroundStairBlock = other.undergroundStairBlock;
        this.undergroundStairWallBlock = other.undergroundStairWallBlock;
        this.undergroundStairRailBlock = other.undergroundStairRailBlock;
        this.skybridgeFloorBlock = other.skybridgeFloorBlock;
        this.skybridgeCeilingBlock = other.skybridgeCeilingBlock;
        this.skybridgeLightBlock = other.skybridgeLightBlock;
        this.skybridgeWallBlock = other.skybridgeWallBlock;
        this.skybridgeStairBlock = other.skybridgeStairBlock;
        this.skybridgeStairWallBlock = other.skybridgeStairWallBlock;
    }

    public NbtCompound toNbt() {
        NbtCompound nbt = new NbtCompound();
        nbt.putString("mode", mode.name());
        nbt.putInt("width", width);
        nbt.putInt("height", height);
        nbt.putString("ugFloor", undergroundFloorBlock.toString());
        nbt.putString("ugCeiling", undergroundCeilingBlock.toString());
        nbt.putString("ugLight", undergroundLightBlock.toString());
        nbt.putString("ugWall", undergroundWallBlock.toString());
        nbt.putString("ugStair", undergroundStairBlock.toString());
        nbt.putString("ugStairWall", undergroundStairWallBlock.toString());
        nbt.putString("ugStairRail", undergroundStairRailBlock.toString());
        nbt.putString("sbFloor", skybridgeFloorBlock.toString());
        nbt.putString("sbCeiling", skybridgeCeilingBlock.toString());
        nbt.putString("sbLight", skybridgeLightBlock.toString());
        nbt.putString("sbWall", skybridgeWallBlock.toString());
        nbt.putString("sbStair", skybridgeStairBlock.toString());
        nbt.putString("sbStairWall", skybridgeStairWallBlock.toString());
        return nbt;
    }

    public void fromNbt(NbtCompound nbt) {
        if (nbt == null) {
            return;
        }
        if (nbt.contains("mode")) {
            try {
                mode = Mode.valueOf(nbt.getString("mode"));
            } catch (IllegalArgumentException ignored) {
                mode = Mode.NONE;
            }
        }
        if (nbt.contains("width")) {
            width = nbt.getInt("width");
        }
        if (nbt.contains("height")) {
            height = nbt.getInt("height");
        }
        undergroundFloorBlock = idFromNbt(nbt, "ugFloor", undergroundFloorBlock);
        undergroundCeilingBlock = idFromNbt(nbt, "ugCeiling", undergroundCeilingBlock);
        undergroundLightBlock = idFromNbt(nbt, "ugLight", undergroundLightBlock);
        undergroundWallBlock = idFromNbt(nbt, "ugWall", undergroundWallBlock);
        undergroundStairBlock = idFromNbt(nbt, "ugStair", undergroundStairBlock);
        undergroundStairWallBlock = idFromNbt(nbt, "ugStairWall", undergroundStairWallBlock);
        undergroundStairRailBlock = idFromNbt(nbt, "ugStairRail", undergroundStairRailBlock);
        skybridgeFloorBlock = idFromNbt(nbt, "sbFloor", skybridgeFloorBlock);
        skybridgeCeilingBlock = idFromNbt(nbt, "sbCeiling", skybridgeCeilingBlock);
        skybridgeLightBlock = idFromNbt(nbt, "sbLight", skybridgeLightBlock);
        skybridgeWallBlock = idFromNbt(nbt, "sbWall", skybridgeWallBlock);
        skybridgeStairBlock = idFromNbt(nbt, "sbStair", skybridgeStairBlock);
        skybridgeStairWallBlock = idFromNbt(nbt, "sbStairWall", skybridgeStairWallBlock);
        sanitize(MAX_WIDTH);
    }

    private static Identifier idFromNbt(NbtCompound nbt, String key, Identifier fallback) {
        if (nbt.contains(key)) {
            Identifier parsed = Identifier.tryParse(nbt.getString(key));
            if (parsed != null) {
                return parsed;
            }
        }
        return fallback;
    }

    public void write(PacketByteBuf buf) {
        buf.writeEnumConstant(mode);
        buf.writeInt(width);
        buf.writeInt(height);
        buf.writeIdentifier(undergroundFloorBlock);
        buf.writeIdentifier(undergroundCeilingBlock);
        buf.writeIdentifier(undergroundLightBlock);
        buf.writeIdentifier(undergroundWallBlock);
        buf.writeIdentifier(undergroundStairBlock);
        buf.writeIdentifier(undergroundStairWallBlock);
        buf.writeIdentifier(undergroundStairRailBlock);
        buf.writeIdentifier(skybridgeFloorBlock);
        buf.writeIdentifier(skybridgeCeilingBlock);
        buf.writeIdentifier(skybridgeLightBlock);
        buf.writeIdentifier(skybridgeWallBlock);
        buf.writeIdentifier(skybridgeStairBlock);
        buf.writeIdentifier(skybridgeStairWallBlock);
    }

    public static PassageConfig read(PacketByteBuf buf) {
        PassageConfig config = new PassageConfig();
        config.mode = buf.readEnumConstant(Mode.class);
        config.width = buf.readInt();
        config.height = buf.readInt();
        config.undergroundFloorBlock = buf.readIdentifier();
        config.undergroundCeilingBlock = buf.readIdentifier();
        config.undergroundLightBlock = buf.readIdentifier();
        config.undergroundWallBlock = buf.readIdentifier();
        config.undergroundStairBlock = buf.readIdentifier();
        config.undergroundStairWallBlock = buf.readIdentifier();
        config.undergroundStairRailBlock = buf.readIdentifier();
        config.skybridgeFloorBlock = buf.readIdentifier();
        config.skybridgeCeilingBlock = buf.readIdentifier();
        config.skybridgeLightBlock = buf.readIdentifier();
        config.skybridgeWallBlock = buf.readIdentifier();
        config.skybridgeStairBlock = buf.readIdentifier();
        config.skybridgeStairWallBlock = buf.readIdentifier();
        return config;
    }
}
