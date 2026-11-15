package cn.myfrank.stationbuilder.scheduler;

/**
 * One platform stop of a route, mirroring MTR's {@code routeStation} JSON object.
 *
 * <p>{@code stationId} is the station hex id used to group platforms; {@code platformName} is the
 * platform / direction name shown by MTR (for example {@code "1"} or {@code "Up"}).</p>
 */
public final class MtrStop {

    public final String stationId;
    public final String platformName;
    public final long dwellMs;
    public final int x;
    public final int y;
    public final int z;

    public MtrStop(String stationId, String platformName, long dwellMs, int x, int y, int z) {
        this.stationId = stationId;
        this.platformName = platformName;
        this.dwellMs = Math.max(0L, dwellMs);
        this.x = x;
        this.y = y;
        this.z = z;
    }
}
