package cn.myfrank.stationbuilder.utils;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

public class CommonUtil {
    public static BlockState getBlockState(ResourceLocation rl) {
        return BuiltInRegistries.BLOCK.get(rl).defaultBlockState();
    }
    
    public static Block getBlock(ResourceLocation rl) {
        return BuiltInRegistries.BLOCK.get(rl);
    }

    public static Item getItem(ResourceLocation rl) {
        return BuiltInRegistries.ITEM.get(rl);
    }
}