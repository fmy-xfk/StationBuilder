package cn.myfrank.stationbuilder.mtr;

import cn.myfrank.stationbuilder.scheduler.Json;
import cn.myfrank.stationbuilder.PlatformServices;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import org.mtr.core.data.Depot;
import org.mtr.core.data.Platform;
import org.mtr.core.data.Position;
import org.mtr.core.data.Route;
import org.mtr.core.data.RoutePlatformData;
import org.mtr.core.data.Siding;
import org.mtr.core.data.Station;
import org.mtr.core.generated.operation.ListDataResponseSchema;
import org.mtr.core.operation.ListDataResponse;
import org.mtr.libraries.it.unimi.dsi.fastutil.longs.Long2ObjectAVLTreeMap;
import org.mtr.libraries.it.unimi.dsi.fastutil.longs.LongArrayList;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.Object2ObjectAVLTreeMap;
import org.mtr.mod.Init;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Reads MTR 4.0.x data directly from the running game (server side) via MTR's {@code list_data}
 * operation. The resulting JSON matches the compact envelope the planner already parses.
 */
public final class MtrGameDataSource {

    private MtrGameDataSource() {
    }

    public record Snapshot(String stationsAndRoutesJson, String departuresJson, String error, ListDataResponse response) {
        public boolean isSuccess() {
            return error == null;
        }

        public static Snapshot error(String message) {
            return new Snapshot("", "", message, null);
        }
    }

    public static void collect(MinecraftServer server, ServerLevel level, Consumer<Snapshot> callback) {
        if (!PlatformServices.isModLoaded("mtr")) {
            callback.accept(Snapshot.error("MTR is not installed on this server"));
            return;
        }
        try {
            Init.sendMessageC2S(
                    "list_data",
                    new org.mtr.mapping.holder.MinecraftServer(server),
                    new org.mtr.mapping.holder.World(level),
                    MtrEmptyRequest.INSTANCE,
                    response -> {
                        try {
                            callback.accept(build(response));
                        } catch (Throwable throwable) {
                            callback.accept(Snapshot.error("Could not read MTR data: " + describe(throwable)));
                        }
                    },
                    ListDataResponse.class
            );
        } catch (Throwable throwable) {
            callback.accept(Snapshot.error("Could not request MTR data: " + describe(throwable)));
        }
    }

    private static Snapshot build(ListDataResponse response) {
        long now = System.currentTimeMillis();
        List<Station> stations = field(response, "stations");
        List<Route> routes = field(response, "routes");
        List<Siding> sidings = field(response, "sidings");
        List<Depot> depots = field(response, "depots");

        // "Repeat vehicles indefinitely" is a depot flag. Its generated departures repeat at the
        // same instant, so departure gaps become zero and the trip count can reach 100k+. Skip the
        // repeat depot's sidings and mark every route that uses such a depot for the UI.
        Set<String> repeatRouteIds = new HashSet<>();
        Map<String, Set<String>> repeatDepotsByRoute = new LinkedHashMap<>();
        for (Depot depot : depots) {
            if (depot == null || !depot.getRepeatInfinitely()) {
                continue;
            }
            String depotName = depot.getName();
            for (Route depotRoute : depot.routes) {
                if (depotRoute == null) {
                    continue;
                }
                repeatRouteIds.add(depotRoute.getHexId());
                repeatDepotsByRoute
                        .computeIfAbsent(depotRoute.getHexId(), key -> new LinkedHashSet<>())
                        .add(depotName);
            }
        }

        List<Object> stationJson = new ArrayList<>(stations.size());
        for (Station station : stations) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("id", station.getHexId());
            entry.put("name", station.getName());
            entry.put("color", (long) station.getColor());
            entry.put("zone1", station.getZone1());
            entry.put("zone2", station.getZone2());
            entry.put("zone3", station.getZone3());
            stationJson.add(entry);
        }

        List<Object> routeJson = new ArrayList<>(routes.size());
        Set<String> fullyRepeatRouteIds = new HashSet<>();
        for (Route route : routes) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("id", route.getHexId());
            entry.put("name", route.getName());
            entry.put("number", route.getRouteNumber());
            entry.put("type", route.getRouteTypeKey() == null ? "" : route.getRouteTypeKey());
            entry.put("color", (long) route.getColor());

            List<Object> stops = new ArrayList<>();
            for (RoutePlatformData routePlatform : route.getRoutePlatforms()) {
                Platform platform = routePlatform.getPlatform();
                if (platform == null || platform.area == null) {
                    continue;
                }
                Position position = platform.getMidPosition();
                Map<String, Object> stop = new LinkedHashMap<>();
                stop.put("id", platform.area.getHexId());
                stop.put("name", platform.getName());
                stop.put("dwellTime", platform.getDwellTime());
                stop.put("x", position == null ? 0L : position.getX());
                stop.put("y", position == null ? 0L : position.getY());
                stop.put("z", position == null ? 0L : position.getZ());
                stops.add(stop);
            }
            entry.put("stations", stops);

            List<Object> durations = new ArrayList<>(route.durations.size());
            for (int index = 0; index < route.durations.size(); index++) {
                durations.add(Math.max(0L, route.durations.getLong(index)));
            }
            entry.put("durations", durations);

            List<Object> depotNames = new ArrayList<>(route.depots.size());
            for (Depot depot : route.depots) {
                if (depot != null) {
                    depotNames.add(depot.getName());
                }
            }
            entry.put("depots", depotNames);

            Set<String> routeRepeatDepots = new LinkedHashSet<>(
                    repeatDepotsByRoute.getOrDefault(route.getHexId(), Set.of()));
            boolean hasNormalDepot = false;
            for (Depot depot : route.depots) {
                if (depot == null) {
                    continue;
                }
                if (depot.getRepeatInfinitely()) {
                    routeRepeatDepots.add(depot.getName());
                } else {
                    hasNormalDepot = true;
                }
            }
            boolean repeatInfinitely = !routeRepeatDepots.isEmpty()
                    || repeatRouteIds.contains(route.getHexId());
            boolean repeatOnly = repeatInfinitely && !hasNormalDepot;
            if (repeatOnly) {
                fullyRepeatRouteIds.add(route.getHexId());
            }
            entry.put("repeatInfinitely", repeatInfinitely);
            entry.put("repeatOnly", repeatOnly);
            entry.put("repeatDepots", new ArrayList<>(routeRepeatDepots));
            routeJson.add(entry);
        }

        Map<String, Object> networkData = new LinkedHashMap<>();
        networkData.put("stations", stationJson);
        networkData.put("routes", routeJson);
        networkData.put("dimensions", List.of());
        Map<String, Object> networkRoot = new LinkedHashMap<>();
        networkRoot.put("status", 200L);
        networkRoot.put("currentTime", now);
        networkRoot.put("data", networkData);

        Map<String, List<Long>> departuresByRoute = collectDepartures(sidings, now, fullyRepeatRouteIds);
        List<Object> departureEntries = new ArrayList<>(departuresByRoute.size());
        for (Map.Entry<String, List<Long>> entry : departuresByRoute.entrySet()) {
            Map<String, Object> routeEntry = new LinkedHashMap<>();
            routeEntry.put("id", entry.getKey());
            List<Object> deviation = new ArrayList<>();
            Map<String, Object> deviationEntry = new LinkedHashMap<>();
            deviationEntry.put("deviation", 0L);
            deviationEntry.put("departures", entry.getValue());
            deviation.add(deviationEntry);
            routeEntry.put("departures", deviation);
            departureEntries.add(routeEntry);
        }
        Map<String, Object> departureData = new LinkedHashMap<>();
        departureData.put("cachedResponseTime", now);
        departureData.put("departures", departureEntries);
        Map<String, Object> departureRoot = new LinkedHashMap<>();
        departureRoot.put("status", 200L);
        departureRoot.put("currentTime", now);
        departureRoot.put("data", departureData);

        return new Snapshot(
                Json.writeCompact(networkRoot),
                Json.writeCompact(departureRoot),
                null,
                response
        );
    }

    private static Map<String, List<Long>> collectDepartures(
            List<Siding> sidings,
            long now,
            Set<String> fullyExcludedRouteIds
    ) {
        Object2ObjectAVLTreeMap<String, Long2ObjectAVLTreeMap<LongArrayList>> departures =
                new Object2ObjectAVLTreeMap<>();
        for (Siding siding : sidings) {
            // Siding.area is the depot that owns this siding. A depot with "repeat vehicles
            // indefinitely" generates an enormous repeated departure list; skip it entirely.
            if (siding.area != null && siding.area.getRepeatInfinitely()) {
                continue;
            }
            try {
                siding.getDepartures(now, departures);
            } catch (Throwable ignored) {
            }
        }
        departures.keySet().removeAll(fullyExcludedRouteIds);
        Map<String, List<Long>> result = new LinkedHashMap<>();
        for (Map.Entry<String, Long2ObjectAVLTreeMap<LongArrayList>> routeEntry : departures.entrySet()) {
            List<Long> times = new ArrayList<>();
            for (var deviationEntry : routeEntry.getValue().long2ObjectEntrySet()) {
                LongArrayList routeTimes = deviationEntry.getValue();
                for (int index = 0; index < routeTimes.size(); index++) {
                    times.add(routeTimes.getLong(index));
                }
            }
            if (!times.isEmpty()) {
                times.sort(Long::compareTo);
                result.put(routeEntry.getKey(), times);
            }
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private static <T> List<T> field(ListDataResponse response, String name) {
        try {
            Field field = ListDataResponseSchema.class.getDeclaredField(name);
            field.setAccessible(true);
            return (List<T>) field.get(response);
        } catch (ReflectiveOperationException | RuntimeException exception) {
            throw new IllegalStateException("MTR list_data response has no '" + name + "' collection", exception);
        }
    }

    private static String describe(Throwable throwable) {
        String message = throwable.getMessage();
        return message == null || message.isBlank() ? throwable.getClass().getSimpleName() : message;
    }
}
