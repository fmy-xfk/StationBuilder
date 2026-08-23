package cn.myfrank.stationbuilder;

import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public class ModItems {
    public static final Item RAIL_BUILDER_ITEM = new RailBuilderItem(new Item.Settings().maxCount(1));
    public static final Item BUILDING_SELECTOR_ITEM = new BuildingSelectorItem(new Item.Settings().maxCount(1)); // 新增
    public static final Item BUILDING_PLACER_ITEM = new BuildingPlacerItem(new Item.Settings().maxCount(1));     // 新增

    public static void register() {
        Registry.register(Registries.ITEM, new Identifier("stationbuilder", "rail_builder"), RAIL_BUILDER_ITEM);
        Registry.register(Registries.ITEM, new Identifier("stationbuilder", "building_selector"), BUILDING_SELECTOR_ITEM); // 新增
        Registry.register(Registries.ITEM, new Identifier("stationbuilder", "building_placer"), BUILDING_PLACER_ITEM);     // 新增
    }
}