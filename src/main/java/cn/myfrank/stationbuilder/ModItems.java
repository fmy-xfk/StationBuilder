package cn.myfrank.stationbuilder;

import net.minecraft.world.item.Item;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModItems {
    public static final ResourceLocation RAIL_BUILDER_ID = ResourceLocation.fromNamespaceAndPath(StationBuilder.MOD_ID, "rail_builder");

    // 1. 创建 ITEMS 延迟注册器
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, StationBuilder.MOD_ID);

    // 2. 将静态实例化替换为延迟注册，返回 RegistryObject
    public static final RegistryObject<Item> RAIL_BUILDER_ITEM = ITEMS.register("rail_builder",
            () -> new RailBuilderItem(new Item.Properties().stacksTo(1)));

    // 3. 事件总线绑定方法
    public static void register(IEventBus bus) {
        ITEMS.register(bus);
    }
}