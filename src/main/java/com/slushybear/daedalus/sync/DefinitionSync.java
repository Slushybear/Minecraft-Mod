package com.slushybear.daedalus.sync;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.slushybear.daedalus.DaedalusClient;
import com.slushybear.daedalus.automation.Allowlist;
import com.slushybear.daedalus.automation.MacroManager;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Fetches the definitions file on a background thread and swaps the allowlist in. It only reads data.
 * No Java code is downloaded or run. Code changes ship through git pull and a restart.
 */
public final class DefinitionSync {
    // Commit definitions/definitions.json to the main branch of the repo for this to resolve.
    private static final URI SOURCE = URI.create(
            "https://raw.githubusercontent.com/Slushybear/Minecraft-Mod/main/definitions/definitions.json");
    private static final int MAX_BYTES = 64 * 1024;
    private static final long INTERVAL_MINUTES = 10;

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            // Redirects are refused, so a redirect cannot send the request to another host.
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    private static final ScheduledExecutorService EXECUTOR = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "daedalus-definition-sync");
        thread.setDaemon(true);
        return thread;
    });

    private DefinitionSync() {}

    public static void start() {
        EXECUTOR.scheduleAtFixedRate(DefinitionSync::refresh, 0, INTERVAL_MINUTES, TimeUnit.MINUTES);
    }

    public static void refreshNow() {
        EXECUTOR.execute(DefinitionSync::refresh);
    }

    private static void refresh() {
        try {
            HttpRequest request = HttpRequest.newBuilder(SOURCE)
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();
            HttpResponse<InputStream> response = HTTP.send(request, HttpResponse.BodyHandlers.ofInputStream());

            if (response.statusCode() != 200) {
                DaedalusClient.LOGGER.warn("Definition sync got HTTP {}, keeping current rules", response.statusCode());
                return;
            }

            byte[] body = readCapped(response.body());
            JsonObject json = JsonParser.parseString(new String(body, StandardCharsets.UTF_8)).getAsJsonObject();
            Allowlist allowlist = Allowlist.fromRemote(json);
            MacroManager.setAllowlist(allowlist);
            DaedalusClient.LOGGER.info("Definitions updated: {} crops, {} actions", allowlist.crops().size(), allowlist.actions().size());
        } catch (IOException | InterruptedException | RuntimeException e) {
            // Any failure keeps the previous rules. The sync thread must never die.
            DaedalusClient.LOGGER.warn("Definition sync failed, keeping current rules: {}", e.getMessage());
        }
    }

    private static byte[] readCapped(InputStream stream) throws IOException {
        try (InputStream in = stream) {
            byte[] bytes = in.readNBytes(MAX_BYTES + 1);
            if (bytes.length > MAX_BYTES) {
                throw new IOException("Definitions file is larger than " + MAX_BYTES + " bytes");
            }
            return bytes;
        }
    }
}
