package com.bogoai.booknow.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

import java.net.ProtocolException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Tiny client for Binance's WebSocket API ({@code wss://ws-api.binance.com/ws-api/v3}).
 *
 * Why this exists: Binance retired {@code POST/PUT/DELETE /api/v3/userDataStream}
 * (returns 410 Gone). The replacement is {@code userDataStream.start} /
 * {@code userDataStream.ping} sent as JSON-RPC over the WS-API. The bundled
 * Java SDK ({@link com.bogoai.api.client.BinanceApiRestClient#startUserDataStream})
 * still hits the dead REST endpoint, so {@link BinanceUserDataStreamService}
 * uses this helper instead.
 *
 * Each call opens a short-lived WebSocket, sends one request, awaits one
 * response, and closes — there's nothing to multiplex for our use case
 * (a single boot-time start + a ping every 25 minutes).
 */
@Slf4j
public final class BinanceWsApiClient {

    private static final String WS_API_URL = "wss://ws-api.binance.com:443/ws-api/v3";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * Binance returns HTTP 418 ("I'm a teapot") when the IP has been
     * temporarily banned for ignoring 429s. Callers can check for this
     * via {@link #isIpBan(Throwable)} and back off accordingly instead of
     * retrying every 25 minutes which only deepens the ban.
     */
    public static final class BinanceIpBannedException extends RuntimeException {
        public final long retryAfterSeconds;

        public BinanceIpBannedException(int httpCode, long retryAfterSeconds, String message) {
            super("HTTP " + httpCode + " from Binance — IP temporarily banned. "
                + "Retry-After: " + retryAfterSeconds + "s. " + message);
            this.retryAfterSeconds = retryAfterSeconds;
        }
    }

    public static boolean isIpBan(Throwable t) {
        Throwable cur = t;
        while (cur != null) {
            if (cur instanceof BinanceIpBannedException) return true;
            cur = cur.getCause();
        }
        return false;
    }

    public static long extractRetryAfterSeconds(Throwable t) {
        Throwable cur = t;
        while (cur != null) {
            if (cur instanceof BinanceIpBannedException) {
                return ((BinanceIpBannedException) cur).retryAfterSeconds;
            }
            cur = cur.getCause();
        }
        return 0;
    }

    private final OkHttpClient http;
    private final long timeoutSeconds;

    public BinanceWsApiClient() {
        this(15);
    }

    public BinanceWsApiClient(long timeoutSeconds) {
        this.http = new OkHttpClient.Builder()
            .pingInterval(20, TimeUnit.SECONDS)
            .build();
        this.timeoutSeconds = timeoutSeconds;
    }

    /** Obtain a 60-minute spot user-data listenKey via WS-API. */
    public String startUserDataStream(String apiKey) throws Exception {
        JsonNode result = call("userDataStream.start", Map.of("apiKey", apiKey));
        JsonNode key = result.get("listenKey");
        if (key == null || key.isNull()) {
            throw new IllegalStateException("WS-API userDataStream.start returned no listenKey: " + result);
        }
        return key.asText();
    }

    /** Refresh an existing listenKey's TTL. */
    public void keepAliveUserDataStream(String apiKey, String listenKey) throws Exception {
        call("userDataStream.ping", Map.of("apiKey", apiKey, "listenKey", listenKey));
    }

    /** Best-effort close so Binance can reclaim the listenKey slot. */
    public void closeUserDataStream(String apiKey, String listenKey) throws Exception {
        call("userDataStream.stop", Map.of("apiKey", apiKey, "listenKey", listenKey));
    }

    // ── core request/response over a short-lived WS ─────────────────────────

    private JsonNode call(String method, Map<String, Object> params) throws Exception {
        // Binance requires `id` to match ^[a-zA-Z0-9-_]{1,36}$. A plain UUID
        // is exactly 36 chars and fits; embedding the method would add a
        // forbidden '.' and overflow the cap.
        String id = UUID.randomUUID().toString();
        String payload = MAPPER.writeValueAsString(Map.of(
            "id", id,
            "method", method,
            "params", params
        ));

        CompletableFuture<JsonNode> future = new CompletableFuture<>();
        Request req = new Request.Builder().url(WS_API_URL).build();

        WebSocket ws = http.newWebSocket(req, new WebSocketListener() {
            @Override
            public void onOpen(WebSocket socket, Response response) {
                socket.send(payload);
            }

            @Override
            public void onMessage(WebSocket socket, String text) {
                try {
                    JsonNode root = MAPPER.readTree(text);
                    int status = root.path("status").asInt(0);
                    if (status >= 400) {
                        JsonNode err = root.get("error");
                        String msg = err != null && err.has("msg") ? err.get("msg").asText()
                                   : err != null ? err.toString() : root.toString();
                        future.completeExceptionally(new RuntimeException(
                            "Binance WS-API " + method + " failed (" + status + "): " + msg));
                    } else {
                        future.complete(root.path("result"));
                    }
                } catch (Exception e) {
                    future.completeExceptionally(e);
                } finally {
                    socket.close(1000, "done");
                }
            }

            @Override
            public void onFailure(WebSocket socket, Throwable t, Response response) {
                // Binance signals an IP ban by responding HTTP 418 ("I'm a
                // teapot") to the WebSocket upgrade. OkHttp surfaces that
                // as ProtocolException("Expected HTTP 101 ... 418"). When
                // we can read the response, extract the Retry-After header
                // so callers can honour the cool-down.
                if (response != null) {
                    int code = response.code();
                    if (code == 418 || code == 429) {
                        long retryAfter = parseRetryAfter(response.header("Retry-After"));
                        future.completeExceptionally(new BinanceIpBannedException(
                            code, retryAfter,
                            "WS-API upgrade rejected: " + (t != null ? t.getMessage() : "")));
                        try { response.close(); } catch (Exception ignored) {}
                        return;
                    }
                }
                future.completeExceptionally(t);
            }
        });

        try {
            return future.get(timeoutSeconds, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            ws.cancel();
            throw new RuntimeException("Binance WS-API " + method + " timed out after " + timeoutSeconds + "s", e);
        } catch (ExecutionException e) {
            // Surface the typed BinanceIpBannedException directly so the
            // caller can branch on it without crawling the cause chain.
            Throwable cause = e.getCause();
            if (cause instanceof BinanceIpBannedException) {
                throw (BinanceIpBannedException) cause;
            }
            throw e;
        }
    }

    /**
     * Parse Binance's `Retry-After` header. Most often a plain integer
     * (seconds); HTTP also allows an HTTP-date but Binance doesn't use
     * that form in practice. Defaults to 120s when unparseable, which is
     * the minimum documented IP-ban duration.
     */
    private static long parseRetryAfter(String header) {
        if (header == null || header.isEmpty()) return 120;
        try {
            long n = Long.parseLong(header.trim());
            return Math.max(60, n);
        } catch (NumberFormatException nfe) {
            return 120;
        }
    }
}
