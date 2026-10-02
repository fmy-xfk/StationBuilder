# Refactor notes

比较结果：

- 1.20.1 Java：143 个
- 1.20.4 Java：143 个
- 完全相同 Java：130 个
- 有差异 Java：13 个（两个 target 各保留一份）

13 个 target-specific Java 文件：

- `ForgePlatformServices.java`
- `ForgeServerEvents.java`
- `blocks/ModBlocks.java`
- `blocks/StationBuilderBlock.java`
- `gui/GuiPanel.java`
- `gui/GuiScreen.java`
- `gui/GuiScrollablePanel.java`
- `manager/BuildingTemplateManager.java`
- `screens/BuildingPlacerScreen.java`
- `screens/BuildingSelectionScreen.java`
- `screens/PresetSaveScreen.java`
- `screens/PresetSelectionScreen.java`
- `screens/StationEditorScreen.java`

资源方面：22 个文件完全相同并进入 common；1.20.1 额外保留两个 item descriptor、主动轨道纹理及其 mcmeta；1.20.4 保留不同版本的主动轨道纹理。

主要 API 差异：

- Network：1.20.1 使用 `NetworkRegistry` / `NetworkEvent.Context`，1.20.4 使用 `ChannelBuilder` / `CustomPayloadEvent.Context`。
- Server tick：1.20.4 使用 `TickEvent.ServerTickEvent.Post`。
- Block settings：1.20.4 使用 `BlockBehaviour.Properties.ofFullCopy`。
- Block codec / `playerWillDestroy`：1.20.4 API 签名变化。
- GUI scroll：1.20.4 的 `mouseScrolled` 增加 horizontal + vertical amount。
- NBT IO：`NbtIo.readCompressed/writeCompressed` 签名变化。


## Fix: ForgeGradle plugin not found

The first generated version omitted `pluginManagement.repositories` from each target's `settings.gradle`. Forge's official ForgeGradle 6 documentation requires the MinecraftForge Maven to be listed there for plugin resolution. This was added to both 1.20.1 and 1.20.4 settings files.


## 2026-10-02: Forge 1.20.4 runtime mod-discovery fix

Forge 49.x / Minecraft 1.20.4 expects development mod classes and processed resources to be available from the same source-set output directory. The merged project previously left them in Gradle's default `build/classes/java/main` and `build/resources/main` directories, which caused `constructed 0 mods: [], but had 1 mods specified` / `The Mod File .../build/resources/main has mods that were not found`.

The 1.20.4 target now merges `sourceSets` output into `build/sourcesSets/<sourceSet>`, and its run configuration no longer declares a manual `mods {}` entry. This follows the Forge 49.x/1.20.4 development layout used by current examples and avoids treating `build/resources/main` as an isolated mod file.
