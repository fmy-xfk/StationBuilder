package cn.myfrank.stationbuilder.scheduler;

import java.time.Instant;
import java.time.ZoneId;

/**
 * Time-of-day helpers for the schedule planner.
 *
 * <p>MTR stores dwell times and inter-station run times in milliseconds, and exposes upcoming
 * departures as a millisecond offset from {@code cachedResponseTime}. The planner keeps every
 * timestamp as an absolute number of milliseconds from the start of the operating day (which may
 * legitimately exceed 24 hours for a trip that crosses midnight) and only folds it back into
 * {@code [0, 24h)} for display. That is what makes the midnight handling correct.</p>
 */
public final class TimeFormat {

    public static final long DAY_MS = 86_400_000L;
    public static final long HOUR_MS = 3_600_000L;
    public static final long MINUTE_MS = 60_000L;

    private TimeFormat() {
    }

    public static long floorModDay(long millis) {
        long result = millis % DAY_MS;
        return result < 0 ? result + DAY_MS : result;
    }

    /** Converts an epoch timestamp into milliseconds after local midnight. */
    public static long localDayMillis(long epochMillis) {
        ZoneId zone = ZoneId.systemDefault();
        long offset = zone.getRules().getOffset(Instant.ofEpochMilli(epochMillis)).getTotalSeconds() * 1000L;
        return floorModDay(epochMillis + offset);
    }

    /** Formats absolute planner milliseconds as {@code HH:MM:SS}, wrapping past midnight. */
    public static String hms(long millis) {
        long value = floorModDay(millis) / 1000L;
        long seconds = value % 60L;
        long minutes = (value / 60L) % 60L;
        long hours = value / 3600L;
        return String.format("%02d:%02d:%02d", hours, minutes, seconds);
    }

    /** Formats absolute planner milliseconds as {@code HH:MM}. */
    public static String hm(long millis) {
        long value = floorModDay(millis) / 60_000L;
        long minutes = value % 60L;
        long hours = value / 60L;
        return String.format("%02d:%02d", hours, minutes);
    }

    /** Formats a duration (which must not wrap) as {@code HH:MM:SS}. */
    public static String duration(long millis) {
        long value = Math.max(0L, millis) / 1000L;
        long seconds = value % 60L;
        long minutes = (value / 60L) % 60L;
        long hours = value / 3600L;
        return String.format("%02d:%02d:%02d", hours, minutes, seconds);
    }

    /** Formats a signed offset as {@code +MM:SS} or {@code -MM:SS}. */
    public static String offset(long millis) {
        long seconds = Math.round(Math.abs(millis) / 1000.0);
        long minutes = seconds / 60L;
        long rest = seconds % 60L;
        return String.format("%s%d:%02d", millis >= 0 ? "+" : "-", minutes, rest);
    }

    /**
     * Parses {@code HH:MM} or {@code HH:MM:SS} into milliseconds after midnight.
     *
     * @return the parsed value, or {@code -1} when the text is not a valid time of day
     */
    public static long parseHms(String text) {
        if (text == null) {
            return -1L;
        }
        String trimmed = text.trim();
        if (trimmed.isEmpty()) {
            return -1L;
        }
        String[] parts = trimmed.split(":", -1);
        if (parts.length != 2 && parts.length != 3) {
            return -1L;
        }
        long hours;
        long minutes;
        long seconds = 0L;
        try {
            hours = Long.parseLong(parts[0].trim());
            minutes = Long.parseLong(parts[1].trim());
            if (parts.length == 3) {
                seconds = Long.parseLong(parts[2].trim());
            }
        } catch (NumberFormatException e) {
            return -1L;
        }
        if (hours < 0 || hours > 47 || minutes < 0 || minutes > 59 || seconds < 0 || seconds > 59) {
            return -1L;
        }
        return (hours * 3600L + minutes * 60L + seconds) * 1000L;
    }

    /** Parses a duration such as {@code 10:00} (ten minutes) or {@code 00:10:00}. */
    public static long parseDuration(String text) {
        long value = parseHms(text);
        return value < 0 ? -1L : value;
    }
}
