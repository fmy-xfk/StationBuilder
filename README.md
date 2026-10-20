# StationBuilder 多版本工程（Forge 1.20.x / NeoForge 1.21.x）

本工程只包含 Forge 1.20.x 与 NeoForge 1.21.x，不包含 Fabric 构建目标或 Fabric 源码。

## 工程结构

- `common/`：确认可在 1.20.x 与 1.21.x 之间共用的源码和资源。
- `1.20-common/`：1.20.x 系列共用源码和资源。由于 1.20.x 对应 Forge，该目录直接包含这组版本需要的实现，不再单独设置 Forge 平台适配源码层。
- `1.21-common/`：1.21.x 系列共用源码和资源。由于 1.21.x 对应 NeoForge，该目录直接包含这组版本需要的实现，不再单独设置 NeoForge 平台适配源码层。
- `1.20.1/`、`1.20.4/`、`1.21.1/`、`1.21.4/`：各 Minecraft 版本的独立构建目标和仅该版本需要的源码、资源及配置。
- `gradle/target-1.20.gradle`、`gradle/target-1.21.gradle`：按版本系列复用的 Gradle 构建配置，不是额外的源码层。由于 ForgeGradle 与 ModDevGradle 的配置不同，仍分别保留对应构建脚本。

## 源码加载规则

每个版本目标只加载三部分：

1. `common/`
2. 对应系列的 `1.20-common/` 或 `1.21-common/`
3. 目标自身的 `src/main/java`、`src/main/resources` 和 `src/generated/resources`

## 构建

在目标目录中执行：

- Windows：`gradlew.bat build`
- Linux/macOS：`./gradlew build`

例如进入 `1.20.1/` 后构建 Forge 1.20.1，进入 `1.21.4/` 后构建 NeoForge 1.21.4。

## 验证说明

源码与资源按共享层重组并进行静态路径审计。实际 Gradle 编译仍需在能访问 Gradle 发行包及 Minecraft/Forge/NeoForge 依赖仓库的环境中执行；不能将静态检查等同于成功编译。
