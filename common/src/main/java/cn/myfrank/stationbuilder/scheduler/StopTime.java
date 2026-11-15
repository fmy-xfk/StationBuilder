package cn.myfrank.stationbuilder.scheduler;

/** Arrival / departure pair of one train at one platform. */
public record StopTime(long arrivalMs, long departureMs) {

    public long dwellMs() {
        return Math.max(0L, departureMs - arrivalMs);
    }
}
