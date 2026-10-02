# StationBuilder Forge 多版本工程

本工程将 Forge 1.20.1 与 Forge 1.20.4 合并到同一个仓库，同时尽量共享源码与资源。

## 结构

- `common/`：两个版本完全相同的 Java 源码和通用资源。
- `1.20.1/`：Forge 47.x target，只保留 1.20.1 独有或存在 API 差异的文件。
- `1.20.4/`：Forge 49.x target，只保留 1.20.4 独有或存在 API 差异的文件。
- `gradle/forge-common.gradle`：两个 target 共享的 Gradle 配置。

## 构建

两个 target 都保持为独立 ForgeGradle 工程，同时复用同一个 `common/` 源码目录：

```text
cd 1.20.1
./gradlew build

cd ../1.20.4
./gradlew build
```

Windows：

```text
cd 1.20.1
gradlew.bat build
```

## Gradle 边界

公共脚本只包含版本无关的 Java、资源、仓库、Mixin、processResources、jar、publishing 和 IDE 配置。

`minecraft { ... }`、Minecraft/Forge mappings、DataGen runs 和 target-specific mod dependencies 继续留在各版本 `build.gradle`，避免把 1.20.x 的 ForgeGradle target API 强行统一。

## Create

上传的两个 Forge 工程中的 `CreateIntegration` 都是空壳实现，因此随完全相同 Java 一起进入 `common/`。以后某一版本真正加入 Create 时，再把该版本实现移回 target 即可。


## ForgeGradle plugin resolution

The target `settings.gradle` files intentionally configure Forge Maven under `pluginManagement.repositories`. ForgeGradle 6 is distributed via the MinecraftForge Maven; this repository is required for resolving the `net.minecraftforge.gradle` plugin used by both 1.20.x targets.
