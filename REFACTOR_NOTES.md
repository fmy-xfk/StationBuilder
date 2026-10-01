# Merge notes

- 156 files were byte-identical between the uploaded 1.21.1 and 1.21.4 projects.
- The merge keeps those files in `common` whenever they are not target metadata or compatibility-sensitive.
- `CommonUtil` remains target-specific because `StructureTemplate#readNbt`, horizontal-direction helpers, registry-key settings, and CustomModelData construction differ between the two mappings/API levels.
- `ClientUtil` remains target-specific because box rendering moved from `WorldRenderer.drawBox` to `VertexRendering.drawBox`.
- `BuildingSelectorItem`, `BuildingPlacerItem`, and `RailBuilderItem` are target-specific thin subclasses; shared behavior lives in `*ItemBase`.
- Create stays outside common and is represented by a small facade in each target.
- 1.21.4's unused `ExampleMixin.java` and 1.21.1's top-level `comb.py` are intentionally not carried into the merged project.
