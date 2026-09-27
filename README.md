# StationBuilder multi-version layout

This repository keeps one shared source tree and two independent NeoForge targets.

```text
common/
  src/main/java/...
  src/main/resources/...

gradle/
  neoforge-base.gradle

1.21.1/
  build.gradle
  gradle.properties
  settings.gradle
  src/main/java/...        # version-specific adapters / Create implementation / client API differences
  src/main/resources/...   # mixin config
  src/main/templates/...   # neoforge.mods.toml template

1.21.4/
  build.gradle
  gradle.properties
  settings.gradle
  src/main/java/...        # version-specific adapters / Create stub / client API differences
  src/main/resources/...   # mixin config
  src/main/templates/...   # neoforge.mods.toml template
```

`common/src/main/java` is compiled twice, once by each target. `CommonUtil` remains target-specific because its implementation contains the actual Minecraft API difference (`Registry#get` vs `Registry#getValue`, and `StructureTemplate#load` lookup type).

Create is intentionally 1.21.1-only. The 1.21.4 target contains the same `CreateIntegration` facade with inert implementations, but does not depend on Create/Ponder/Flywheel/Catnip.

Build each target from its own directory:

```bash
cd 1.21.1
./gradlew build

cd ../1.21.4
./gradlew build
```
