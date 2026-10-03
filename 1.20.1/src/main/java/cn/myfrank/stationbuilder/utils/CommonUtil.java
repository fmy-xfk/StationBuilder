package cn.myfrank.stationbuilder.utils;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;
import net.minecraft.registry.Registries;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.structure.StructureTemplate;
import net.minecraft.text.Text;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.Identifier;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Cross-version compatibility helpers for Fabric 1.20.x.
 *
 * Keep Minecraft/Yarn API differences and environment queries here so
 * the shared gameplay code does not need to know the target version.
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

    public static ItemStack getItemStack(Identifier id) {
        Item item = getItem(id);
        return item != Items.AIR ? new ItemStack(item) : new ItemStack(getBlock(id));
    }

    public static StructureTemplate loadStructureTemplate(StructureTemplate template, NbtCompound nbt) {
        template.readNbt(Registries.BLOCK.getReadOnlyWrapper(), nbt);
        return template;
    }

    public static NbtCompound readCompressed(Path path) throws IOException {
        return NbtIo.readCompressed(path.toFile());
    }

    public static void writeCompressed(NbtCompound nbt, Path path) throws IOException {
        NbtIo.writeCompressed(nbt, path.toFile());
    }

    private static final boolean createLoaded = FabricLoader.getInstance().isModLoaded("create");
    public static boolean isCreateLoaded() {
        return createLoaded;
    }

    private static final boolean mtrLoaded = FabricLoader.getInstance().isModLoaded("mtr");
    public static boolean isMtrLoaded() {
        return mtrLoaded;
    }

    private static final boolean msdLoaded = FabricLoader.getInstance().isModLoaded("msd");
    public static boolean isMsdLoaded() {
        return msdLoaded;
    }

    public static boolean isSoftTransparent(BlockState state) {
        return state.isAir()
                || state.isReplaceable()
                || !state.getFluidState().isEmpty()
                || state.isIn(BlockTags.LOGS)
                || state.isIn(BlockTags.LEAVES);
    }

    public static boolean isNotLiquidTransparent(BlockState state) {
        return state.isAir()
                || state.isReplaceable()
                || state.isIn(BlockTags.LOGS)
                || state.isIn(BlockTags.LEAVES);
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
}
