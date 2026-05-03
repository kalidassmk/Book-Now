package com.bogoai.booknow.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
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
                future.completeExceptionally(t);
            }
        });

        try {
            return future.get(timeoutSeconds, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            ws.cancel();
            throw new RuntimeException("Binance WS-API " + method + " timed out after " + timeoutSeconds + "s", e);
        }
    }
}
