package cn.myfrank.stationbuilder.scheduler;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Platform-occupancy conflict analysis and depot-offset optimisation.
 *
 * <p>The model follows the original web planner but fixes the issues that made its solver unsafe
 * to act on:</p>
 * <ul>
 *   <li>times are absolute and folded per operating day, so a 23:58&ndash;00:04 stop is compared
 *       correctly instead of producing a negative interval;</li>
 *   <li>the differencing solver always includes a fixed reference depot, so a solution can never
 *       shift every depot by an arbitrary amount;</li>
 *   <li>the maximum-gap search starts at one minute and doubles until it becomes infeasible instead
 *       of stopping at a hard-coded 300 seconds;</li>
 *   <li>conflicts that cannot be resolved by depot offsets (routes sharing one depot) are reported
 *       separately rather than silently dropped.</li>
 * </ul>
 *
 * <p>Scope: this analyses <em>platform occupancy</em> only. It does not model block sections,
 * junctions, single-line working or rolling-stock circulation.</p>
 */
public final class SchedulerAnalysis {

    /** Minimum platform headway the planner considers safe. */
    public static final long DEFAULT_SAFE_GAP_MS = 60_000L;
    /** Upper bound for the maximum-gap search; beyond this the result is only reported as "at least". */
    public static final long MAX_GAP_SEARCH_MS = 10L * TimeFormat.MINUTE_MS;

    public enum Kind {
        /** Two trains are on the same platform at the same time. */
        OVERLAP,
        /** Two trains use the same platform with less than the safe headway between them. */
        TIGHT_GAP
    }

    /** One point of conflict, kept structural so the UI can format and link it. */
    public static final class Issue {
        public final Kind kind;
        public final String stationId;
        public final String platform;
        public final String firstRouteId;
        public final String secondRouteId;
        public final long firstStartMs;
        public final long firstEndMs;
        public final long secondStartMs;
        public final long secondEndMs;

        Issue(
                Kind kind,
                String stationId,
                String platform,
                String firstRouteId,
                String secondRouteId,
                long firstStartMs,
                long firstEndMs,
                long secondStartMs,
                long secondEndMs
        ) {
            this.kind = kind;
            this.stationId = stationId;
            this.platform = platform;
            this.firstRouteId = firstRouteId;
            this.secondRouteId = secondRouteId;
            this.firstStartMs = firstStartMs;
            this.firstEndMs = firstEndMs;
            this.secondStartMs = secondStartMs;
            this.secondEndMs = secondEndMs;
        }

        /** Gap in milliseconds; negative for overlaps. */
        public long gapMs() {
            return secondStartMs - firstEndMs;
        }
    }

    /**
     * Conflicts aggregated per station / platform / route pair. Listing one row per train
     * occurrence is unreadable for a busy network, so the UI shows these groups (with the number of
     * occurrences, the covered time range and the worst gap) instead of every single trip pair.
     */
    public static final class ConflictGroup {
        public final String stationId;
        public final String platform;
        public final String firstRouteId;
        public final String secondRouteId;
        public final int occurrences;
        public final int overlapOccurrences;
        public final long earliestStartMs;
        public final long latestEndMs;
        public final long worstGapMs;

        ConflictGroup(
                String stationId,
                String platform,
                String firstRouteId,
                String secondRouteId,
                int occurrences,
                int overlapOccurrences,
                long earliestStartMs,
                long latestEndMs,
                long worstGapMs
        ) {
            this.stationId = stationId;
            this.platform = platform;
            this.firstRouteId = firstRouteId;
            this.secondRouteId = secondRouteId;
            this.occurrences = occurrences;
            this.overlapOccurrences = overlapOccurrences;
            this.earliestStartMs = earliestStartMs;
            this.latestEndMs = latestEndMs;
            this.worstGapMs = worstGapMs;
        }

        public boolean hasOverlap() {
            return overlapOccurrences > 0;
        }
    }

    private static final class GroupBuilder {
        private final String stationId;
        private final String platform;
        private final String firstRouteId;
        private final String secondRouteId;
        private int occurrences;
        private int overlapOccurrences;
        private long earliestStartMs = Long.MAX_VALUE;
        private long latestEndMs = Long.MIN_VALUE;
        private long worstGapMs = Long.MAX_VALUE;

        GroupBuilder(Issue issue) {
            this.stationId = issue.stationId;
            this.platform = issue.platform;
            this.firstRouteId = issue.firstRouteId;
            this.secondRouteId = issue.secondRouteId;
        }

        void add(Issue issue) {
            occurrences++;
            if (issue.kind == Kind.OVERLAP) {
                overlapOccurrences++;
            }
            earliestStartMs = Math.min(earliestStartMs, issue.firstStartMs);
            latestEndMs = Math.max(latestEndMs, issue.secondEndMs);
            worstGapMs = Math.min(worstGapMs, issue.gapMs());
        }

        ConflictGroup build() {
            return new ConflictGroup(
                    stationId,
                    platform,
                    firstRouteId,
                    secondRouteId,
                    occurrences,
                    overlapOccurrences,
                    earliestStartMs == Long.MAX_VALUE ? 0L : earliestStartMs,
                    latestEndMs == Long.MIN_VALUE ? 0L : latestEndMs,
                    worstGapMs == Long.MAX_VALUE ? 0L : worstGapMs
            );
        }
    }

    private final List<Issue> issues;
    private final List<ConflictGroup> groups;
    private final Map<String, Long> depotOffsetsMs;
    private final long achievableGapMs;
    private final boolean gapAtSearchLimit;
    private final boolean solvable;
    private final int sameDepotProblems;
    private final boolean hasConstraints;
    private final String summary;

    private SchedulerAnalysis(
            List<Issue> issues,
            Map<String, Long> depotOffsetsMs,
            long achievableGapMs,
            boolean gapAtSearchLimit,
            boolean solvable,
            int sameDepotProblems,
            boolean hasConstraints
    ) {
        this.issues = List.copyOf(issues);
        this.groups = groupIssues(this.issues);
        this.depotOffsetsMs = Map.copyOf(depotOffsetsMs);
        this.achievableGapMs = achievableGapMs;
        this.gapAtSearchLimit = gapAtSearchLimit;
        this.solvable = solvable;
        this.sameDepotProblems = sameDepotProblems;
        this.hasConstraints = hasConstraints;
        this.summary = buildSummary(
                this.issues.size(),
                this.groups.size(),
                sameDepotProblems,
                hasConstraints,
                solvable,
                achievableGapMs,
                gapAtSearchLimit,
                this.depotOffsetsMs.size()
        );
    }

    public List<Issue> issues() {
        return issues;
    }

    /**
     * One entry per station / platform / unordered route pair, aggregated over every train
     * occurrence in the analysis window.
     */
    public List<ConflictGroup> groups() {
        return groups;
    }

    private static List<ConflictGroup> groupIssues(List<Issue> issues) {
        if (issues.isEmpty()) {
            return List.of();
        }
        Map<String, GroupBuilder> builders = new LinkedHashMap<>();
        for (Issue issue : issues) {
            String first = issue.firstRouteId;
            String second = issue.secondRouteId;
            if (first.compareTo(second) > 0) {
                String swap = first;
                first = second;
                second = swap;
            }
            String key = issue.stationId + '\u0000' + issue.platform + '\u0000' + first + '\u0000' + second;
            builders.computeIfAbsent(key, ignored -> new GroupBuilder(issue)).add(issue);
        }
        List<ConflictGroup> result = new ArrayList<>(builders.size());
        for (GroupBuilder builder : builders.values()) {
            result.add(builder.build());
        }
        result.sort((left, right) -> {
            int byTime = Long.compare(left.earliestStartMs, right.earliestStartMs);
            if (byTime != 0) {
                return byTime;
            }
            int byStation = left.stationId.compareTo(right.stationId);
            return byStation != 0 ? byStation : left.platform.compareTo(right.platform);
        });
        return result;
    }

    /** Suggested offset per depot (milliseconds); the reference depot is always {@code 0}. */
    public Map<String, Long> depotOffsetsMs() {
        return depotOffsetsMs;
    }

    /** Largest minimum headway the offsets can achieve, or {@code -1} when there is nothing to fix. */
    public long achievableGapMs() {
        return achievableGapMs;
    }

    /** True when the search hit {@link #MAX_GAP_SEARCH_MS}; the value is then only a lower bound. */
    public boolean achievableGapIsLowerBound() {
        return gapAtSearchLimit;
    }

    public boolean solvable() {
        return solvable;
    }

    public int sameDepotProblems() {
        return sameDepotProblems;
    }

    public boolean hasConstraints() {
        return hasConstraints;
    }

    public String summary() {
        return summary;
    }

    public int overlapCount() {
        int count = 0;
        for (Issue issue : issues) {
            if (issue.kind == Kind.OVERLAP) {
                count++;
            }
        }
        return count;
    }

    public int warningCount() {
        return issues.size() - overlapCount();
    }

    // ------------------------------------------------------------------
    // Analysis
    // ------------------------------------------------------------------

    /** An empty result used before any MTR data has been loaded. */
    public static SchedulerAnalysis none() {
        return new SchedulerAnalysis(
                List.of(),
                Map.of(),
                -1L,
                false,
                true,
                0,
                false
        );
    }

    /**
     * Analyses the given network snapshot.
     *
     * @param network   snapshot whose routes carry the current departure times
     * @param follows   map of {@code routeBeingFollowed -> precedingRoute}; the first platform stop of
     *                  a followed route is already represented by the preceding route and is skipped
     * @param windowStart absolute start of the analysis window (milliseconds after the operating day started)
     * @param windowEnd   absolute end of the analysis window; may exceed 24 hours for overnight windows
     */
    public static SchedulerAnalysis compute(
            MtrNetwork network,
            Map<String, String> follows,
            long windowStart,
            long windowEnd
    ) {
        long start = Math.min(windowStart, windowEnd);
        long end = Math.max(windowStart, windowEnd);
        if (end - start < 60_000L) {
            end = start + 60_000L;
        }

        List<Segment> segments = new ArrayList<>();
        for (MtrRoute route : network.routes) {
            boolean followsAnotherRoute = network.isPlatformContinuation(follows, route);
            List<List<StopTime>> timelines = route.timelines();
            for (List<StopTime> timeline : timelines) {
                for (int index = 0; index < timeline.size(); index++) {
                    if (index == 0 && followsAnotherRoute) {
                        continue;
                    }
                    MtrStop stop = route.stops.get(index);
                    StopTime stopTime = timeline.get(index);
                    long segmentStart = stopTime.arrivalMs();
                    long segmentEnd = stopTime.departureMs();
                    for (int shift = -1; shift <= 2; shift++) {
                        long shiftedStart = segmentStart + shift * TimeFormat.DAY_MS;
                        long shiftedEnd = segmentEnd + shift * TimeFormat.DAY_MS;
                        if (shiftedEnd > start && shiftedStart < end) {
                            segments.add(new Segment(
                                    route,
                                    stop.stationId,
                                    stop.platformName,
                                    shiftedStart,
                                    shiftedEnd
                            ));
                        }
                    }
                }
            }
        }

        Map<String, List<Segment>> byStationPlatform = new LinkedHashMap<>();
        for (Segment segment : segments) {
            byStationPlatform
                    .computeIfAbsent(segment.stationId + "\u0000" + segment.platform, key -> new ArrayList<>())
                    .add(segment);
        }

        List<Issue> issues = new ArrayList<>();
        List<Constraint> constraints = new ArrayList<>();
        int sameDepotProblems = 0;
        long minimumSameDepotGap = Long.MAX_VALUE;

        for (List<Segment> group : byStationPlatform.values()) {
            group.sort(Comparator.comparingLong((Segment segment) -> segment.start)
                    .thenComparingLong(segment -> segment.end));
            for (int index = 1; index < group.size(); index++) {
                Segment previous = group.get(index - 1);
                Segment current = group.get(index);
                long gap = current.start - previous.end;

                boolean isIssue = gap < DEFAULT_SAFE_GAP_MS;
                if (gap < 0L) {
                    issues.add(new Issue(
                            Kind.OVERLAP,
                            current.stationId,
                            current.platform,
                            previous.route.id,
                            current.route.id,
                            previous.start,
                            previous.end,
                            current.start,
                            current.end
                    ));
                } else if (gap < DEFAULT_SAFE_GAP_MS) {
                    issues.add(new Issue(
                            Kind.TIGHT_GAP,
                            current.stationId,
                            current.platform,
                            previous.route.id,
                            current.route.id,
                            previous.start,
                            previous.end,
                            current.start,
                            current.end
                    ));
                }

                String previousDepot = previous.route.primaryDepot();
                String currentDepot = current.route.primaryDepot();
                if (previousDepot != null && currentDepot != null) {
                    if (previousDepot.equals(currentDepot)) {
                        // Offsetting the shared depot moves both trains, so the gap is invariant.
                        minimumSameDepotGap = Math.min(minimumSameDepotGap, gap);
                        if (isIssue) {
                            sameDepotProblems++;
                        }
                    } else {
                        // (previous.end + o_previous) + gap <= (current.start + o_current)
                        // => o_previous - o_current <= current.start - previous.end - gap
                        constraints.add(new Constraint(currentDepot, previousDepot, gap));
                    }
                } else if (isIssue) {
                    // A route without a depot cannot be moved independently.
                    sameDepotProblems++;
                }
            }
        }

        Map<String, Long> offsets = new LinkedHashMap<>();
        long achievableGap = -1L;
        boolean gapAtLimit = false;
        boolean solvable = true;
        boolean hasConstraints = !constraints.isEmpty();

        if (hasConstraints) {
            String referenceDepot = chooseReferenceDepot(constraints);
            if (!feasible(constraints, 0L)) {
                solvable = false;
            } else {
                long low = 0L;
                long high = TimeFormat.MINUTE_MS;
                while (high <= MAX_GAP_SEARCH_MS && feasible(constraints, high)) {
                    low = high;
                    high *= 2L;
                }
                if (high > MAX_GAP_SEARCH_MS) {
                    // The search limit is only a lower bound; binary search up to it and flag the result.
                    high = MAX_GAP_SEARCH_MS + 1L;
                }
                while (high - low > 1_000L) {
                    long mid = low + (high - low) / 2L;
                    if (feasible(constraints, mid)) {
                        low = mid;
                    } else {
                        high = mid;
                    }
                }
                achievableGap = low;
                gapAtLimit = achievableGap >= MAX_GAP_SEARCH_MS - 1_000L;
                if (minimumSameDepotGap != Long.MAX_VALUE && minimumSameDepotGap >= 0L) {
                    achievableGap = Math.min(achievableGap, minimumSameDepotGap);
                }
                // Offsets target the safe headway (not the theoretical maximum), which keeps the
                // required shifts as small as possible. If the safe headway is unreachable, use the
                // best gap that was found.
                long preferredGap = Math.min(DEFAULT_SAFE_GAP_MS, Math.max(0L, achievableGap));
                long targetGap = feasible(constraints, preferredGap) ? preferredGap : achievableGap;
                Map<String, Long> distances = solve(constraints, targetGap);
                if (distances != null) {
                    long referenceDistance = distances.getOrDefault(referenceDepot, 0L);
                    for (Map.Entry<String, Long> entry : distances.entrySet()) {
                        long offset = entry.getValue() - referenceDistance;
                        if (offset != 0L) {
                            offsets.put(entry.getKey(), offset);
                        }
                    }
                } else {
                    solvable = false;
                }
            }
        }

        return new SchedulerAnalysis(
                issues,
                offsets,
                achievableGap,
                gapAtLimit,
                solvable,
                sameDepotProblems,
                hasConstraints
        );
    }

    /** Picks the depot that appears most often in the constraints as the fixed reference. */
    private static String chooseReferenceDepot(List<Constraint> constraints) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (Constraint constraint : constraints) {
            counts.merge(constraint.previousDepot, 1, Integer::sum);
            counts.merge(constraint.currentDepot, 1, Integer::sum);
        }
        String best = null;
        int bestCount = -1;
        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            if (entry.getValue() > bestCount) {
                best = entry.getKey();
                bestCount = entry.getValue();
            }
        }
        return best == null ? "" : best;
    }

    private static boolean feasible(List<Constraint> constraints, long gapMs) {
        return solve(constraints, gapMs) != null;
    }

    /**
     * Solves the difference constraints with Bellman&ndash;Ford.
     *
     * <p>Each depot is a variable; {@code dist[current] <= dist[previous] + (gap - target)}. All
     * distances start at zero, which is equivalent to a super-source connected to every depot with
     * weight zero, so a negative cycle means the requested minimum gap is infeasible.</p>
     *
     * @return the shortest distances, or {@code null} when a negative cycle exists
     */
    private static Map<String, Long> solve(List<Constraint> constraints, long gapMs) {
        Set<String> depots = new LinkedHashSet<>();
        for (Constraint constraint : constraints) {
            depots.add(constraint.previousDepot);
            depots.add(constraint.currentDepot);
        }
        Map<String, Long> distances = new HashMap<>();
        for (String depot : depots) {
            distances.put(depot, 0L);
        }

        int nodeCount = depots.size();
        for (int iteration = 0; iteration < nodeCount; iteration++) {
            boolean changed = false;
            for (Constraint constraint : constraints) {
                long weight = constraint.availableGapMs - gapMs;
                long candidate = distances.get(constraint.currentDepot) + weight;
                if (candidate < distances.get(constraint.previousDepot)) {
                    distances.put(constraint.previousDepot, candidate);
                    changed = true;
                }
            }
            if (!changed) {
                return distances;
            }
            if (iteration == nodeCount - 1) {
                return null;
            }
        }
        return distances;
    }

    private static String buildSummary(
            int issueCount,
            int groupCount,
            int sameDepotProblems,
            boolean hasConstraints,
            boolean solvable,
            long achievableGapMs,
            boolean gapAtSearchLimit,
            int offsetCount
    ) {
        StringBuilder summary = new StringBuilder();
        summary.append(issueCount).append(" trip conflict(s) in ").append(groupCount).append(" group(s)");
        if (!hasConstraints) {
            summary.append("; no cross-depot constraints");
        } else if (!solvable) {
            summary.append("; depot offsets cannot resolve the conflicts at any non-negative gap");
        } else {
            long targetSeconds = Math.max(0L, Math.min(DEFAULT_SAFE_GAP_MS, achievableGapMs)) / 1000L;
            if (gapAtSearchLimit) {
                summary.append("; at least ").append(achievableGapMs / 1000L)
                        .append("s minimum headway is achievable");
            } else {
                summary.append("; up to ").append(achievableGapMs / 1000L)
                        .append("s minimum headway is achievable");
            }
            if (offsetCount == 0) {
                summary.append("; no depot offset needed for the ").append(targetSeconds).append("s target");
            } else {
                summary.append("; ").append(offsetCount).append(" depot offset(s) reach the ")
                        .append(targetSeconds).append("s target");
            }
        }
        if (sameDepotProblems > 0) {
            summary.append("; ").append(sameDepotProblems)
                    .append(" conflicting pair(s) share a depot and cannot be fixed by depot offsets");
        }
        return summary.toString();
    }

    private record Segment(MtrRoute route, String stationId, String platform, long start, long end) {
    }

    /**
     * {@code o_previous - o_current <= availableGapMs}; offsets are measured in milliseconds.
     */
    private record Constraint(String currentDepot, String previousDepot, long availableGapMs) {
    }
}
