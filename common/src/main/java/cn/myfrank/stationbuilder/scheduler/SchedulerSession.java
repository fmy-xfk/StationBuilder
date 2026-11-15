package cn.myfrank.stationbuilder.scheduler;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Client-side state of the in-game MTR schedule planner.
 *
 * <p>Everything in this class is a snapshot obtained from MTR's in-game data; the planner is strictly
 * read from MTR with the read-only {@code list_data} operation. Departure edits and follow relations
 * only exist in this session; saving offsets writes them to MTR's depot real-time schedules.</p>
 *
 * <p>The instance is shared by all screens so closing and reopening the planner keeps the loaded
 * network, the analysis window and the user's edits.</p>
 */
public final class SchedulerSession {

    public enum LoadState {
        IDLE,
        LOADING,
        READY,
        ERROR
    }

    /**
     * Loader-specific bridge that asks the server for an MTR snapshot. The server answers through
     * {@link #acceptSnapshot(int, String, String)} or {@link #acceptError(int, String)} with the same token.
     */
    public interface Transport {
        void request(int token);

        void write(int token, Map<String, Long> offsets);
    }

    private static final SchedulerSession INSTANCE = new SchedulerSession();

    /** Longest layover still considered a continuation when auto-detecting follow relations. */
    private static final long AUTO_FOLLOW_MAX_TURNAROUND_MS = 2L * TimeFormat.HOUR_MS;
    private static final long REQUEST_TIMEOUT_MS = 15_000L;

    private final Object lock = new Object();

    private volatile Transport transport;
    private LoadState state = LoadState.IDLE;
    private String status = "";
    private MtrNetwork network;
    private int generation;
    private int modCount;
    private long loadingSince;
    private boolean pendingWrite;

    private final Map<String, String> follows = new LinkedHashMap<>();
    /** Route ids whose follow relation was created explicitly by the player and derives its times. */
    private final Set<String> manualFollows = new HashSet<>();
    /** Route ids whose follow relation was detected automatically from a shared terminal platform. */
    private final Set<String> autoFollows = new HashSet<>();
    /** Route ids the player deliberately un-followed; auto-detection must not re-add them. */
    private final Set<String> suppressedAutoFollows = new HashSet<>();
    private String selectedRouteId;
    private String selectedStationId;

    private long windowStartMs = 6L * TimeFormat.HOUR_MS;
    private long windowEndMs = 7L * TimeFormat.HOUR_MS;
    private boolean windowCustomized;
    private boolean introDismissed;

    private SchedulerAnalysis analysis;
    private List<MtrRoute> sortedRoutes = List.of();
    private List<String> sortedStationIds = List.of();

    private SchedulerSession() {
    }

    public static SchedulerSession get() {
        return INSTANCE;
    }

    // ------------------------------------------------------------------
    // Loading
    // ------------------------------------------------------------------

    /** Loads the network the first time the screen is opened, or after a failure. */
    public void ensureLoaded() {
        synchronized (lock) {
            if (state == LoadState.IDLE) {
                refresh();
            }
        }
    }

    /**
     * Asks the server for a fresh MTR snapshot through the loader-specific {@link Transport}. The
     * response is delivered on the client thread through {@link #acceptSnapshot} /
     * {@link #acceptError}, or fails after {@link #REQUEST_TIMEOUT_MS}.
     */
    public void refresh() {
        final Transport currentTransport;
        final int token;
        synchronized (lock) {
            currentTransport = transport;
            token = ++generation;
            state = LoadState.LOADING;
            loadingSince = System.currentTimeMillis();
            status = "Requesting MTR data from the server…";
            modCount++;
        }

        if (currentTransport == null) {
            synchronized (lock) {
                if (token == generation) {
                    state = LoadState.ERROR;
                    status = "MTR data transport is not available";
                    modCount++;
                }
            }
            return;
        }

        try {
            currentTransport.request(token);
        } catch (Throwable throwable) {
            acceptError(token, "Could not request MTR data: " + throwable.getMessage());
        }
    }

    /** Called by the client networking layer with the server-provided snapshot. */
    public void acceptSnapshot(int token, String stationsAndRoutesJson, String departuresJson) {
        synchronized (lock) {
            if (token != generation || state != LoadState.LOADING) {
                return;
            }
            try {
                MtrNetwork loaded = MtrNetwork.parse(
                        stationsAndRoutesJson,
                        departuresJson,
                        0,
                        System.currentTimeMillis()
                );
                adoptNetwork(loaded);
                state = LoadState.READY;
                int warnings = loaded.warnings().size();
                status = (pendingWrite ? "Saved to MTR · " : "")
                        + loaded.summary()
                        + (warnings == 0 ? "" : " · " + warnings + " warning(s)")
                        + " · " + TimeFormat.hms(TimeFormat.localDayMillis(loaded.fetchedAt));
                pendingWrite = false;
            } catch (RuntimeException exception) {
                state = LoadState.ERROR;
                status = "Could not read MTR data: " + exception.getMessage();
                pendingWrite = false;
            }
            modCount++;
        }
    }

    /** Called by the client networking layer when the server cannot provide a snapshot. */
    public void acceptError(int token, String message) {
        synchronized (lock) {
            if (token != generation || state != LoadState.LOADING) {
                return;
            }
            state = LoadState.ERROR;
            status = message == null || message.isBlank() ? "Could not read MTR data" : message;
            pendingWrite = false;
            modCount++;
        }
    }

    /** Called from the screen tick to surface a silent server-side failure. */
    public void poll() {
        synchronized (lock) {
            if (state == LoadState.LOADING && System.currentTimeMillis() - loadingSince > REQUEST_TIMEOUT_MS) {
                state = LoadState.ERROR;
                status = "Timed out waiting for MTR data from the server";
                pendingWrite = false;
                generation++;
                modCount++;
            }
        }
    }

    public void setTransport(Transport value) {
        synchronized (lock) {
            transport = value;
        }
    }

    /** Writes the current depot offsets into MTR and refreshes the routes afterwards. */
    public void writeOffsets() {        SchedulerAnalysis current = analysis();
        if (!current.solvable()) {
            return;
        }
        Map<String, Long> offsets = new LinkedHashMap<>(current.depotOffsetsMs());
        final Transport currentTransport;
        final int token;
        synchronized (lock) {
            currentTransport = transport;
            token = ++generation;
            state = LoadState.LOADING;
            loadingSince = System.currentTimeMillis();
            pendingWrite = true;
            status = "Writing depot offsets to MTR…";
            modCount++;
        }
        if (currentTransport == null) {
            acceptError(token, "MTR data transport is not available");
            return;
        }
        try {
            currentTransport.write(token, offsets);
        } catch (Throwable throwable) {
            acceptError(token, "Could not write to MTR: " + throwable.getMessage());
        }
    }

    /** Top-bar shortcut: save the current planner offsets to the MTR depot schedules. */
    public void writeCurrentSchedule() {
        writeOffsets();
    }

    private void adoptNetwork(MtrNetwork loaded) {
        network = loaded;
        analysis = null;

        follows.keySet().removeIf(routeId -> loaded.route(routeId) == null
                || loaded.route(follows.get(routeId)) == null);
        manualFollows.retainAll(follows.keySet());
        autoFollows.retainAll(follows.keySet());
        suppressedAutoFollows.removeIf(routeId -> loaded.route(routeId) == null);

        List<MtrRoute> routes = new ArrayList<>(loaded.routes);
        routes.sort((left, right) -> left.label().compareToIgnoreCase(right.label()));
        sortedRoutes = Collections.unmodifiableList(routes);

        List<String> stationIds = new ArrayList<>(loaded.stationIdsWithPlatforms());
        stationIds.sort((left, right) -> loaded.stationName(left)
                .compareToIgnoreCase(loaded.stationName(right)));
        sortedStationIds = Collections.unmodifiableList(stationIds);

        if (selectedRouteId == null || loaded.route(selectedRouteId) == null) {
            selectedRouteId = routes.isEmpty() ? null : routes.get(0).id;
        }
        if (selectedStationId == null || !stationIds.contains(selectedStationId)) {
            selectedStationId = stationIds.isEmpty() ? null : stationIds.get(0);
        }

        if (!windowCustomized) {
            // Default analysis window requested by the UI: 06:00 - 07:00 of the operating day.
            windowStartMs = 6L * TimeFormat.HOUR_MS;
            windowEndMs = 7L * TimeFormat.HOUR_MS;
        }

        // Re-detect automatic continuations, then only cascade times for explicit follow relations.
        for (String routeId : new ArrayList<>(autoFollows)) {
            follows.remove(routeId);
        }
        autoFollows.clear();
        for (MtrRoute route : routes) {
            if (follows.containsKey(route.id) || suppressedAutoFollows.contains(route.id)) {
                continue;
            }
            List<MtrRoute> candidates = followCandidates(route.id);
            MtrRoute chosen = chooseAutoFollowCandidate(route, candidates);
            if (chosen != null) {
                follows.put(route.id, chosen.id);
                autoFollows.add(route.id);
            }
        }
        // Re-interpret the raw departures now that continuations are known, then cascade only the
        // explicitly followed routes (automatic ones keep MTR's reported times).
        loaded.deriveStartTimes(follows);
        cascadeFollows();
    }

    /**
     * Picks the predecessor whose last arrival best matches the follower's first departure. This
     * resolves the common ambiguity of two routes that terminate on each other's first platform: the
     * old web planner always took the first geometric match, which could pick the wrong direction and
     * leave a phantom conflict at the turnaround platform.
     */
    private static MtrRoute chooseAutoFollowCandidate(MtrRoute follower, List<MtrRoute> candidates) {
        if (candidates.isEmpty()) {
            return null;
        }
        MtrRoute best = null;
        long bestGap = Long.MAX_VALUE;
        boolean anyTimeData = false;
        for (MtrRoute candidate : candidates) {
            long gap = turnaroundGap(follower, candidate);
            if (gap >= 0L) {
                anyTimeData = true;
                if (gap <= AUTO_FOLLOW_MAX_TURNAROUND_MS && gap < bestGap) {
                    best = candidate;
                    bestGap = gap;
                }
            }
        }
        if (best != null) {
            return best;
        }
        // With departure data available but no plausible layover, the two routes simply are not a
        // continuation; only fall back to the geometric match when there is no timing data at all.
        return anyTimeData ? null : candidates.get(0);
    }

    /**
     * Smallest non-negative time between the predecessor's last arrival and the follower's first
     * departure, wrapping over midnight.
     *
     * @return the gap in milliseconds, or {@code -1} when either route has no times
     */
    private static long turnaroundGap(MtrRoute follower, MtrRoute predecessor) {
        List<Long> starts = follower.startTimes();
        List<Long> arrivals = predecessor.lastArrivals();
        if (starts.isEmpty() || arrivals.isEmpty()) {
            return -1L;
        }
        long best = Long.MAX_VALUE;
        for (Long start : starts) {
            for (Long arrival : arrivals) {
                long gap = TimeFormat.floorModDay(start) - TimeFormat.floorModDay(arrival);
                if (gap < 0L) {
                    gap += TimeFormat.DAY_MS;
                }
                if (gap < best) {
                    best = gap;
                }
            }
        }
        return best == Long.MAX_VALUE ? -1L : best;
    }

    // ------------------------------------------------------------------
    // Accessors
    // ------------------------------------------------------------------

    public LoadState state() {
        synchronized (lock) {
            return state;
        }
    }

    public String status() {
        synchronized (lock) {
            return status;
        }
    }

    public boolean isLoading() {
        synchronized (lock) {
            return state == LoadState.LOADING;
        }
    }

    public MtrNetwork network() {
        synchronized (lock) {
            return network;
        }
    }

    public boolean hasNetwork() {
        synchronized (lock) {
            return network != null;
        }
    }

    public int modCount() {
        synchronized (lock) {
            return modCount;
        }
    }

    public List<MtrRoute> routes() {
        synchronized (lock) {
            return sortedRoutes;
        }
    }

    public List<String> stationIds() {
        synchronized (lock) {
            return sortedStationIds;
        }
    }

    public MtrRoute route(String id) {
        synchronized (lock) {
            return network == null ? null : network.route(id);
        }
    }

    public MtrRoute selectedRoute() {
        synchronized (lock) {
            return network == null ? null : network.route(selectedRouteId);
        }
    }

    public String selectedRouteId() {
        synchronized (lock) {
            return selectedRouteId;
        }
    }

    public void selectRoute(String routeId) {
        synchronized (lock) {
            if (network != null && network.route(routeId) != null) {
                selectedRouteId = routeId;
                modCount++;
            }
        }
    }

    public String selectedStationId() {
        synchronized (lock) {
            return selectedStationId;
        }
    }

    public void selectStation(String stationId) {
        synchronized (lock) {
            if (network != null && network.station(stationId) != null) {
                selectedStationId = stationId;
                modCount++;
            }
        }
    }

    public long windowStartMs() {
        synchronized (lock) {
            return windowStartMs;
        }
    }

    public long windowEndMs() {
        synchronized (lock) {
            return windowEndMs;
        }
    }

    public void setWindow(long startMs, long endMs) {
        synchronized (lock) {
            long start = TimeFormat.floorModDay(startMs);
            long span = endMs - startMs;
            if (span < TimeFormat.MINUTE_MS) {
                span = 4L * TimeFormat.HOUR_MS;
            }
            if (span > TimeFormat.DAY_MS) {
                span = TimeFormat.DAY_MS;
            }
            // A window that crosses midnight keeps an end value larger than 24h on purpose.
            windowStartMs = start;
            windowEndMs = start + span;
            windowCustomized = true;
            analysis = null;
            modCount++;
        }
    }

    public boolean hasCustomWindow() {
        synchronized (lock) {
            return windowCustomized;
        }
    }

    /** True when the player chose to skip the read-only / platform-occupancy notice this session. */
    public boolean isIntroDismissed() {
        synchronized (lock) {
            return introDismissed;
        }
    }

    public void setIntroDismissed(boolean dismissed) {
        synchronized (lock) {
            introDismissed = dismissed;
        }
    }

    public Map<String, String> follows() {
        synchronized (lock) {
            return new LinkedHashMap<>(follows);
        }
    }

    public String followedBy(String routeId) {
        synchronized (lock) {
            return follows.get(routeId);
        }
    }

    /** True when the follow relation was detected automatically (its times are not rewritten). */
    public boolean isAutoFollow(String routeId) {
        synchronized (lock) {
            return autoFollows.contains(routeId);
        }
    }

    // ------------------------------------------------------------------
    // Analysis
    // ------------------------------------------------------------------

    /** Recomputes the conflict analysis when needed. Cheap enough to call every frame. */
    public SchedulerAnalysis analysis() {
        synchronized (lock) {
            if (network == null) {
                return SchedulerAnalysis.none();
            }
            if (analysis == null) {
                analysis = SchedulerAnalysis.compute(network, follows, windowStartMs, windowEndMs);
            }
            return analysis;
        }
    }

    public void invalidateAnalysis() {
        synchronized (lock) {
            analysis = null;
            modCount++;
        }
    }

    // ------------------------------------------------------------------
    // Editing (planner-local only)
    // ------------------------------------------------------------------

    /**
     * Replaces the departures of a route with a generated headway.
     *
     * @return an error message, or {@code null} on success
     */
    public String generateDepartures(MtrRoute route, long startMs, long intervalMs, int count) {
        synchronized (lock) {
            if (route == null) {
                return "no route selected";
            }
            if (startMs < 0L) {
                return "invalid start time";
            }
            if (intervalMs <= 0L) {
                return "interval must be greater than zero";
            }
            if (count <= 0) {
                return "repeat count must be positive";
            }
            List<Long> times = new ArrayList<>(count);
            for (int index = 0; index < count; index++) {
                times.add(TimeFormat.floorModDay(startMs + index * intervalMs));
            }
            route.setStartTimes(times);
            cascadeFollows();
            analysis = null;
            modCount++;
            return null;
        }
    }

    public void offsetRoute(MtrRoute route, long deltaMs) {
        synchronized (lock) {
            if (route == null || deltaMs == 0L) {
                return;
            }
            route.offsetStartTimes(deltaMs);
            cascadeFollows();
            analysis = null;
            modCount++;
        }
    }

    public void clearFollow(String routeId) {
        synchronized (lock) {
            String precedingId = follows.get(routeId);
            boolean removed = follows.remove(routeId) != null;
            manualFollows.remove(routeId);
            autoFollows.remove(routeId);
            // Do not let auto-detection immediately restore the pair the player removed. Suppressing
            // both endpoints also prevents it from reappearing in the opposite direction.
            suppressedAutoFollows.add(routeId);
            if (precedingId != null) {
                suppressedAutoFollows.add(precedingId);
            }
            if (removed) {
                analysis = null;
                modCount++;
            }
        }
    }

    /**
     * Makes {@code toRoute}'s departures equal to the last arrival of {@code fromRoute}.
     *
     * @return an error message, or {@code null} on success
     */
    public String setFollow(String toRouteId, String fromRouteId) {
        synchronized (lock) {
            if (network == null) {
                return "no data loaded";
            }
            MtrRoute toRoute = network.route(toRouteId);
            MtrRoute fromRoute = network.route(fromRouteId);
            if (toRoute == null || fromRoute == null || toRoute == fromRoute) {
                return "invalid follow relation";
            }
            if (toRoute.stops.isEmpty() || fromRoute.stops.isEmpty()) {
                return "route has no stops";
            }
            MtrStop first = toRoute.stops.get(0);
            MtrStop last = fromRoute.stops.get(fromRoute.stops.size() - 1);
            if (!first.stationId.equals(last.stationId) || !first.platformName.equals(last.platformName)) {
                return "the routes do not share the same terminal platform";
            }
            if (wouldCreateCycle(toRouteId, fromRouteId)) {
                return "follow relation would create a cycle";
            }
            follows.put(toRouteId, fromRouteId);
            manualFollows.add(toRouteId);
            autoFollows.remove(toRouteId);
            suppressedAutoFollows.remove(toRouteId);
            applyFollow(toRoute, fromRoute);
            analysis = null;
            modCount++;
            return null;
        }
    }

    /** Routes that could precede {@code routeId}: same terminal platform and a shared depot. */
    public List<MtrRoute> followCandidates(String routeId) {
        synchronized (lock) {
            List<MtrRoute> candidates = new ArrayList<>();
            if (network == null) {
                return candidates;
            }
            MtrRoute route = network.route(routeId);
            if (route == null || route.stops.isEmpty()) {
                return candidates;
            }
            String firstStation = route.stops.get(0).stationId;
            String firstPlatform = route.stops.get(0).platformName;
            for (MtrRoute other : network.routes) {
                if (other.id.equals(routeId) || other.stops.isEmpty()) {
                    continue;
                }
                MtrStop otherLastStop = other.stops.get(other.stops.size() - 1);
                if (!otherLastStop.stationId.equals(firstStation)
                        || !otherLastStop.platformName.equals(firstPlatform)) {
                    continue;
                }
                if (!shareDepot(route, other)) {
                    continue;
                }
                if (wouldCreateCycle(routeId, other.id)) {
                    continue;
                }
                candidates.add(other);
            }
            candidates.sort((left, right) -> left.label().compareToIgnoreCase(right.label()));
            return candidates;
        }
    }

    /** Applies the depot offsets suggested by the analysis. */
    public void applyOffsets() {
        synchronized (lock) {
            SchedulerAnalysis current = analysis();
            if (!current.solvable() || current.depotOffsetsMs().isEmpty()) {
                return;
            }
            for (MtrRoute route : sortedRoutes) {
                Long offset = current.depotOffsetsMs().get(route.primaryDepot());
                if (offset != null && offset != 0L) {
                    route.offsetStartTimes(offset);
                }
            }
            cascadeFollows();
            analysis = null;
            modCount++;
        }
    }

    // ------------------------------------------------------------------
    // Follow helpers
    // ------------------------------------------------------------------

    /**
     * Re-derives the departures of manually followed routes from their predecessor. Automatically
     * detected continuations intentionally keep the departure times reported by MTR; they only
     * suppress the duplicate terminal-platform occupancy.
     */
    private void cascadeFollows() {
        if (network == null || follows.isEmpty() || manualFollows.isEmpty()) {
            return;
        }
        // Repeat until stable so chains of relations are handled regardless of insertion order.
        int guard = follows.size() + 1;
        boolean changed = true;
        while (changed && guard-- > 0) {
            changed = false;
            for (Map.Entry<String, String> entry : new ArrayList<>(follows.entrySet())) {
                if (!manualFollows.contains(entry.getKey())) {
                    continue;
                }
                MtrRoute toRoute = network.route(entry.getKey());
                MtrRoute fromRoute = network.route(entry.getValue());
                if (toRoute == null || fromRoute == null) {
                    continue;
                }
                List<Long> expected = floorModAll(fromRoute.lastArrivals());
                if (!expected.equals(toRoute.startTimes())) {
                    toRoute.setStartTimes(expected);
                    changed = true;
                }
            }
        }
    }

    private void applyFollow(MtrRoute toRoute, MtrRoute fromRoute) {
        toRoute.setStartTimes(floorModAll(fromRoute.lastArrivals()));
    }

    private boolean wouldCreateCycle(String toRouteId, String fromRouteId) {
        if (toRouteId.equals(fromRouteId)) {
            return true;
        }
        Set<String> visited = new HashSet<>();
        String cursor = fromRouteId;
        while (cursor != null && visited.add(cursor)) {
            if (cursor.equals(toRouteId)) {
                return true;
            }
            cursor = follows.get(cursor);
        }
        return false;
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

    private static List<Long> floorModAll(List<Long> values) {
        List<Long> result = new ArrayList<>(values.size());
        for (Long value : values) {
            result.add(TimeFormat.floorModDay(value == null ? 0L : value));
        }
        return result;
    }
}
