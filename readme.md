# Station Builder

This repository contains Fabric 1.20.1 and 1.20.4 targets sharing one `common` source tree. Client and main Java sources are intentionally not split: all shared and client code lives in `common/src/main/java`, while target-specific compatibility classes live in each target's `src/main/java`.

The screen package follows the newer 1.21 layout (`cn.myfrank.stationbuilder.screens`). Rail generation uses `CurveData` / `MTRCurveData` / `PointProvider`, and station tracks are represented by a concrete `TrackElement.track` identifier.
