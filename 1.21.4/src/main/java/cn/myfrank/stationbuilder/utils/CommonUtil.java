package cn.myfrank.stationbuilder.utils;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.BlockState;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.component.type.CustomModelDataComponent;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.structure.StructureTemplate;
import net.minecraft.text.Text;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Direction;

/**
 * Cross-version compatibility helpers for Fabric. Keep Minecraft/Yarn API differences here.
 */
public final class CommonUtil {
    private CommonUtil() {}

    public static BlockState getBlockState(Identifier id) {
        return getBlock(id).getDefaultState();
    }

    public static Block getBlock(Identifier id) {
        return Registries.BLOCK.get(id);
    }

    public static Item getItem(Identifier id) {
        return Registries.ITEM.get(id);
    }

    public static boolean hasBlock(Identifier id) {
        return Registries.BLOCK.containsId(id);
    }

    public static boolean hasItem(Identifier id) {
        return Registries.ITEM.containsId(id);
    }

    public static Identifier getBlockId(Block block) {
        return Registries.BLOCK.getId(block);
    }

    public static Identifier getItemId(Item item) {
        return Registries.ITEM.getId(item);
    }

    public static ItemStack getItemStack(Identifier id) {
        Item item = getItem(id);
        return item != Items.AIR ? new ItemStack(item) : new ItemStack(getBlock(id));
    }

    public static StructureTemplate loadStructureTemplate(StructureTemplate template, NbtCompound nbt) {
        template.readNbt(Registries.BLOCK, nbt);
        return template;
    }

    public static StructureTemplate loadStructureTemplate(NbtCompound nbt) {
        return loadStructureTemplate(new StructureTemplate(), nbt);
    }

    public static Direction fromHorizontal(int quarterTurns) {
        return Direction.fromHorizontalQuarterTurns(quarterTurns);
    }

    public static int getHorizontal(Direction direction) {
        return direction.getHorizontalQuarterTurns();
    }

    public static AbstractBlock.Settings blockSettings(AbstractBlock.Settings settings, RegistryKey<Block> key) {
        return settings.registryKey(key);
    }

    public static Item.Settings itemSettings(Item.Settings settings, RegistryKey<Item> key) {
        return settings.registryKey(key);
    }

    public static CustomModelDataComponent customModelData(int value) {
        return new CustomModelDataComponent(java.util.List.of((float) value), java.util.List.of(), java.util.List.of(), java.util.List.of());
    }

    private static final boolean hasMTR = FabricLoader.getInstance().isModLoaded("mtr");
    private static final boolean hasCreate = FabricLoader.getInstance().isModLoaded("create");
    private static final boolean hasMSD = FabricLoader.getInstance().isModLoaded("msd");

    public static boolean isMTRLoaded() { return hasMTR; }
    public static boolean isCreateLoaded() { return hasCreate; }
    public static boolean isMsdLoaded() { return hasMSD; }

    public static boolean isSoftTransparent(BlockState state) {
        return state.isAir() || state.isReplaceable() || !state.getFluidState().isEmpty()
                || state.isIn(BlockTags.LOGS) || state.isIn(BlockTags.LEAVES);
    }

    public static boolean isNotLiquidTransparent(BlockState state) {
        return state.isAir() || state.isReplaceable()
                || state.isIn(BlockTags.LOGS) || state.isIn(BlockTags.LEAVES);
    }

    public static String getRotName(BlockRotation rotation) {
        return Text.translatable("gui.stationbuilder.rotation_" + rotation.name()).getString();
    }

    public static Block getBlockOrAir(Identifier id) {
        Block block = getBlock(id);
        if (block == null || (block == Blocks.AIR && !"minecraft:air".equals(id.toString()))) {
            return Blocks.AIR;
        }
        return block;
    }

    public static Direction fromRotation(float angle) {
        return Direction.fromHorizontalDegrees(angle);
    }
}
