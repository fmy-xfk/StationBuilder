package cn.myfrank.stationbuilder;

import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;

public class ModItems {
    public static final Identifier RAIL_BUILDER_ID = Identifier.of("stationbuilder", "rail_builder");
    public static final RegistryKey<Item> RAIL_BUILDER_ITEM_KEY =
            RegistryKey.of(RegistryKeys.ITEM, RAIL_BUILDER_ID);
    public static final Item RAIL_BUILDER_ITEM = new RailBuilderItem(
            new Item.Settings().maxCount(1)
    );

    public static final Identifier BUILDING_SELECTOR_ID = Identifier.of("stationbuilder", "building_selector");
    public static final RegistryKey<Item> BUILDING_SELECTOR_ITEM_KEY =
            RegistryKey.of(RegistryKeys.ITEM, BUILDING_SELECTOR_ID);
    public static final Item BUILDING_SELECTOR_ITEM = new BuildingSelectorItem(
            new Item.Settings().maxCount(1)
    );

    public static final Identifier BUILDING_PLACER_ID = Identifier.of("stationbuilder", "building_placer");
    public static final RegistryKey<Item> BUILDING_PLACER_ITEM_KEY =
            RegistryKey.of(RegistryKeys.ITEM, BUILDING_PLACER_ID);
    public static final Item BUILDING_PLACER_ITEM = new BuildingPlacerItem(
            new Item.Settings().maxCount(1)
    );

    public static void register() {
        Registry.register(Registries.ITEM, RAIL_BUILDER_ID, RAIL_BUILDER_ITEM);
        Registry.register(Registries.ITEM, BUILDING_SELECTOR_ID, BUILDING_SELECTOR_ITEM);
        Registry.register(Registries.ITEM, BUILDING_PLACER_ID, BUILDING_PLACER_ITEM);
    }
}