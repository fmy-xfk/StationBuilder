# StationBuilder Fabric multi-version

This repository keeps Minecraft/Fabric targets separate while sharing the actual mod source.

## Layout

- `common/` — shared Java/resources.
- `1.21.1/` — Fabric 1.21.1 target and its tiny compatibility layer.
- `1.21.4/` — Fabric 1.21.4 target and its tiny compatibility layer.
- `gradle/fabric-common.gradle` — Gradle configuration shared by both targets.

## Build

```bat
cd 1.21.1
gradlew build

cd ..\1.21.4
gradlew build
```

### Version-specific compatibility

`CommonUtil` contains registry/direction/structure-template/custom-model-data differences. `ClientUtil` contains client-only renderer differences. The three item classes keep only the `Item#use` return-type difference in each target; their actual logic lives in `common/*Base.java`.

Create remains a target-specific facade. Fabric currently has no Create implementation bundled here, so both facades are empty shells; do not add Create dependencies to 1.21.4.
