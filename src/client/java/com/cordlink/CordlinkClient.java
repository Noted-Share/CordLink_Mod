package com.cordlink;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

public class CordlinkClient implements ClientModInitializer {
    private static final Logger LOGGER = LoggerFactory.getLogger("cordlink");
    private static final int UDP_PORT = 9150;
    public static final String API_BASE = "http://slcdn.info:8080";
    public static final String DISCORD_INVITE = "https://discord.gg/7bGzPnNUjS";

    private static CordlinkClient instance;

    private DatagramSocket udpSocket;
    private InetAddress localhost;
    private boolean injected = false;
    private volatile boolean syncing = false;
    private String discordVariant = "discord.exe";

    private final SharedPositionWriter shmWriter = new SharedPositionWriter();
    private boolean shmOpen = false;

    private volatile boolean writerRunning = false;
    private Thread rotationWriter;
    private float masterVolume;

    private final DatagramPacket packet = new DatagramPacket(new byte[1], 1);

    private static final HttpClient httpClient = HttpClient.newHttpClient();

    public static CordlinkClient getInstance() { return instance; }
    public boolean isInjected() { return injected; }
    public boolean isShmOpen() { return shmOpen; }
    public boolean isSyncing() { return syncing; }
    public String getDiscordVariant() { return discordVariant; }
    public void setDiscordVariant(String variant) { this.discordVariant = variant; }
    public float getMasterVolume() { return masterVolume; }
    public void setMasterVolume(float volume) {
        this.masterVolume = Math.max(0.0f, Math.min(2.0f, volume));
        if (shmOpen) shmWriter.writeMasterVolume(this.masterVolume);
        CordlinkConfig.masterVolume = this.masterVolume;
        CordlinkConfig.save();
    }

    @Override
    public void onInitializeClient() {
        instance = this;
        CordlinkConfig.load();
        masterVolume = CordlinkConfig.masterVolume;
        registerRenderHandler();
        detectExistingInjection();

        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            if (injected) {
                LOGGER.info("Minecraft stopping, unloading DLL...");
                sendText("Q");
                stopRotationWriter();
                shmWriter.close();
                shmOpen = false;
                injected = false;
            }
        });

        // Clear SHM sources when disconnecting from server
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            if (shmOpen) {
                shmWriter.writeSources(new String[0], new float[0], new float[0], new float[0], 0);
                shmWriter.commit();
                LOGGER.info("Server disconnected, cleared SHM sources");
            }
        });

        LOGGER.info("Cordlink client initialized");
    }

    private void registerRenderHandler() {
        WorldRenderEvents.START.register(ctx -> {
            var client = MinecraftClient.getInstance();
            if (!injected || !shmOpen || client.player == null || client.world == null) return;

            var camera = ctx.camera();
            var camPos = camera.getPos();
            float lx = (float) camPos.x;
            float ly = (float) camPos.y;
            float lz = (float) camPos.z;
            float yaw = (float) Math.toRadians(camera.getYaw());
            float pitch = (float) Math.toRadians(camera.getPitch());

            shmWriter.writeListener(lx, ly, lz, yaw, pitch);

            // Write only requested players' positions (DLL tells us which names it needs)
            var requestedNames = shmWriter.readRequestedNames();
            var players = client.world.getPlayers();
            var localPlayer = client.player;
            String[] ids = new String[requestedNames.size()];
            float[] xs = new float[requestedNames.size()];
            float[] ys = new float[requestedNames.size()];
            float[] zs = new float[requestedNames.size()];
            int count = 0;
            for (var player : players) {
                if (player == localPlayer) continue;
                String name = player.getName().getString();
                if (!requestedNames.contains(name)) continue;
                if (count >= ids.length) break;
                ids[count] = name;
                xs[count] = (float) player.getX();
                ys[count] = (float) player.getY();
                zs[count] = (float) player.getZ();
                count++;
            }
            shmWriter.writeSources(ids, xs, ys, zs, count);
            shmWriter.commit();
        });
    }


    private void startRotationWriter() {
        writerRunning = true;
        rotationWriter = new Thread(() -> {
            while (writerRunning) {
                var client = MinecraftClient.getInstance();
                if (client != null && client.player != null && shmOpen) {
                    float yaw = (float) Math.toRadians(client.player.getYaw());
                    float pitch = (float) Math.toRadians(client.player.getPitch());
                    shmWriter.writeLiveRotation(yaw, pitch);
                }
                try { Thread.sleep(2); } catch (InterruptedException e) { break; }
            }
        }, "cordlink-rotation-writer");
        rotationWriter.setDaemon(true);
        rotationWriter.start();
    }

    private void stopRotationWriter() {
        writerRunning = false;
        if (rotationWriter != null) {
            try { rotationWriter.join(100); } catch (InterruptedException ignored) {}
            rotationWriter = null;
        }
    }

    private void detectExistingInjection() {
        if (shmWriter.exists()) {
            LOGGER.info("Existing shared memory detected, reconnecting...");
            injected = true;
            shmOpen = shmWriter.open();
            if (shmOpen) {
                shmWriter.writeMasterVolume(masterVolume);
                startRotationWriter();
                LOGGER.info("Reconnected to existing DLL injection (shared memory OK)");
            }
        }
    }

    public int cmdInject() {
        var client = MinecraftClient.getInstance();

        if (syncing) {
            msg(client, "\u00a7eSyncing in progress...");
            return 1;
        }
        if (injected) {
            msg(client, "\u00a7eAlready synced");
            return 1;
        }
        if (shmWriter.exists()) {
            detectExistingInjection();
            if (injected) {
                msg(client, "\u00a7eAlready synced");
                return 1;
            }
        }

        if (!NativeExtractor.ensureBundledNatives()) {
            msg(client, "\u00a7cFailed to extract bundled native files. Check latest.log.");
            return 0;
        }

        var session = client.getSession();
        var uuid = session.getUuidOrNull();
        if (uuid == null) {
            msg(client, "\u00a7cCould not determine player UUID.");
            return 0;
        }

        syncing = true;
        msg(client, "\u00a7eChecking Discord link...");
        String uuidStr = uuid.toString().replace("-", "");
        Thread.ofVirtual().start(() -> {
            try {
                try {
                    var req = HttpRequest.newBuilder()
                            .uri(URI.create(API_BASE + "/api/link-by-uuid/" + uuidStr))
                            .GET()
                            .timeout(java.time.Duration.ofSeconds(5))
                            .build();
                    var resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
                    if (resp.statusCode() != 200) {
                        syncing = false;
                        client.execute(() -> msg(client, "\u00a7cNot linked. Use /link in Discord first."));
                        return;
                    }
                } catch (Exception e) {
                    client.execute(() -> msg(client, "\u00a7eCould not reach server, proceeding anyway..."));
                }
                client.execute(() -> doInject(client));
            } catch (Exception e) {
                LOGGER.error("Injection failed", e);
                syncing = false;
                client.execute(() -> msg(client, "\u00a7cInjection failed unexpectedly."));
            }
        });
        return 1;
    }

    private void doInject(MinecraftClient client) {
        NativeExtractor.deployOpenAL(discordVariant);
        String dllPath = NativeExtractor.getDllPath();

        var pids = DllInjector.findDiscordPids(discordVariant);
        if (pids.isEmpty()) {
            syncing = false;
            msg(client, "\u00a7cDiscord process not found");
            return;
        }

        int success = 0;
        for (long pid : pids) {
            if (DllInjector.inject((int) pid, dllPath)) {
                success++;
                LOGGER.info("Injected into PID {}", pid);
            }
        }

        if (success > 0) {
            injected = true;
            shmOpen = shmWriter.open();
            if (shmOpen) {
                shmWriter.writeMasterVolume(masterVolume);
                startRotationWriter();
                msg(client, "\u00a7aInjected (%d/%d, SHM OK)".formatted(success, pids.size()));
                msg(client, "\u00a7eLeave and rejoin the voice channel for it to work properly.");
            } else {
                msg(client, "\u00a7eInjected (%d/%d, SHM failed)".formatted(success, pids.size()));
            }
        } else {
            msg(client, "\u00a7cInjection failed (run as admin?)");
        }
        syncing = false;
    }

    public int cmdUnload() {
        var client = MinecraftClient.getInstance();
        if (!injected) {
            msg(client, "\u00a7cNot injected");
            return 0;
        }
        sendText("Q");
        stopRotationWriter();
        shmWriter.close();
        shmOpen = false;
        injected = false;
        msg(client, "\u00a7aUnload signal sent");
        return 1;
    }

    private void sendText(String message) {
        try {
            if (udpSocket == null) {
                udpSocket = new DatagramSocket();
                localhost = InetAddress.getByName("127.0.0.1");
            }
            byte[] data = message.getBytes(StandardCharsets.UTF_8);
            packet.setData(data, 0, data.length);
            packet.setAddress(localhost);
            packet.setPort(UDP_PORT);
            udpSocket.send(packet);
        } catch (Exception e) {
            // silent
        }
    }

    private void msg(MinecraftClient client, String text) {
        if (client.player != null) {
            client.player.sendMessage(Text.literal("[Cordlink] " + text), false);
        }
    }
}
