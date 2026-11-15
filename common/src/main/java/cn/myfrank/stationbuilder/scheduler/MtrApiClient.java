package cn.myfrank.stationbuilder.scheduler;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

/**
 * Minimal HTTP client for the MTR web API.
 *
 * <p>The planner reads data exclusively through MTR's read-only HTTP endpoints and never touches
 * MTR classes, the simulator thread, or MTR save data. Requests run on a short-lived daemon thread
 * so the client render thread is never blocked.</p>
 */
public final class MtrApiClient {

    public static final String DEFAULT_HOST = "127.0.0.1:8888";

    private static final int CONNECT_TIMEOUT_MS = 4_000;
    private static final int READ_TIMEOUT_MS = 8_000;
    private static final String USER_AGENT = "StationBuilder-Scheduler/1.0";

    private MtrApiClient() {
    }

    /** Fetch result: exactly one of {@link #network} / {@link #error} is non-null. */
    public static final class FetchResult {
        public final MtrNetwork network;
        public final String error;
        public final String origin;

        private FetchResult(MtrNetwork network, String error, String origin) {
            this.network = network;
            this.error = error;
            this.origin = origin;
        }

        public boolean isSuccess() {
            return network != null;
        }
    }

    /** Adds a scheme when missing and strips trailing slashes. */
    public static String normalizeBase(String host) {
        String value = host == null ? "" : host.trim();
        if (value.isEmpty()) {
            value = DEFAULT_HOST;
        }
        if (!value.startsWith("http://") && !value.startsWith("https://")) {
            value = "http://" + value;
        }
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        return value;
    }

    /** Runs both endpoint requests on a daemon thread and reports the result to {@code callback}. */
    public static void fetchAsync(String host, int dimension, Consumer<FetchResult> callback) {
        String base = normalizeBase(host);
        Thread thread = new Thread(
                () -> callback.accept(fetch(base, dimension)),
                "stationbuilder-mtr-scheduler"
        );
        thread.setDaemon(true);
        thread.start();
    }

    /** Blocking fetch; intended for the background thread and for offline tests. */
    public static FetchResult fetch(String host, int dimension) {
        String base = normalizeBase(host);
        String stationsUrl = base + "/mtr/api/map/stations-and-routes?dimension=" + dimension;
        String departuresUrl = base + "/mtr/api/map/departures?dimension=" + dimension;

        String stationsBody;
        try {
            stationsBody = get(stationsUrl);
        } catch (IOException | RuntimeException e) {
            return new FetchResult(null, "Could not read " + stationsUrl + " — " + describe(e), base);
        }

        String departuresBody = null;
        String departureError = null;
        try {
            departuresBody = get(departuresUrl);
        } catch (IOException | RuntimeException e) {
            departureError = describe(e);
        }

        try {
            MtrNetwork network = MtrNetwork.parse(
                    stationsBody,
                    departuresBody,
                    dimension,
                    System.currentTimeMillis()
            );
            if (departureError != null) {
                network.addWarning("departures: " + departureError);
            }
            if (network.routes.isEmpty()) {
                // An empty network is a valid state (the dimension simply has no routes yet), not a
                // failure: the screen renders an empty-state hint instead of an error.
                network.addWarning("the MTR API returned no routes for dimension " + dimension);
            }
            return new FetchResult(network, null, base);
        } catch (RuntimeException e) {
            return new FetchResult(null, "Could not parse the MTR response — " + describe(e), base);
        }
    }

    private static String describe(Throwable throwable) {
        String message = throwable.getMessage();
        return message == null || message.isBlank() ? throwable.getClass().getSimpleName() : message;
    }

    private static String get(String url) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        try {
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
            connection.setRequestMethod("GET");
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("User-Agent", USER_AGENT);
            int status = connection.getResponseCode();
            InputStream stream = status >= 400 ? connection.getErrorStream() : connection.getInputStream();
            String body = stream == null ? "" : readFully(stream);
            if (status != HttpURLConnection.HTTP_OK) {
                throw new IOException("HTTP " + status + (body.isBlank() ? "" : " — " + trim(body)));
            }
            return body;
        } finally {
            connection.disconnect();
        }
    }

    private static String readFully(InputStream stream) throws IOException {
        try (InputStream input = stream; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                output.write(buffer, 0, read);
            }
            return output.toString(StandardCharsets.UTF_8);
        }
    }

    private static String trim(String text) {
        String singleLine = text.replace('\n', ' ').replace('\r', ' ').trim();
        return singleLine.length() <= 160 ? singleLine : singleLine.substring(0, 160) + "…";
    }
}
