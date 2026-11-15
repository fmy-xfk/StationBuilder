package cn.myfrank.stationbuilder.scheduler;

/**
 * A station as reported by {@code /mtr/api/map/stations-and-routes}.
 *
 * <p>Only the fields the planner actually renders are kept; the planner never talks to MTR
 * classes, so this is a plain snapshot of the JSON payload.</p>
 */
public final class MtrStation {

    public final String id;
    public final String name;
    public final int color;
    public final int zone1;
    public final int zone2;
    public final int zone3;

    public MtrStation(String id, String name, int color, int zone1, int zone2, int zone3) {
        this.id = id;
        this.name = name;
        this.color = color;
        this.zone1 = zone1;
        this.zone2 = zone2;
        this.zone3 = zone3;
    }

    /** Color as an opaque ARGB value suitable for {@code DrawContext.fill}. */
    public int argb() {
        return 0xFF000000 | (color & 0xFFFFFF);
    }
}
