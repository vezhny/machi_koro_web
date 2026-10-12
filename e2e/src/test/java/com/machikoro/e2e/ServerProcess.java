package com.machikoro.e2e;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.ServerSocket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * The packaged server jar, started once per test JVM on a free port with the {@code test-dice}
 * profile. Plain {@link HttpURLConnection} is used on purpose: it needs no NIO selector.
 */
final class ServerProcess {

    private static final Duration STARTUP_TIMEOUT = Duration.ofSeconds(90);
    private static ServerProcess shared;

    private final Process process;
    private final String baseUrl;
    private final Path log;

    private ServerProcess(Process process, String baseUrl, Path log) {
        this.process = process;
        this.baseUrl = baseUrl;
        this.log = log;
    }

    static synchronized ServerProcess shared() {
        if (shared == null) {
            try {
                shared = start();
            } catch (IOException e) {
                throw new IllegalStateException("Could not start the server", e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while starting the server", e);
            }
            Runtime.getRuntime().addShutdownHook(new Thread(shared::stop));
        }
        return shared;
    }

    String baseUrl() {
        return baseUrl;
    }

    /** Next rolls of whichever game rolls next, in order. */
    void pushDice(int... values) {
        String json = Arrays.stream(values).mapToObj(String::valueOf).collect(Collectors.joining(",", "{\"values\":[", "]}"));
        request("POST", "/api/test/dice", json);
    }

    void clearDice() {
        request("DELETE", "/api/test/dice", null);
    }

    private static ServerProcess start() throws IOException, InterruptedException {
        Path jar = Path.of(requiredProperty("e2e.server.jar")).normalize();
        if (!Files.isRegularFile(jar)) {
            throw new IllegalStateException("Server jar not found: " + jar
                    + ". Build it first, e.g. ./mvnw verify -Pe2e from the repository root.");
        }
        Path workDir = Path.of(requiredProperty("e2e.work.dir"));
        Files.createDirectories(workDir);
        Path log = workDir.resolve("e2e-server.log");
        int port = freePort();
        List<String> command = List.of(
                Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-Djdk.net.unixdomain.tmpdir=" + workDir.toAbsolutePath(),
                "-jar", jar.toString(),
                "--server.port=" + port,
                "--spring.profiles.active=test-dice");
        Process process = new ProcessBuilder(command)
                .redirectErrorStream(true)
                .redirectOutput(log.toFile())
                .start();
        ServerProcess server = new ServerProcess(process, "http://localhost:" + port, log);
        server.awaitHealthy();
        return server;
    }

    private void awaitHealthy() throws InterruptedException, IOException {
        Instant deadline = Instant.now().plus(STARTUP_TIMEOUT);
        while (Instant.now().isBefore(deadline)) {
            if (!process.isAlive()) {
                throw new IllegalStateException("Server exited with " + process.exitValue() + ":\n" + logTail());
            }
            try {
                if (request("GET", "/actuator/health", null).contains("\"UP\"")) {
                    return;
                }
            } catch (IllegalStateException notYet) {
                // still starting
            }
            Thread.sleep(250);
        }
        stop();
        throw new IllegalStateException("Server did not become healthy in " + STARTUP_TIMEOUT + ":\n" + logTail());
    }

    private String request(String method, String path, String json) {
        try {
            HttpURLConnection connection = (HttpURLConnection) URI.create(baseUrl + path).toURL().openConnection();
            connection.setRequestMethod(method);
            connection.setConnectTimeout(2000);
            connection.setReadTimeout(5000);
            if (json != null) {
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "application/json");
                try (OutputStream out = connection.getOutputStream()) {
                    out.write(json.getBytes(StandardCharsets.UTF_8));
                }
            }
            int status = connection.getResponseCode();
            InputStream body = status < 400 ? connection.getInputStream() : connection.getErrorStream();
            String text = body == null ? "" : new String(body.readAllBytes(), StandardCharsets.UTF_8);
            if (status >= 400) {
                throw new IllegalStateException(method + " " + path + " -> " + status + " " + text);
            }
            return text;
        } catch (IOException e) {
            throw new IllegalStateException(method + " " + path + " failed: " + e.getMessage(), e);
        }
    }

    private void stop() {
        process.destroy();
        try {
            if (!process.waitFor(10, TimeUnit.SECONDS)) {
                process.destroyForcibly();
            }
        } catch (InterruptedException e) {
            process.destroyForcibly();
            Thread.currentThread().interrupt();
        }
    }

    private String logTail() throws IOException {
        List<String> lines = Files.readAllLines(log);
        return String.join("\n", lines.subList(Math.max(0, lines.size() - 40), lines.size()));
    }

    private static int freePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    private static String requiredProperty(String name) {
        String value = System.getProperty(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("System property " + name + " is not set; run through Maven with -Pe2e");
        }
        return value;
    }
}
