package io.github.zzz1999.entityreskin.server.backend;

import io.github.zzz1999.entityreskin.server.PluginSettings;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

/**
 * Minimal HTTPS client for the resource backend, built on {@link HttpURLConnection} because the
 * plugin must run on Java 8 servers. Gson is provided by every Bukkit server since 1.8 and is
 * used through its Java 8 compatible entry points.
 */
public final class BackendClient {

    /** Result of a manifest hash poll. */
    public static final class HashResponse {

        public enum Status {
            /** The backend returned a valid hash. */
            OK,
            /** The backend has no appearance entries for this server (HTTP 404). */
            NO_CONTENT,
            /** The backend was unreachable or returned an unexpected response. */
            ERROR
        }

        private final Status status;
        private final String sha256Hex;

        private HashResponse(Status status, String sha256Hex) {
            this.status = status;
            this.sha256Hex = sha256Hex;
        }

        static HashResponse ok(String sha256Hex) {
            return new HashResponse(Status.OK, sha256Hex);
        }

        static HashResponse noContent() {
            return new HashResponse(Status.NO_CONTENT, null);
        }

        static HashResponse error() {
            return new HashResponse(Status.ERROR, null);
        }

        public Status status() {
            return status;
        }

        public String sha256Hex() {
            return sha256Hex;
        }
    }

    private static final Pattern SHA256_PATTERN = Pattern.compile("^[0-9a-f]{64}$");
    private static final int CONNECT_TIMEOUT_MILLIS = 5000;
    private static final int READ_TIMEOUT_MILLIS = 10000;
    private static final int MAX_RESPONSE_BYTES = 4096;

    private final String baseUrl;
    private final String serverToken;

    public BackendClient(PluginSettings settings) {
        this.baseUrl = settings.baseUrl();
        this.serverToken = settings.serverToken();
    }

    /** Fetches the current manifest SHA-256 for this server; never throws. */
    public HashResponse fetchManifestSha256() {
        HttpURLConnection connection = null;
        try {
            URL url = new URL(baseUrl + "api/manifest/sha256?srv="
                    + URLEncoder.encode(serverToken, "UTF-8"));
            connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(CONNECT_TIMEOUT_MILLIS);
            connection.setReadTimeout(READ_TIMEOUT_MILLIS);
            connection.setRequestProperty("User-Agent", "EntityReskin-Plugin");
            int code = connection.getResponseCode();
            if (code == HttpURLConnection.HTTP_OK) {
                String body = readBody(connection.getInputStream());
                String hash = parseSha256Response(body);
                return hash != null ? HashResponse.ok(hash) : HashResponse.error();
            }
            if (code == HttpURLConnection.HTTP_NOT_FOUND) {
                return HashResponse.noContent();
            }
            return HashResponse.error();
        } catch (IOException e) {
            return HashResponse.error();
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    /** Extracts and validates the {@code sha256} field, or returns {@code null}. */
    @SuppressWarnings("deprecation") // Instance JsonParser: required for the older Gson bundled by older servers.
    public static String parseSha256Response(String json) {
        try {
            JsonElement root = new JsonParser().parse(json);
            if (!root.isJsonObject()) {
                return null;
            }
            JsonObject object = root.getAsJsonObject();
            JsonElement hash = object.get("sha256");
            if (hash == null || !hash.isJsonPrimitive()) {
                return null;
            }
            String value = hash.getAsString();
            return SHA256_PATTERN.matcher(value).matches() ? value : null;
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static String readBody(InputStream in) throws IOException {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[1024];
            int read;
            while (out.size() < MAX_RESPONSE_BYTES && (read = in.read(buffer)) != -1) {
                out.write(buffer, 0, Math.min(read, MAX_RESPONSE_BYTES - out.size()));
            }
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        } finally {
            in.close();
        }
    }
}
