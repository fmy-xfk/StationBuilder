package cn.myfrank.stationbuilder.scheduler;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * A planned MTR route: ordered stops, run times, dwell times, depots and the departure times the
 * planner is currently working with.
 *
 * <p>The departure times are user-editable inside the planner only. Nothing here is written back
 * to MTR; the planner is a read-only analysis tool that can export a depot plan for manual entry.</p>
 */
public final class MtrRoute {

    public final String id;
    public final String name;
    public final String number;
    public final String type;
    public final int color;
    public final List<MtrStop> stops;
    /** Run time between consecutive stops, in milliseconds; length is normally {@code stops - 1}. */
    public final List<Long> durations;
    public final List<String> depots;

    private final List<Long> startTimes = new ArrayList<>();
    private List<List<StopTime>> timelines;
    private long durationMs = -1L;

    public MtrRoute(
            String id,
            String name,
            String number,
            String type,
            int color,
            List<MtrStop> stops,
            List<Long> durations,
            List<String> depots
    ) {
        this.id = id;
        this.name = name;
        this.number = number;
        this.type = type;
        this.color = color;
        this.stops = List.copyOf(stops);
        this.durations = List.copyOf(durations);
        this.depots = List.copyOf(depots);
    }

    /** Opaque ARGB color used for the occupancy bars. */
    public int argb() {
        return 0xFF000000 | (color & 0xFFFFFF);
    }

    /** First depot of the route, or {@code null} when MTR reports none. */
    public String primaryDepot() {
        return depots.isEmpty() ? null : depots.get(0);
    }

    public List<Long> startTimes() {
        return Collections.unmodifiableList(startTimes);
    }

    public boolean hasDepartures() {
        return !startTimes.isEmpty();
    }

    /**
     * Replaces the departure times. Values are folded into the operating day and de-duplicated, so
     * callers may pass absolute or negative timestamps safely.
     */
    public void setStartTimes(Collection<Long> times) {
        List<Long> normalized = new ArrayList<>();
        if (times != null) {
            for (Long time : times) {
                if (time != null) {
                    normalized.add(TimeFormat.floorModDay(time));
                }
            }
        }
        normalized.sort(Long::compareTo);
        List<Long> unique = new ArrayList<>(normalized.size());
        for (Long time : normalized) {
            if (unique.isEmpty() || !unique.get(unique.size() - 1).equals(time)) {
                unique.add(time);
            }
        }
        startTimes.clear();
        startTimes.addAll(unique);
        invalidate();
    }

    /** Shifts every departure of this route by {@code deltaMs}. */
    public void offsetStartTimes(long deltaMs) {
        if (deltaMs == 0L || startTimes.isEmpty()) {
            return;
        }
        List<Long> shifted = new ArrayList<>(startTimes.size());
        for (Long time : startTimes) {
            shifted.add(TimeFormat.floorModDay(time + deltaMs));
        }
        setStartTimes(shifted);
    }

    /**
     * Total time from the first departure to the last departure, i.e. all dwell times plus the run
     * times between consecutive stops. Circular routes may store one extra closing segment (the
     * return leg to the first platform); that segment is not part of the last platform's departure
     * time and is therefore excluded from the duration used to back-calculate the start time.
     */
    public long durationMs() {
        if (durationMs < 0L) {
            long total = 0L;
            for (MtrStop stop : stops) {
                total += Math.max(0L, stop.dwellMs);
            }
            int usedSegments = Math.max(0, stops.size() - 1);
            for (int index = 0; index < durations.size() && index < usedSegments; index++) {
                Long duration = durations.get(index);
                total += Math.max(0L, duration == null ? 0L : duration);
            }
            durationMs = total;
        }
        return durationMs;
    }

    /**
     * Computes the arrival / departure timeline for every departure of this route.
     *
     * <p>Times are absolute milliseconds from the start of the operating day and are intentionally
     * not wrapped, so a trip that starts at 23:58 correctly produces a departure at 00:04 the next
     * day instead of a negative interval.</p>
     */
    public List<List<StopTime>> timelines() {
        if (timelines == null) {
            List<List<StopTime>> result = new ArrayList<>(startTimes.size());
            for (Long startTime : startTimes) {
                List<StopTime> line = new ArrayList<>(stops.size());
                long cursor = startTime;
                for (int i = 0; i < stops.size(); i++) {
                    MtrStop stop = stops.get(i);
                    long arrival = cursor;
                    long departure = arrival + Math.max(0L, stop.dwellMs);
                    line.add(new StopTime(arrival, departure));
                    cursor = departure;
                    if (i < durations.size()) {
                        Long run = durations.get(i);
                        cursor += Math.max(0L, run == null ? 0L : run);
                    }
                }
                result.add(line);
            }
            timelines = result;
        }
        return timelines;
    }

    /** Time the last departure leaves the last platform, for each trip. */
    public List<Long> lastDepartures() {
        List<Long> result = new ArrayList<>();
        for (List<StopTime> line : timelines()) {
            result.add(line.isEmpty() ? 0L : line.get(line.size() - 1).departureMs());
        }
        return result;
    }

    /** Time the last arrival reaches the last platform, for each trip. */
    public List<Long> lastArrivals() {
        List<Long> result = new ArrayList<>();
        for (List<StopTime> line : timelines()) {
            result.add(line.isEmpty() ? 0L : line.get(line.size() - 1).arrivalMs());
        }
        return result;
    }

    /** Dwell time at the first platform, used when a route follows another route. */
    public long firstDwellMs() {
        return stops.isEmpty() ? 0L : Math.max(0L, stops.get(0).dwellMs);
    }

    private void invalidate() {
        timelines = null;
    }

    /** Human readable label such as {@code "Line 2 (2)"}. */
    public String label() {
        if (number == null || number.isEmpty()) {
            return name;
        }
        return name + " (" + number + ")";
    }
}
