package cn.myfrank.stationbuilder;

import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(StationBuilder.MOD_ID);

    public static final DeferredItem<RailBuilderItem> RAIL_BUILDER_ITEM =
            ITEMS.registerItem("rail_builder", props -> new RailBuilderItem(props.stacksTo(1)));
    public static final DeferredItem<BuildingSelectorItem> BUILDING_SELECTOR_ITEM =
            ITEMS.registerItem("building_selector", props -> new BuildingSelectorItem(props.stacksTo(1)));
    public static final DeferredItem<BuildingPlacerItem> BUILDING_PLACER_ITEM =
            ITEMS.registerItem("building_placer", props -> new BuildingPlacerItem(props.stacksTo(1)));
}
