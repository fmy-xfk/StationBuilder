package cn.myfrank.stationbuilder;

import net.minecraft.component.ComponentType;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public class ModComponents {
    public static final ComponentType<NbtComponent> STATION_BUILDER_DATA =
            Registry.register(
                    Registries.DATA_COMPONENT_TYPE,
                    Identifier.of(StationBuilder.MOD_ID, "station_builder_data"),
                    ComponentType.<NbtComponent>builder()
                            .codec(NbtComponent.CODEC)
                            .build()
            );

    public static final ComponentType<NbtComponent> RAIL_BUILDER_DATA =
            Registry.register(
                    Registries.DATA_COMPONENT_TYPE,
                    Identifier.of(StationBuilder.MOD_ID, "rail_builder_data"),
                    ComponentType.<NbtComponent>builder()
                            .codec(NbtComponent.CODEC)
                            .build()
            );

    // 增加一个初始化方法，用于在游戏初始化早期触发该类的静态代码块
    public static void initialize() {
        StationBuilder.LOGGER.info("Registering Station Builder Components");
    }
}