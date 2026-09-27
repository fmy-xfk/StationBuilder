# Refactor notes

## Compatibility surface (`CommonUtil`)

The existing `utils/CommonUtil.java` remains target-specific on purpose. Its public API is shared, while its implementation contains the actual Minecraft-version differences.

Current shared API:

- block/item lookup
- block/item registry keys
- item-stack resolution for a block id
- `StructureTemplate` loading
- mod-loaded checks
- soft-transparent block checks
- non-liquid-transparent block checks
- rotation display-name lookup
- safe block-or-air resolution

The most important API differences are now isolated to two lines/branches:

- 1.21.1: `BuiltInRegistries.*.get(...)` and `StructureTemplate#load(...asLookup(), ...)`
- 1.21.4: `BuiltInRegistries.*.getValue(...)` and `StructureTemplate#load(BuiltInRegistries.BLOCK, ...)`

## Shared code moved into `common`

Most gameplay, GUI, station/rain generation, MTR integration, schematic4j, managers, elements, state, blocks, and configuration model classes are shared.

The common `StationBuilder` keeps its old helper methods as a small compatibility facade so existing target/client code can migrate gradually. Those methods delegate to `CommonUtil` or `CreateIntegration.isAvailable()`.

## Create isolation

Create is strictly target-specific:

- `1.21.1/create/CreateIntegration.java` is the real implementation.
- `1.21.1/create/CreateCurveData.java` and `mixin/create/PlacementInfoAccessor.java` remain target-specific.
- `1.21.4/create/CreateIntegration.java` has the same public methods but no Create imports and `isAvailable()` always returns `false`.
- 1.21.4 has no Create/Ponder/Flywheel/Catnip Gradle dependencies.
- `stationbuilder.mixins.json` is target-specific because only 1.21.1 contains the Create mixin accessor.

## Gradle organization

Each Minecraft version stays an independent Gradle project. Both projects apply the shared `../gradle/neoforge-base.gradle` convention script and add `../common/src/main/java` + `../common/src/main/resources` to their main source set.

This avoids a binary `common.jar` built against one Minecraft version and keeps each target's mappings/NeoForge toolchain independent.

## Why `StationBuilderClient` and three item classes stay target-specific

These files contain real API-signature differences rather than simple lookup differences:

- `StationBuilderClient.java`: render-stage / renderer API changes and other client-side differences.
- `BuildingPlacerItem.java`: `InteractionResultHolder<ItemStack>` vs `InteractionResult`.
- `BuildingSelectorItem.java`: same interaction API change.
- `RailBuilderItem.java`: same interaction API change plus `CustomModelData` constructor changes.

Their business logic can be extracted further later, but keeping their public Minecraft overrides in the target source avoids introducing artificial adapter interfaces.
