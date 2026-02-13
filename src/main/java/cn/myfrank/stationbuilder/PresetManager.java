package cn.myfrank.stationbuilder;

import cn.myfrank.stationbuilder.elements.StationElement;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.StringNbtReader;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class PresetManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path PRESET_PATH = FabricLoader.getInstance().getConfigDir().resolve("stationbuilder/presets");

    public static void savePreset(String name, int length, List<StationElement> elements) {
        try {
            File dir = PRESET_PATH.toFile();
            if (!dir.exists()) dir.mkdirs();

            JsonObject json = new JsonObject();
            json.addProperty("length", length);
            JsonArray elementArray = new JsonArray();
            for (StationElement e : elements) {
                // 将 NBT 转为字符串存入 JSON
                elementArray.add(e.toNbt().toString());
            }
            json.add("elements", elementArray);

            try (FileWriter writer = new FileWriter(new File(dir, name + ".json"))) {
                GSON.toJson(json, writer);
            }
        } catch (Exception e) { e.printStackTrace(); }
    }

    public static List<String> getPresetList() {
        List<String> list = new ArrayList<>();
        File dir = PRESET_PATH.toFile();
        if (dir.exists() && dir.listFiles() != null) {
            for (File f : dir.listFiles()) {
                if (f.getName().endsWith(".json")) list.add(f.getName().replace(".json", ""));
            }
        }
        return list;
    }

    public static PresetData loadPreset(String name) {
        try {
            File file = new File(PRESET_PATH.toFile(), name + ".json");
            if (!file.exists()) return null;
            try (FileReader reader = new FileReader(file)) {
                JsonObject json = GSON.fromJson(reader, JsonObject.class);
                int length = json.get("length").getAsInt();
                List<StationElement> elements = new ArrayList<>();
                JsonArray elementArray = json.getAsJsonArray("elements");
                for (int i = 0; i < elementArray.size(); i++) {
                    NbtCompound nbt = StringNbtReader.parse(elementArray.get(i).getAsString());
                    elements.add(StationElement.fromNbt(nbt));
                }
                return new PresetData(length, elements);
            }
        } catch (Exception e) { e.printStackTrace(); return null; }
    }

    public record PresetData(int length, List<StationElement> elements) {}

    public static Path getPresetPath() {
        return FabricLoader.getInstance().getConfigDir().resolve("stationbuilder/presets");
    }
}