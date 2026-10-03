# Fabric 1.20.x multi-version refactor (updated)

- `common/src/main/java` is the only shared source set; client and main sources are intentionally not split.
- `common/src/main/resources` contains both common and client mixin resources.
- `1.20.1/src/main/java` and `1.20.4/src/main/java` contain only target-specific compatibility/API shims.
- Screens are organized under `common/.../screens/`, matching the newer 1.21 project layout.
- Station elements were synchronized with the latest Fabric 1.21 data model: `TrackElement.track`, `MTRCurveData`, `CurveData`, and generic `PointProvider` remain the shared rail abstraction.
- `StationEditorScreen` now exposes a track block selector rather than the old boolean MTR toggle; 1.20.x keeps the legacy Fabric networking API.
- `StationElement.fromNbt` retains backwards compatibility for old `isMtrTrack` station presets.
- `StationGenerator` consumes `TrackElement.track`; MTR tracks are built through `MTRIntegration`, while ordinary rail blocks are placed directly. Create is intentionally not introduced because the 1.20.x targets do not declare the Create dependency.
- Gradle uses a single `main` source set and shared build configuration; target-specific dependencies stay in each version project.
