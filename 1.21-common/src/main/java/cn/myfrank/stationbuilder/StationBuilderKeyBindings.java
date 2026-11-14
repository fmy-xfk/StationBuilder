package cn.myfrank.stationbuilder;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;

public class StationBuilderKeyBindings {
    public static final KeyMapping CLEAR_RAIL_STATE = new KeyMapping(
            "key.stationbuilder.clear_rail_state",
            InputConstants.Type.KEYSYM,
            org.lwjgl.glfw.GLFW.GLFW_KEY_V,
            "category.stationbuilder"
    );
    public static final KeyMapping UNDO = new KeyMapping(
            "key.stationbuilder.undo",
            InputConstants.Type.KEYSYM,
            org.lwjgl.glfw.GLFW.GLFW_KEY_Z,
            "category.stationbuilder"
    );
}
