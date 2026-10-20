# StationBuilder Fabric multi-version

Four independent Fabric Loom targets are maintained in this repository: Minecraft 1.20.1, 1.20.4, 1.21.1, and 1.21.4.

## Source layout

- `common/`: Java/resources confirmed identical between the supplied 1.20.x and 1.21.x source trees.
- `1.20-common/`: code/resources shared by the 1.20.x family, including family API differences and 1.20 mixin configuration.
- `1.21-common/`: code/resources shared by the 1.21.x family, including family API differences and the Create compatibility facade.
- `1.20.1/`, `1.20.4/`, `1.21.1/`, `1.21.4/`: independent target builds and compatibility shims that differ at the patch-version level.
- `gradle/fabric-common.gradle`: source-set and common Loom configuration. Each target automatically loads `common` plus its matching family source set.

## Build

Run the target Gradle wrapper from the target directory, for example:

```bash
cd 1.20.1 && ./gradlew build
cd ../1.20.4 && ./gradlew build
cd ../1.21.1 && ./gradlew build
cd ../1.21.4 && ./gradlew build
```

Minecraft API differences are intentionally not forced into a single implementation where that would make the code fail to compile: patch-specific shims such as `CommonUtil`, `ClientUtil`, the 1.20 GUI scroll signatures, `StationBuilderBlock`, and Create integration remain target-specific as needed.
