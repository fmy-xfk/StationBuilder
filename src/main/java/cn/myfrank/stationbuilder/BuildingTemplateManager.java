package cn.myfrank.stationbuilder;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.neoforged.fml.loading.FMLPaths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class BuildingTemplateManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(BuildingTemplateManager.class);
    private static final Path BUILDINGS_PATH = FMLPaths.GAMEDIR.get()
            .resolve("stationbuilder/buildings");
    private static final Map<String, StructureTemplate> TEMPLATES = new ConcurrentHashMap<>();

    // 启动时加载 stationbuilder/buildings/ 下的所有 .nbt 文件
    public static void loadTemplates() {
        TEMPLATES.clear();
        File dir = BUILDINGS_PATH.toFile();
        if (!dir.exists() && !dir.mkdirs()) {
            LOGGER.warn("Could not create buildings directory: {}", BUILDINGS_PATH);
            return;
        }

        try (var stream = Files.list(BUILDINGS_PATH)) {
            stream.filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".nbt"))
                    .forEach(p -> {
                        try {
                            String name = p.getFileName().toString().replaceFirst("\\.nbt$", "");
                            StructureTemplate template = new StructureTemplate();
                            template.load(
                                    BuiltInRegistries.BLOCK.asLookup(),
                                    NbtIo.readCompressed(p, NbtAccounter.unlimitedHeap())
                            );
                            TEMPLATES.put(name, template);
                            LOGGER.info("Loaded building template: {}", name);
                        } catch (IOException e) {
                            LOGGER.error("Failed to load building template: {}", p, e);
                        }
                    });
        } catch (IOException e) {
            LOGGER.error("Failed to scan buildings directory", e);
        }
    }

    public static Optional<StructureTemplate> getTemplate(String name) {
        return Optional.ofNullable(TEMPLATES.get(name));
    }

    public static List<String> getTemplateNames() {
        return new ArrayList<>(TEMPLATES.keySet());
    }

    // 运行时添加（用于导入文件）
    public static void addTemplate(String name, StructureTemplate template) {
        TEMPLATES.put(name, template);
        // 可选：同时保存到磁盘以便下次启动使用
        Path file = BUILDINGS_PATH.resolve(name + ".nbt");
        try {
            CompoundTag nbt = template.save(new CompoundTag());
            NbtIo.writeCompressed(nbt, file);
        } catch (IOException e) {
            LOGGER.error("Failed to save template: {}", file, e);
        }
    }
}
