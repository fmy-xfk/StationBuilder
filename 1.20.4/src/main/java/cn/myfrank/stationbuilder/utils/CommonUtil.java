package cn.myfrank.stationbuilder.utils;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

import java.io.File;
import java.io.IOException;

/**
 * Minecraft 1.20.4 compatibility facade.
 *
 * Shared helpers live in {@link CommonUtilBase}. This class only contains
 * version-specific APIs that are safe to use from common/server code.
 */
public final class CommonUtil extends CommonUtilBase {
    private CommonUtil() {
    }

    public static StructureTemplate loadStructureTemplate(File file) throws IOException {
        CompoundTag nbt = NbtIo.readCompressed(file.toPath(), NbtAccounter.unlimitedHeap());
        return loadStructureTemplate(nbt);
    }
}
