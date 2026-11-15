package cn.myfrank.stationbuilder.elements;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

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
    public ResourceLocation undergroundFloorBlock = id("minecraft", "smooth_stone");
    public ResourceLocation undergroundCeilingBlock = id("minecraft", "smooth_quartz");
    public ResourceLocation undergroundLightBlock = id("minecraft", "sea_lantern");
    public ResourceLocation undergroundWallBlock = id("minecraft", "quartz_block");
    public ResourceLocation undergroundStairBlock = id("minecraft", "smooth_quartz_stairs");
    public ResourceLocation undergroundStairWallBlock = id("minecraft", "quartz_block");
    public ResourceLocation undergroundStairRailBlock = id("minecraft", "stone_brick_wall");

    // Skybridge palette.
    public ResourceLocation skybridgeFloorBlock = id("minecraft", "smooth_stone");
    public ResourceLocation skybridgeCeilingBlock = id("minecraft", "smooth_stone_slab");
    public ResourceLocation skybridgeLightBlock = id("minecraft", "sea_lantern");
    public ResourceLocation skybridgeWallBlock = id("minecraft", "glass_pane");
    public ResourceLocation skybridgeStairBlock = id("minecraft", "stone_brick_stairs");
    public ResourceLocation skybridgeStairWallBlock = id("minecraft", "stone_bricks");

    private static ResourceLocation id(String namespace, String path) {
        return ResourceLocation.fromNamespaceAndPath(namespace, path);
    }

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

    public CompoundTag toNbt() {
        CompoundTag nbt = new CompoundTag();
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

    public void fromNbt(CompoundTag nbt) {
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

    private static ResourceLocation idFromNbt(CompoundTag nbt, String key, ResourceLocation fallback) {
        if (nbt.contains(key)) {
            ResourceLocation parsed = ResourceLocation.tryParse(nbt.getString(key));
            if (parsed != null) {
                return parsed;
            }
        }
        return fallback;
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeInt(mode.ordinal());
        buf.writeInt(width);
        buf.writeInt(height);
        buf.writeResourceLocation(undergroundFloorBlock);
        buf.writeResourceLocation(undergroundCeilingBlock);
        buf.writeResourceLocation(undergroundLightBlock);
        buf.writeResourceLocation(undergroundWallBlock);
        buf.writeResourceLocation(undergroundStairBlock);
        buf.writeResourceLocation(undergroundStairWallBlock);
        buf.writeResourceLocation(undergroundStairRailBlock);
        buf.writeResourceLocation(skybridgeFloorBlock);
        buf.writeResourceLocation(skybridgeCeilingBlock);
        buf.writeResourceLocation(skybridgeLightBlock);
        buf.writeResourceLocation(skybridgeWallBlock);
        buf.writeResourceLocation(skybridgeStairBlock);
        buf.writeResourceLocation(skybridgeStairWallBlock);
    }

    public static PassageConfig read(FriendlyByteBuf buf) {
        PassageConfig config = new PassageConfig();
        int ordinal = buf.readInt();
        config.mode = Mode.values()[Math.max(0, Math.min(ordinal, Mode.values().length - 1))];
        config.width = buf.readInt();
        config.height = buf.readInt();
        config.undergroundFloorBlock = buf.readResourceLocation();
        config.undergroundCeilingBlock = buf.readResourceLocation();
        config.undergroundLightBlock = buf.readResourceLocation();
        config.undergroundWallBlock = buf.readResourceLocation();
        config.undergroundStairBlock = buf.readResourceLocation();
        config.undergroundStairWallBlock = buf.readResourceLocation();
        config.undergroundStairRailBlock = buf.readResourceLocation();
        config.skybridgeFloorBlock = buf.readResourceLocation();
        config.skybridgeCeilingBlock = buf.readResourceLocation();
        config.skybridgeLightBlock = buf.readResourceLocation();
        config.skybridgeWallBlock = buf.readResourceLocation();
        config.skybridgeStairBlock = buf.readResourceLocation();
        config.skybridgeStairWallBlock = buf.readResourceLocation();
        return config;
    }
}
