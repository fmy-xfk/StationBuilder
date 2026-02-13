package cn.myfrank.stationbuilder;

import org.lwjgl.glfw.GLFW;

import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;

public class StationBuilderKeyBindings {

    public static KeyBinding CLEAR_RAIL_STATE;

    public static void register() {
        CLEAR_RAIL_STATE = KeyBindingHelper.registerKeyBinding(
                new KeyBinding(
                        "key.stationbuilder.clear_rail_state",
                        InputUtil.Type.KEYSYM,
                        GLFW.GLFW_KEY_V,
                        "category.stationbuilder"
                )
        );
    }
}