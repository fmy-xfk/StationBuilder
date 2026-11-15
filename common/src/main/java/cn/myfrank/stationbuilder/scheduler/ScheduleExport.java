package cn.myfrank.stationbuilder.scheduler;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Text exports produced by the in-game planner.
 *
 * <p>Exports are copy-to-clipboard only: the planner never writes to MTR. The depot plan follows
 * MTR's real-time schedule syntax ({@code HH:MM:SS + count*HH:MM:SS}), which can be pasted into a
 * depot's schedule field by the player.</p>
 */
public final class ScheduleExport {

    private ScheduleExport() {
    }

    /** Human-readable depot plan for manual entry into MTR depots. */
    public static String depotPlan(
            MtrNetwork network,
            String host,
            int dimension,
            long windowStartMs,
            long windowEndMs,
            Map<String, String> follows
    ) {
        StringBuilder out = new StringBuilder();
        out.append("# StationBuilder MTR schedule export\n");
        out.append("# Read-only planner: no MTR data has been modified.\n");
        out.append("# Server: ").append(MtrApiClient.normalizeBase(host))
                .append(" (dimension ").append(dimension).append(")\n");
        out.append("# Window: ").append(TimeFormat.hms(windowStartMs))
                .append(" - ").append(TimeFormat.hms(windowEndMs)).append('\n');
        out.append("# Paste a route line into the depot's real time schedule field, for example:\n");
        out.append("#   06:00:00 + 47*00:05:00\n");
        out.append('\n');

        Map<String, List<MtrRoute>> routesByDepot = new LinkedHashMap<>();
        for (MtrRoute route : network.routes) {
            String depot = route.primaryDepot();
            routesByDepot.computeIfAbsent(depot == null ? "(no depot)" : depot, key -> new ArrayList<>())
                    .add(route);
        }
        List<String> depotNames = new ArrayList<>(routesByDepot.keySet());
        depotNames.sort(String.CASE_INSENSITIVE_ORDER);

        for (String depot : depotNames) {
            out.append("[Depot] ").append(depot).append('\n');
            List<MtrRoute> routes = routesByDepot.get(depot);
            routes.sort(Comparator.comparing(route -> route.label()));
            for (MtrRoute route : routes) {
                out.append("  ").append(route.label()).append(": ")
                        .append(formatDepartures(route.startTimes()))
                        .append('\n');
                String followed = follows.get(route.id);
                if (followed != null) {
                    MtrRoute preceding = network.route(followed);
                    out.append("      follows ")
                            .append(preceding == null ? followed : preceding.label())
                            .append(" (departures derived from its last arrival)\n");
                }
            }
            out.append('\n');
        }
        if (depotNames.isEmpty()) {
            out.append("(no routes loaded)\n");
        }
        return out.toString();
    }

    /** Formats departure times of day either as a repeating pattern or as an explicit list. */
    public static String formatDepartures(List<Long> departuresMs) {
        if (departuresMs.isEmpty()) {
            return "(no departures)";
        }
        List<Long> sorted = new ArrayList<>();
        for (Long departure : departuresMs) {
            sorted.add(TimeFormat.floorModDay(departure));
        }
        sorted.sort(Long::compareTo);

        if (sorted.size() >= 2) {
            long interval = sorted.get(1) - sorted.get(0);
            boolean regular = interval > 0L;
            for (int index = 2; index < sorted.size(); index++) {
                if (sorted.get(index) - sorted.get(index - 1) != interval) {
                    regular = false;
                    break;
                }
            }
            if (regular) {
                return TimeFormat.hms(sorted.get(0)) + " + " + (sorted.size() - 1)
                        + "*" + TimeFormat.duration(interval);
            }
        }
        if (sorted.size() == 1) {
            return TimeFormat.hms(sorted.get(0));
        }
        StringBuilder out = new StringBuilder();
        for (int index = 0; index < sorted.size(); index++) {
            if (index > 0) {
                out.append("; ");
            }
            out.append(TimeFormat.hms(sorted.get(index)));
        }
        return out.toString();
    }

    /** Complete, versioned project snapshot for external editing or future import support. */
    public static String projectJson(
            MtrNetwork network,
            String host,
            int dimension,
            long windowStartMs,
            long windowEndMs,
            Map<String, String> follows
    ) {
        Map<String, Object> project = new LinkedHashMap<>();
        project.put("format", "stationbuilder-mtr-schedule");
        project.put("version", 1L);
        // Deterministic timestamps: the snapshot time of the loaded MTR data rather than "now", so
        // repeatedly previewing the export does not produce a different document every frame.
        project.put("snapshotTime", network.fetchedAt);
        project.put("serverTime", network.serverTime);
        project.put("server", MtrApiClient.normalizeBase(host));
        project.put("dimension", (long) dimension);
        project.put("windowStartMs", windowStartMs);
        project.put("windowEndMs", windowEndMs);
        project.put("note", "Generated by the read-only in-game planner; MTR data is not modified.");

        Map<String, Object> followMap = new LinkedHashMap<>();
        follows.forEach((to, from) -> followMap.put(to, from));
        project.put("follows", followMap);

        List<Object> routes = new ArrayList<>();
        for (MtrRoute route : network.routes) {
            Map<String, Object> routeObject = new LinkedHashMap<>();
            routeObject.put("id", route.id);
            routeObject.put("name", route.name);
            routeObject.put("number", route.number);
            routeObject.put("type", route.type);
            routeObject.put("color", (long) route.color);
            routeObject.put("depots", route.depots);

            List<Object> stops = new ArrayList<>();
            for (MtrStop stop : route.stops) {
                Map<String, Object> stopObject = new LinkedHashMap<>();
                stopObject.put("stationId", stop.stationId);
                stopObject.put("platform", stop.platformName);
                stopObject.put("dwellMs", stop.dwellMs);
                stops.add(stopObject);
            }
            routeObject.put("stops", stops);
            routeObject.put("durationsMs", route.durations);
            routeObject.put("departuresMs", route.startTimes());
            routes.add(routeObject);
        }
        project.put("routes", routes);
        return Json.write(project);
    }

}
