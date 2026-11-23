package cn.myfrank.stationbuilder;

import org.lwjgl.glfw.GLFW;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;

public class StationBuilderKeyBindings {

    public static KeyBinding CLEAR_RAIL_STATE;
    public static KeyBinding UNDO; // Ctrl + this key; the key itself is rebindable

    public static void register() {
        CLEAR_RAIL_STATE = KeyBindingHelper.registerKeyBinding(
                new KeyBinding(
                        "key.stationbuilder.clear_rail_state",
                        InputUtil.Type.KEYSYM,
                        GLFW.GLFW_KEY_V,
                        "category.stationbuilder"
                )
        );
        // Ctrl is always required; only the key after Ctrl is configurable here.
        // The default is Ctrl+Z, and rebinding it to K makes it Ctrl+K.
        UNDO = KeyBindingHelper.registerKeyBinding(
                new KeyBinding(
                        "key.stationbuilder.undo",
                        InputUtil.Type.KEYSYM,
                        GLFW.GLFW_KEY_Z,
                        "category.stationbuilder"
                )
        );
    }
}