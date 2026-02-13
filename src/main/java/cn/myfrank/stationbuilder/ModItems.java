package cn.myfrank.stationbuilder;

import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public class ModItems {
    public static final Item RAIL_BUILDER_ITEM = new RailBuilderItem(new Item.Settings().maxCount(1));

    public static void register() {
        Identifier id = new Identifier("stationbuilder", "rail_builder");
        Registry.register(Registries.ITEM, id, RAIL_BUILDER_ITEM);
    }
}
