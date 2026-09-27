package cn.myfrank.stationbuilder;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModComponents {
    public static final DeferredRegister.DataComponents DATA_COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, StationBuilder.MOD_ID);

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<CustomData>> STATION_BUILDER_DATA =
            DATA_COMPONENTS.registerComponentType("station_builder_data",
                    builder -> builder.persistent(CustomData.CODEC).networkSynchronized(CustomData.STREAM_CODEC));

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<CustomData>> RAIL_BUILDER_DATA =
            DATA_COMPONENTS.registerComponentType("rail_builder_data",
                    builder -> builder.persistent(CustomData.CODEC).networkSynchronized(CustomData.STREAM_CODEC));

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<CustomData>> SELECTOR_DATA =
            DATA_COMPONENTS.registerComponentType("selector_data",
                    builder -> builder.persistent(CustomData.CODEC).networkSynchronized(CustomData.STREAM_CODEC));

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<CustomData>> PLACER_DATA =
            DATA_COMPONENTS.registerComponentType("placer_data",
                    builder -> builder.persistent(CustomData.CODEC).networkSynchronized(CustomData.STREAM_CODEC));
}
