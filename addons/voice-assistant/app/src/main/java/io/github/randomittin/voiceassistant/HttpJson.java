package io.github.randomittin.voiceassistant;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/** One blocking HTTPS POST. Call it off the main thread. */
final class HttpJson {

    private static final int CONNECT_TIMEOUT_MS = 10000;

    private HttpJson() {
    }

    static String post(String url, Map<String, String> headers, String contentType, byte[] body,
                       int readTimeoutMs) throws ApiException {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
            conn.setReadTimeout(readTimeoutMs);
            conn.setDoOutput(true);
            conn.setFixedLengthStreamingMode(body.length);
            conn.setRequestProperty("Content-Type", contentType);
            conn.setRequestProperty("Accept", "application/json");
            for (Map.Entry<String, String> h : headers.entrySet()) {
                conn.setRequestProperty(h.getKey(), h.getValue());
            }
            try (OutputStream out = conn.getOutputStream()) {
                out.write(body);
            }
            int status = conn.getResponseCode();
            if (status >= 200 && status < 300) {
                return read(conn.getInputStream());
            }
            String errorBody = conn.getErrorStream() == null ? "" : read(conn.getErrorStream());
            throw new ApiException(ApiException.kindForStatus(status), status,
                    ApiRequests.errorMessage(errorBody));
        } catch (SocketTimeoutException e) {
            throw new ApiException(ApiException.Kind.TIMEOUT, 0, "timed out");
        } catch (IOException e) {
            throw new ApiException(ApiException.Kind.NETWORK, 0, String.valueOf(e.getMessage()));
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    private static String read(InputStream in) throws IOException {
        try (InputStream stream = in) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = stream.read(buf)) > 0) {
                out.write(buf, 0, n);
            }
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        }
    }
}
