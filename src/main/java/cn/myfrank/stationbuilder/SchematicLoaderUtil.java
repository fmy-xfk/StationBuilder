package cn.myfrank.stationbuilder;

import cn.myfrank.stationbuilder.schematic4j.SchematicLoader;
import cn.myfrank.stationbuilder.schematic4j.exception.ParsingException;
import cn.myfrank.stationbuilder.schematic4j.schematic.Schematic;
import net.minecraft.SharedConstants;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public class SchematicLoaderUtil {

    public static StructureTemplate loadSchematic(Path filePath) throws IOException, ParsingException {
        Schematic schematic = SchematicLoader.load(filePath.toString());

        int width = schematic.width();
        int height = schematic.height();
        int length = schematic.length();

        CompoundTag nbt = new CompoundTag();
        // 关键修复：加入 DataVersion，游戏需要这个版本号以确保正确的结构升级转换
        nbt.putInt("DataVersion", SharedConstants.getCurrentVersion().getDataVersion().getVersion());

        ListTag sizeList = new ListTag();
        sizeList.add(IntTag.valueOf(width));
        sizeList.add(IntTag.valueOf(height));
        sizeList.add(IntTag.valueOf(length));
        nbt.put("size", sizeList);

        ListTag blocksList = new ListTag();
        ListTag paletteList = new ListTag();
        Map<String, Integer> paletteMap = new HashMap<>();

        // 保留空气作为0索引是个好习惯
        CompoundTag airEntry = new CompoundTag();
        airEntry.putString("Name", "minecraft:air");
        paletteMap.put("minecraft:air{}", 0);
        paletteList.add(airEntry);

        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                for (int z = 0; z < length; z++) {
                    var sBlock = schematic.block(x, y, z);
                    if (sBlock == null) continue;

                    String blockId = sBlock.block;

                    // 关键修复：直接跳过写入空气方块，避免 50x50x50 结构生成十几万无用数据卡死
                    if (blockId.equals("minecraft:air") || blockId.equals("minecraft:cave_air") || blockId.equals("minecraft:void_air")) {
                        continue;
                    }

                    // 使用包含属性的唯一键
                    String blockKey = blockId + sBlock.states.toString();

                    int stateId = paletteMap.computeIfAbsent(blockKey, k -> {
                        int id = paletteList.size();
                        CompoundTag paletteEntry = new CompoundTag();
                        paletteEntry.putString("Name", blockId);

                        // 关键修复：无损保留所有方块属性（如楼梯的 facing, half 等）
                        if (!sBlock.states.isEmpty()) {
                            CompoundTag propertiesNbt = new CompoundTag();
                            for (Map.Entry<String, String> entry : sBlock.states.entrySet()) {
                                propertiesNbt.putString(entry.getKey(), entry.getValue());
                            }
                            paletteEntry.put("Properties", propertiesNbt);
                        }

                        paletteList.add(paletteEntry);
                        return id;
                    });

                    CompoundTag blockNbt = new CompoundTag();
                    ListTag posList = new ListTag();
                    posList.add(IntTag.valueOf(x));
                    posList.add(IntTag.valueOf(y));
                    posList.add(IntTag.valueOf(z));
                    blockNbt.put("pos", posList);
                    blockNbt.putInt("state", stateId);

                    blocksList.add(blockNbt);
                }
            }
        }

        nbt.put("palette", paletteList);
        nbt.put("blocks", blocksList);
        nbt.put("entities", new ListTag());

        StructureTemplate template = new StructureTemplate();
        template.load(BuiltInRegistries.BLOCK, nbt);

        return template;
    }

    public static BlockState convertBlockState(String blockString) {
        if (blockString == null || blockString.isEmpty()) {
            return Blocks.AIR.defaultBlockState();
        }

        // schematic4j 的 name 包含附带的方块状态（如楼梯的朝向），我们用 "[" 截断只取其 ID 进行最基础的映射
        String blockId = blockString.split("\\[")[0];

        ResourceLocation id = ResourceLocation.tryParse(blockId);
        if (id == null) return Blocks.AIR.defaultBlockState();

        var block = BuiltInRegistries.BLOCK.getValue(id);

        // 如果注册表中找不到这个方块 (比如旧版模组的方块)，则用空气替代
        if (block == null || (block == Blocks.AIR && !blockId.equals("minecraft:air"))) {
            return Blocks.AIR.defaultBlockState();
        }

        // 简单返回默认状态 (如果希望支持精确朝向，需要编写额外的字符串解析逻辑)
        return block.defaultBlockState();
    }
}
