package cn.myfrank.stationbuilder;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import org.lwjgl.glfw.GLFW;

@Mod.EventBusSubscriber(modid = StationBuilder.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ForgeClientModEvents {
    public static final KeyMapping CLEAR_RAIL_STATE = new KeyMapping("key.stationbuilder.clear_rail_state", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_V, "category.stationbuilder");
    public static final KeyMapping UNDO_PLACER = new KeyMapping("key.stationbuilder.undo_placer", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_U, "category.stationbuilder");

    @SubscribeEvent
    public static void registerKeys(RegisterKeyMappingsEvent event) {
        event.register(CLEAR_RAIL_STATE);
        event.register(UNDO_PLACER);
        ForgeClientEvents.registerReceivers();
    }

    @SubscribeEvent
    public static void clientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            // 补全：注册和 Fabric 版本一致的物品动画闪烁模型谓词 (Model Predicate)
            ItemProperties.register(
                    ModItems.RAIL_BUILDER_ITEM.get(),
                    ResourceLocation.fromNamespaceAndPath(StationBuilder.MOD_ID, "building"),
                    (stack, level, entity, seed) -> {
                        if (level == null) return 0.0F;

                        // 当且仅当处于轨道放置过程中时运行闪烁逻辑
                        if (!RailBuilderState.isBuilding(stack)) {
                            return 0.0F;
                        }

                        long t = level.getGameTime();
                        return (t / 10 % 2 == 0) ? 1.0F : 0.0F;
                    }
            );
        });
    }
}