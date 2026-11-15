package cn.myfrank.stationbuilder.scheduler;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Immutable snapshot of one MTR dimension, built exclusively from MTR's read-only in-game data (`list_data`).
 *
 * <p>Two endpoints are merged:</p>
 * <p>The server-side collector converts MTR's stations, routes, stops, run times, depots and
 * departures into this compact envelope before sending it to the client.</p>
 *
 * <p>Both the MTR 4.x envelope ({@code status/text/currentTime/data}) and the older
 * {@code code/data} envelope are accepted, and a number of alternative field names are tolerated,
 * because the exact payload has changed between MTR releases. The parser never trusts the shape
 * of the document; anything unexpected is skipped and reported through {@link #warnings()}.</p>
 */
public final class MtrNetwork {

    public final int dimension;
    public final long fetchedAt;
    public final long serverTime;
    public long cachedResponseTime;
    public final List<String> dimensions;
    public final List<MtrStation> stations;
    public final List<MtrRoute> routes;

    private final Map<String, MtrStation> stationsById;
    private final Map<String, MtrRoute> routesById;
    private final List<String> warnings = new ArrayList<>();
    private final Map<String, List<String>> platformsByStation;
    /** Raw departure epoch times per route, kept so the start-time interpretation can be revised. */
    private final Map<String, List<Long>> rawDepartureEpochs = new LinkedHashMap<>();

    private MtrNetwork(
            int dimension,
            long fetchedAt,
            long serverTime,
            long cachedResponseTime,
            List<String> dimensions,
            List<MtrStation> stations,
            List<MtrRoute> routes
    ) {
        this.dimension = dimension;
        this.fetchedAt = fetchedAt;
        this.serverTime = serverTime;
        this.cachedResponseTime = cachedResponseTime;
        this.dimensions = List.copyOf(dimensions);
        this.stations = List.copyOf(stations);
        this.routes = List.copyOf(routes);

        Map<String, MtrStation> stationIndex = new LinkedHashMap<>();
        for (MtrStation station : stations) {
            stationIndex.put(station.id, station);
        }
        this.stationsById = Collections.unmodifiableMap(stationIndex);

        Map<String, MtrRoute> routeIndex = new LinkedHashMap<>();
        for (MtrRoute route : routes) {
            routeIndex.put(route.id, route);
        }
        this.routesById = Collections.unmodifiableMap(routeIndex);

        Map<String, LinkedHashSet<String>> platforms = new LinkedHashMap<>();
        for (MtrRoute route : routes) {
            for (MtrStop stop : route.stops) {
                platforms.computeIfAbsent(stop.stationId, key -> new LinkedHashSet<>()).add(stop.platformName);
            }
        }
        Map<String, List<String>> frozenPlatforms = new LinkedHashMap<>();
        platforms.forEach((stationId, names) -> frozenPlatforms.put(stationId, List.copyOf(names)));
        this.platformsByStation = Collections.unmodifiableMap(frozenPlatforms);
    }

    public MtrStation station(String id) {
        return id == null ? null : stationsById.get(id);
    }

    public MtrRoute route(String id) {
        return id == null ? null : routesById.get(id);
    }

    public String stationName(String id) {
        MtrStation station = station(id);
        return station == null ? id : station.name;
    }

    /** Platform names in the order they first appear across routes. */
    public List<String> platformsOf(String stationId) {
        List<String> platforms = platformsByStation.get(stationId);
        return platforms == null ? List.of() : platforms;
    }

    /** Station ids that have at least one platform used by a route. */
    public List<String> stationIdsWithPlatforms() {
        return new ArrayList<>(platformsByStation.keySet());
    }

    /**
     * True when {@code route} is marked as following another route that terminates on the same
     * station <em>and</em> platform the route starts from. Only then does the first stop represent
     * the same physical train as the preceding route's last stop, which is what makes it safe to
     * de-duplicate the occupancy.
     */
    public boolean isPlatformContinuation(Map<String, String> follows, MtrRoute route) {
        String precedingId = follows.get(route.id);
        if (precedingId == null) {
            return false;
        }
        MtrRoute preceding = route(precedingId);
        if (preceding == null || preceding.stops.isEmpty() || route.stops.isEmpty()) {
            return false;
        }
        MtrStop last = preceding.stops.get(preceding.stops.size() - 1);
        MtrStop first = route.stops.get(0);
        return last.stationId.equals(first.stationId) && last.platformName.equals(first.platformName);
    }

    public List<String> warnings() {
        return Collections.unmodifiableList(warnings);
    }

    /** Adds a warning discovered after parsing (for example a failed secondary endpoint). */
    public void addWarning(String warning) {
        if (warning != null && !warning.isBlank()) {
            warnings.add(warning);
        }
    }

    // ------------------------------------------------------------------
    // Parsing
    // ------------------------------------------------------------------

    /**
     * Builds a network snapshot from the raw bodies of the two endpoints.
     *
     * @param stationsAndRoutesBody raw JSON of {@code stations-and-routes}; must not be {@code null}
     * @param departuresBody        raw JSON of {@code departures}, or {@code null} when that call failed
     */
    public static MtrNetwork parse(
            String stationsAndRoutesBody,
            String departuresBody,
            int dimension,
            long fetchedAt
    ) {
        List<String> warnings = new ArrayList<>();

        Map<String, Object> root = unwrap(stationsAndRoutesBody, "stations-and-routes");
        Object dataValue = root.get("data");
        Map<String, Object> data = dataValue instanceof String text
                ? Json.object(Json.parse(text))
                : Json.object(dataValue == null ? root : dataValue);

        long serverTime = Json.longValue(Json.first(root, "currentTime", "serverTime", "cachedResponseTime"),
                fetchedAt);
        long cachedResponseTime = fetchedAt;

        List<String> dimensions = new ArrayList<>();
        for (Object value : Json.array(Json.first(data, "dimensions", "dimensionNames"))) {
            String name = Json.string(value, null);
            if (name != null) {
                dimensions.add(name);
            }
        }

        List<MtrStation> stations = new ArrayList<>();
        for (Object value : Json.array(Json.first(data, "stations", "stationList"))) {
            Map<String, Object> stationObject = Json.object(value);
            String id = Json.string(Json.first(stationObject, "id", "stationId", "hexId"), null);
            if (id == null || id.isEmpty()) {
                continue;
            }
            stations.add(new MtrStation(
                    id,
                    Json.string(Json.first(stationObject, "name", "stationName"), id),
                    Json.intValue(Json.first(stationObject, "color", "colour"), 0xFFFFFF),
                    Json.intValue(Json.first(stationObject, "zone1", "zone1Color"), 0),
                    Json.intValue(Json.first(stationObject, "zone2", "zone2Color"), 0),
                    Json.intValue(Json.first(stationObject, "zone3", "zone3Color"), 0)
            ));
        }

        List<MtrRoute> routes = new ArrayList<>();
        for (Object value : Json.array(Json.first(data, "routes", "routeList"))) {
            Map<String, Object> routeObject = Json.object(value);
            String id = Json.string(Json.first(routeObject, "id", "routeId", "hexId"), null);
            if (id == null || id.isEmpty()) {
                continue;
            }
            String name = Json.string(Json.first(routeObject, "name", "routeName"), id);
            String number = Json.string(Json.first(routeObject, "number", "routeNumber"), "");
            String type = Json.string(Json.first(routeObject, "type", "routeType", "transportMode"), "");

            List<MtrStop> stops = new ArrayList<>();
            Object stopsValue = Json.first(routeObject, "stations", "platforms", "routeStations", "stops");
            for (Object stopValue : Json.array(stopsValue)) {
                Map<String, Object> stopObject = Json.object(stopValue);
                String stationId = Json.string(
                        Json.first(stopObject, "id", "stationId", "station", "stationHexId"), "");
                String platformName = Json.string(
                        Json.first(stopObject, "name", "platformName", "platform", "platformId"), "");
                stops.add(new MtrStop(
                        stationId,
                        platformName,
                        Json.longValue(Json.first(stopObject, "dwellTime", "dwell", "dwellMillis"), 0L),
                        Json.intValue(Json.first(stopObject, "x", "posX"), 0),
                        Json.intValue(Json.first(stopObject, "y", "posY"), 0),
                        Json.intValue(Json.first(stopObject, "z", "posZ"), 0)
                ));
            }

            List<Long> durations = new ArrayList<>();
            for (Object durationValue : Json.array(Json.first(routeObject, "durations", "travelTimes", "runTimes"))) {
                durations.add(Math.max(0L, Json.longValue(durationValue, 0L)));
            }
            if (!durations.isEmpty() && durations.size() < stops.size() - 1) {
                warnings.add("route " + name + " reports " + durations.size() + " run times for "
                        + stops.size() + " stops");
            }

            List<String> depots = new ArrayList<>();
            for (Object depotValue : Json.array(Json.first(routeObject, "depots", "depotNames"))) {
                String depot = depotValue instanceof Map<?, ?> depotObject
                        ? Json.string(Json.first(depotObject, "name", "depotName"), null)
                        : Json.string(depotValue, null);
                if (depot != null && !depot.isEmpty()) {
                    depots.add(depot);
                }
            }

            List<String> repeatDepots = new ArrayList<>();
            for (Object depotValue : Json.array(Json.first(routeObject, "repeatDepots", "ignoredDepots"))) {
                String depot = Json.string(depotValue, null);
                if (depot != null && !depot.isEmpty()) {
                    repeatDepots.add(depot);
                }
            }
            boolean repeatInfinitely = Json.booleanValue(
                    Json.first(routeObject, "repeatInfinitely", "repeatVehiclesIndefinitely"),
                    false
            );
            boolean repeatOnly = Json.booleanValue(
                    Json.first(routeObject, "repeatOnly", "allDepotsRepeat"),
                    false
            );
            if (repeatInfinitely) {
                warnings.add("route " + name
                        + ": a depot repeats vehicle instructions indefinitely; its departures are excluded");
            }

            routes.add(new MtrRoute(
                    id,
                    name,
                    number,
                    type,
                    Json.intValue(Json.first(routeObject, "color", "colour"), 0xFFFFFF),
                    stops,
                    durations,
                    depots,
                    repeatInfinitely,
                    repeatDepots,
                    repeatOnly
            ));
        }

        MtrNetwork network = new MtrNetwork(
                dimension,
                fetchedAt,
                serverTime,
                cachedResponseTime,
                dimensions,
                stations,
                routes
        );
        network.warnings.addAll(warnings);

        if (departuresBody != null && !departuresBody.isBlank()) {
            network.applyDepartures(departuresBody);
        } else {
            network.warnings.add("departures endpoint unavailable; departure times must be entered manually");
        }

        return network;
    }

    /**
     * Fills {@link MtrRoute#setStartTimes(Collection)} for every route from the departures payload.
     *
     * <p>MTR reports departures as milliseconds from {@code cachedResponseTime}; the planner
     * converts them into the departure time at the first platform by subtracting the route run
     * time, exactly like the original web planner did.</p>
     */
    private void applyDepartures(String body) {
        try {
            applyDeparturesUnchecked(body);
        } catch (RuntimeException e) {
            warnings.add("could not read departures payload: " + e.getMessage());
        }
    }

    private void applyDeparturesUnchecked(String body) {
        Map<String, Object> root;
        try {
            root = unwrap(body, "departures");
        } catch (RuntimeException e) {
            warnings.add("could not read departures payload: " + e.getMessage());
            return;
        }
        Object dataValue = root.get("data");
        Map<String, Object> data = dataValue instanceof String text
                ? Json.object(Json.parse(text))
                : Json.object(dataValue == null ? root : dataValue);

        long cached = Json.longValue(
                Json.first(data, "cachedResponseTime", "currentTime", "responseTime"),
                Json.longValue(Json.first(root, "currentTime"), fetchedAt)
        );
        this.cachedResponseTime = cached;

        Map<String, List<Long>> offsetsByRoute = new LinkedHashMap<>();
        collectDepartures(Json.first(data, "departures", "routes", "departuresByRoute"), null, offsetsByRoute);

        int matched = 0;
        for (Map.Entry<String, List<Long>> entry : offsetsByRoute.entrySet()) {
            MtrRoute route = route(entry.getKey());
            if (route == null) {
                route = routeFindIgnoreCase(entry.getKey());
            }
            if (route == null) {
                continue;
            }
            if (route.repeatOnly) {
                route.markDeparturesIgnored("every depot of this route repeats vehicle instructions indefinitely");
                warnings.add("route " + route.label()
                        + ": all depots repeat vehicle instructions indefinitely; departures are excluded");
                continue;
            }
            if (isSuspiciousDepartureSet(entry.getValue())) {
                route.markDeparturesIgnored(
                        "abnormal departure count; the depot may repeat vehicle instructions indefinitely");
                warnings.add("route " + route.label()
                        + ": abnormal departure data ignored (possible permanent repeat instruction)");
                continue;
            }
            matched++;
            List<Long> epochs = rawDepartureEpochs.computeIfAbsent(route.id, key -> new ArrayList<>());
            for (Long offset : entry.getValue()) {
                if (offset == null) {
                    continue;
                }
                epochs.add(Math.abs(offset) > 1_000_000_000_000L ? offset : cached + offset);
            }
        }
        // Initially assume every route is reported at its last platform; the session revises this
        // after continuation relations are known.
        deriveStartTimes(Map.of());

        if (!offsetsByRoute.isEmpty() && matched == 0) {
            warnings.add("departures payload contains route ids that do not match any route");
        }
        if (offsetsByRoute.isEmpty()) {
            warnings.add("departures payload contains no departures");
        }
    }


    /**
     * Guards against a depot with "repeat vehicles indefinitely". Such a depot can generate
     * 100k+ departures at identical times, which would otherwise collapse the headway to zero.
     * The server already skips repeat-depot sidings; this is a second line of defence for older
     * snapshots or unusual MTR builds.
     */
    private static boolean isSuspiciousDepartureSet(List<Long> offsets) {
        if (offsets == null || offsets.isEmpty()) {
            return false;
        }
        if (offsets.size() > 100_000) {
            return true;
        }
        if (offsets.size() < 200) {
            return false;
        }
        List<Long> sorted = new ArrayList<>(offsets);
        sorted.sort(Long::compareTo);
        int duplicates = 0;
        for (int index = 1; index < sorted.size(); index++) {
            if (sorted.get(index).equals(sorted.get(index - 1))) {
                duplicates++;
            }
        }
        return duplicates > sorted.size() / 2;
    }

    /**
     * Recomputes route start times from the raw MTR departures.
     *
     * <p>MTR's data reports a departure either at the route's <em>last</em> platform or, when a
     * depot chains two routes at the same platform, at the <em>first</em> platform of the next route
     * (see {@code Siding.getDeparturesAtEndOfRoute}). Assuming the last-platform case for every
     * route makes those chained routes appear shifted by the whole route run time. Routes that are
     * known (or mutually detected) continuations are therefore interpreted as first-platform
     * departures, which only requires subtracting the first dwell instead of the full duration.</p>
     */
    public void deriveStartTimes(Map<String, String> follows) {
        for (MtrRoute route : routes) {
            List<Long> epochs = rawDepartureEpochs.get(route.id);
            if (epochs == null || epochs.isEmpty()) {
                continue;
            }
            boolean firstPlatformReport = (follows != null && follows.containsKey(route.id))
                    || hasMutualContinuation(route);
            long offset = firstPlatformReport ? route.firstDwellMs() : route.durationMs();
            List<Long> starts = new ArrayList<>(epochs.size());
            for (Long epoch : epochs) {
                if (epoch == null) {
                    continue;
                }
                starts.add(TimeFormat.floorModDay(TimeFormat.localDayMillis(epoch) - offset));
            }
            route.setStartTimes(starts);
        }
    }

    /**
     * True when another route starts where this route ends and ends where this route starts on the
     * same platform, i.e. a depot loop (out-and-back) in which MTR reports both routes at their
     * first platform.
     */
    private boolean hasMutualContinuation(MtrRoute route) {
        if (route.stops.size() < 2) {
            return false;
        }
        for (MtrRoute other : routes) {
            if (other == route || other.stops.size() != route.stops.size()) {
                continue;
            }
            if (!shareDepot(route, other)) {
                continue;
            }
            boolean reversed = true;
            for (int index = 0; index < route.stops.size(); index++) {
                MtrStop left = route.stops.get(index);
                MtrStop right = other.stops.get(other.stops.size() - 1 - index);
                if (!samePlatform(left, right)) {
                    reversed = false;
                    break;
                }
            }
            if (reversed) {
                return true;
            }
        }
        return false;
    }

    private static boolean samePlatform(MtrStop left, MtrStop right) {
        return left.stationId.equals(right.stationId) && left.platformName.equals(right.platformName);
    }

    private static boolean shareDepot(MtrRoute left, MtrRoute right) {
        if (left.depots.isEmpty() || right.depots.isEmpty()) {
            return left.depots.isEmpty() && right.depots.isEmpty();
        }
        for (String depot : left.depots) {
            if (right.depots.contains(depot)) {
                return true;
            }
        }
        return false;
    }

    private MtrRoute routeFindIgnoreCase(String id) {
        for (MtrRoute route : routes) {
            if (route.id.equalsIgnoreCase(id)) {
                return route;
            }
        }
        return null;
    }

    /**
     * Recursively walks the departures payload, tolerating both the documented array form
     * ({@code [{id, departures:[{deviation, departures:[ms]}]}]}) and a map form
     * ({@code {routeId: {platformId: [ms]}}}).
     */
    private static void collectDepartures(Object node, String routeId, Map<String, List<Long>> out) {
        if (node == null) {
            return;
        }
        if (node instanceof List<?> list) {
            for (Object element : list) {
                if (element instanceof Number number) {
                    if (routeId != null) {
                        out.computeIfAbsent(routeId, key -> new ArrayList<>()).add(number.longValue());
                    }
                } else {
                    collectDepartures(element, routeId, out);
                }
            }
            return;
        }
        if (node instanceof Map<?, ?> rawMap) {
            Map<String, Object> map = Json.object(rawMap);
            String id = Json.string(Json.first(map, "id", "routeId", "route", "routeHexId"), routeId);
            boolean nested = false;
            for (String key : new String[]{"departures", "times", "departureTimes", "schedules"}) {
                if (map.containsKey(key)) {
                    collectDepartures(map.get(key), id, out);
                    nested = true;
                }
            }
            if (nested) {
                return;
            }
            if (routeId == null) {
                // Top level map keyed by route id.
                for (Map.Entry<String, Object> entry : map.entrySet()) {
                    String key = entry.getKey();
                    if (key.equals("cachedResponseTime") || key.equals("currentTime")) {
                        continue;
                    }
                    collectDepartures(entry.getValue(), key, out);
                }
            } else {
                for (Object value : map.values()) {
                    collectDepartures(value, routeId, out);
                }
            }
            return;
        }
        if (node instanceof String text) {
            String trimmed = text.trim();
            if (!trimmed.isEmpty()) {
                try {
                    collectDepartures(Json.parse(trimmed), routeId, out);
                } catch (RuntimeException ignored) {
                    // Not JSON: ignore the entry.
                }
            }
        }
    }

    /** Validates the envelope, returning the object that carries {@code data}. */
    private static Map<String, Object> unwrap(String body, String label) {
        Object parsed = Json.parse(body);
        Map<String, Object> root = Json.object(parsed);
        if (root.isEmpty() && !(parsed instanceof Map<?, ?>)) {
            throw new Json.ParseException(label + " payload is not a JSON object");
        }
        long status = Json.longValue(Json.first(root, "status", "code", "statusCode"), 200L);
        if (status != 200L) {
            String text = Json.string(Json.first(root, "text", "message", "error"), "");
            throw new Json.ParseException(label + " endpoint returned status " + status
                    + (text.isEmpty() ? "" : " (" + text + ")"));
        }
        return root;
    }

    /** Groups the occupied platforms of a station, used by the timetable tab. */
    public Map<String, List<MtrRoute>> routesByPlatform(String stationId) {
        Map<String, List<MtrRoute>> result = new LinkedHashMap<>();
        for (MtrRoute route : routes) {
            Set<String> seen = new LinkedHashSet<>();
            for (MtrStop stop : route.stops) {
                if (stop.stationId.equals(stationId) && seen.add(stop.platformName)) {
                    result.computeIfAbsent(stop.platformName, key -> new ArrayList<>()).add(route);
                }
            }
        }
        return result;
    }

    /** Number of platforms in the snapshot. */
    public int platformCount() {
        int platforms = 0;
        for (List<String> names : platformsByStation.values()) {
            platforms += names.size();
        }
        return platforms;
    }

    /** Number of trips currently loaded in the snapshot. */
    public int tripCount() {
        int trips = 0;
        for (MtrRoute route : routes) {
            trips += route.startTimes().size();
        }
        return trips;
    }

    /** Number of distinct stations / platforms / trains in the snapshot, for the status line. */
    public String summary() {
        int platforms = 0;
        for (List<String> names : platformsByStation.values()) {
            platforms += names.size();
        }
        int trips = 0;
        for (MtrRoute route : routes) {
            trips += route.startTimes().size();
        }
        return stations.size() + " stations / " + platforms + " platforms / "
                + routes.size() + " routes / " + trips + " trips";
    }
}
