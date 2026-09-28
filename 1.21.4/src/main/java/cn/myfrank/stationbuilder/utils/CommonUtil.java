package cn.myfrank.stationbuilder.utils;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.tags.BlockTags;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.neoforged.fml.ModList;

/**
 * Cross-version compatibility helpers.
 *
 * Keep Minecraft/NeoForge API differences here instead of spreading them
 * throughout gameplay, GUI and generator code.
 */
public final class CommonUtil {
    public static BlockState getBlockState(ResourceLocation id) {
        return getBlock(id).defaultBlockState();
    }

    public static Block getBlock(ResourceLocation id) {
        return BuiltInRegistries.BLOCK.getValue(id);
    }

    public static Item getItem(ResourceLocation id) {
        return BuiltInRegistries.ITEM.getValue(id);
    }

    public static boolean hasBlock(ResourceLocation id) {
        return BuiltInRegistries.BLOCK.containsKey(id);
    }

    public static boolean hasItem(ResourceLocation id) {
        return BuiltInRegistries.ITEM.containsKey(id);
    }

    public static ResourceLocation getBlockId(Block block) {
        return BuiltInRegistries.BLOCK.getKey(block);
    }

    public static ResourceLocation getItemId(Item item) {
        return BuiltInRegistries.ITEM.getKey(item);
    }

    /**
     * Resolve an item first, then fall back to the corresponding block item.
     * This mirrors the behavior used by GhostSlot.
     */
    public static ItemStack getItemStack(ResourceLocation id) {
        Item item = getItem(id);
        return item != Items.AIR ? new ItemStack(item) : new ItemStack(getBlock(id));
    }

    /**
     * Version adapter for StructureTemplate#load.
     */
    public static StructureTemplate loadStructureTemplate(StructureTemplate template, CompoundTag nbt) {
        template.load(BuiltInRegistries.BLOCK, nbt);
        return template;
    }

    public static StructureTemplate loadStructureTemplate(CompoundTag nbt) {
        return loadStructureTemplate(new StructureTemplate(), nbt);
    }

    private static final boolean isCreateLoaded = ModList.get().isLoaded("create");
    public static boolean isCreateLoaded() {
        return isCreateLoaded;
    }

    private static final boolean isMTRLoaded = ModList.get().isLoaded("mtr");
    public static boolean isMtrLoaded() {
        return isMTRLoaded;
    }

    private static final boolean isMsdLoaded = ModList.get().isLoaded("msd");
    public static boolean isMsdLoaded() {
        return isMsdLoaded;
    }

    public static boolean isSoftTransparent(BlockState state) {
        return state.isAir()
                || state.canBeReplaced()
                || !state.getFluidState().isEmpty()
                || state.is(BlockTags.LOGS)
                || state.is(BlockTags.LEAVES);
    }

    public static boolean isNotLiquidTransparent(BlockState state) {
        return state.isAir()
                || state.canBeReplaced()
                || state.is(BlockTags.LOGS)
                || state.is(BlockTags.LEAVES);
    }

    public static String getRotName(Rotation rotation) {
        return Component.translatable("gui.stationbuilder.rotation_" + rotation.name()).getString();
    }

    /**
     * Safe conversion used by config/schematic input paths.
     */
    public static Block getBlockOrAir(ResourceLocation id) {
        Block block = getBlock(id);
        if (block == null || (block == Blocks.AIR && !"minecraft:air".equals(id.toString()))) {
            return Blocks.AIR;
        }
        return block;
    }
}
