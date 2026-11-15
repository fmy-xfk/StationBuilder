package cn.myfrank.stationbuilder.mtr;

import org.mtr.core.data.Depot;
import org.mtr.core.generated.operation.ListDataResponseSchema;
import org.mtr.core.operation.ListDataResponse;
import org.mtr.core.simulation.Simulator;
import org.mtr.libraries.it.unimi.dsi.fastutil.longs.LongArrayList;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectArrayList;

import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Writes depot offsets into MTR's real-time schedules and asks MTR to regenerate the affected
 * depots. Only existing real-time schedules are shifted; frequency-based depots are reported as
 * unsupported instead of being overwritten.
 */
public final class MtrDepotOffsetWriter {

    public record Result(boolean success, String message, int depotsWritten) {
        public static Result error(String message) {
            return new Result(false, message, 0);
        }
    }

    private MtrDepotOffsetWriter() {
    }

    public static Result write(ListDataResponse response, Map<String, Long> offsets) {
        if (response == null) {
            return Result.error("MTR data is not available");
        }
        if (offsets == null || offsets.isEmpty()) {
            return new Result(true, "No depot offset needed", 0);
        }

        Simulator simulator;
        List<Depot> depots;
        try {
            simulator = simulator(response);
            depots = field(response, "depots");
        } catch (RuntimeException exception) {
            return Result.error("Could not access MTR depot data: " + exception.getMessage());
        }
        if (simulator == null) {
            return Result.error("MTR is not running as a simulator");
        }

        Map<Depot, Long> pending = new LinkedHashMap<>();
        for (Map.Entry<String, Long> entry : offsets.entrySet()) {
            long offset = entry.getValue() == null ? 0L : entry.getValue();
            if (offset == 0L) {
                continue;
            }
            Depot depot = findDepot(depots, entry.getKey());
            if (depot == null) {
                return Result.error("Depot not found: " + entry.getKey());
            }
            if (depot.getRepeatInfinitely()) {
                continue;
            }
            if (!depot.getUseRealTime() || depot.getRealTimeDepartures().isEmpty()) {
                return Result.error("Depot " + depot.getName()
                        + " does not use a real-time schedule; enable it in MTR first");
            }
            pending.put(depot, offset);
        }

        if (pending.isEmpty()) {
            return new Result(true, "No depot offset needed", 0);
        }

        try {
            for (Map.Entry<Depot, Long> entry : pending.entrySet()) {
                LongArrayList departures = entry.getKey().getRealTimeDepartures();
                for (int index = 0; index < departures.size(); index++) {
                    departures.set(index, departures.getLong(index) + entry.getValue());
                }
                entry.getKey().setUseRealTime(true);
            }

            ObjectArrayList<Depot> regenerated = new ObjectArrayList<>(pending.keySet());
            simulator.save();
            Depot.generateDepots(simulator, regenerated);
            simulator.save();
        } catch (Throwable throwable) {
            return Result.error("Could not write MTR schedule: " + throwable.getMessage());
        }
        return new Result(true, "Wrote " + pending.size() + " depot offset(s) and refreshed routes", pending.size());
    }

    private static Depot findDepot(List<Depot> depots, String name) {
        if (name == null) {
            return null;
        }
        for (Depot depot : depots) {
            if (depot != null && name.equals(depot.getName())) {
                return depot;
            }
        }
        return null;
    }

    private static Simulator simulator(ListDataResponse response) {
        try {
            Field field = ListDataResponse.class.getDeclaredField("data");
            field.setAccessible(true);
            Object data = field.get(response);
            return data instanceof Simulator simulator ? simulator : null;
        } catch (ReflectiveOperationException exception) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> List<T> field(ListDataResponse response, String name) {
        try {
            Field field = ListDataResponseSchema.class.getDeclaredField(name);
            field.setAccessible(true);
            return (List<T>) field.get(response);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("MTR list_data response has no '" + name + "' collection", exception);
        }
    }
}
