package cn.myfrank.stationbuilder.scheduler;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Gzip helper for the MTR snapshot packet.
 *
 * <p>MTR networks can contain thousands of routes, stops and departures. The JSON snapshot is
 * compressed before it is split into network packets so the transfer stays well below the packet
 * size limit.</p>
 */
public final class PayloadCompression {

    private PayloadCompression() {
    }

    public static byte[] compress(String text) {
        if (text == null || text.isEmpty()) {
            return new byte[0];
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(output)) {
            gzip.write(text.getBytes(StandardCharsets.UTF_8));
        } catch (IOException exception) {
            throw new IllegalStateException("Could not compress MTR snapshot", exception);
        }
        return output.toByteArray();
    }

    public static String decompress(byte[] data) {
        if (data == null || data.length == 0) {
            return "";
        }
        try (GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(data))) {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int read;
            while ((read = gzip.read(buffer)) >= 0) {
                output.write(buffer, 0, read);
            }
            return output.toString(StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not decompress MTR snapshot", exception);
        }
    }
}
