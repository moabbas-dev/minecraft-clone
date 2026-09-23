package com.example.testctl;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeoutException;

/**
 * Opt-in local HTTP control surface for automated testing (see the request's live-controller
 * requirements). Off by default - only started when Game.init() sees -Dtest.controller=true.
 * Bound to loopback only, on an OS-assigned port (never hardcoded, so it's never reachable
 * off-box and never collides with another process's port - the request's own safety
 * constraints). Uses only the JDK-bundled HttpServer, so this adds no new dependency.
 */
@Slf4j
public final class TestController {
    private static final long COMMAND_TIMEOUT_MILLIS = 5000L;

    private final GameCommandQueue queue;
    private final Commands commands;
    private HttpServer server;

    public TestController(GameAccess game, GameCommandQueue queue) {
        this.queue = queue;
        this.commands = new Commands(game);
    }

    /** Starts listening on 127.0.0.1 with an OS-assigned port and logs the actual port (needed
     *  since callers can't predict an ephemeral port ahead of time). */
    public void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/", this::handle);
        server.setExecutor(null); // default executor is fine - every command hands off to the GL thread anyway
        server.start();
        log.info("Test controller listening on http://127.0.0.1:{}", server.getAddress().getPort());
    }

    public int port() {
        return server != null ? server.getAddress().getPort() : -1;
    }

    /** No-op if never started. */
    public void stop() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
    }

    private void handle(HttpExchange exchange) {
        try {
            String path = exchange.getRequestURI().getPath();
            String command = path.startsWith("/") ? path.substring(1) : path;
            Map<String, String> params = parseQuery(exchange.getRequestURI().getQuery());

            Commands.Result result;
            try {
                result = queue.submitAndWait(() -> commands.execute(command, params), COMMAND_TIMEOUT_MILLIS);
            } catch (TimeoutException e) {
                result = new Commands.Result(504, "command timed out after " + COMMAND_TIMEOUT_MILLIS + "ms: " + command);
            } catch (RuntimeException e) {
                log.warn("Command '{}' threw", command, e);
                result = new Commands.Result(500, "command failed: " + e.getMessage());
            }

            byte[] body = result.body().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
            exchange.sendResponseHeaders(result.status(), body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        } catch (IOException e) {
            log.warn("Failed to write test controller response", e);
        } finally {
            exchange.close();
        }
    }

    private Map<String, String> parseQuery(String query) {
        Map<String, String> params = new HashMap<>();
        if (query == null || query.isEmpty()) return params;
        for (String pair : query.split("&")) {
            int eq = pair.indexOf('=');
            String key = eq >= 0 ? pair.substring(0, eq) : pair;
            String value = eq >= 0 ? pair.substring(eq + 1) : "";
            params.put(urlDecode(key), urlDecode(value));
        }
        return params;
    }

    private String urlDecode(String s) {
        return URLDecoder.decode(s, StandardCharsets.UTF_8);
    }
}
