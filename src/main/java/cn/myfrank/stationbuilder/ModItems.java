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

    public static void register() {
        Registry.register(Registries.ITEM, RAIL_BUILDER_ID, RAIL_BUILDER_ITEM);
    }
}