package com.cordlink;

import net.fabricmc.loader.api.FabricLoader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public class CordlinkConfig {
    private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("cordlink.properties");
    private static final Properties props = new Properties();

    public static float masterVolume = 1.0f;

    public static void load() {
        org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger("cordlink");
        log.info("[Config] load() path={} exists={}", CONFIG_PATH, Files.exists(CONFIG_PATH));
        if (!Files.exists(CONFIG_PATH)) return;
        try (var in = Files.newInputStream(CONFIG_PATH)) {
            props.load(in);
            String raw = props.getProperty("masterVolume", "1.0");
            log.info("[Config] raw masterVolume={}", raw);
            masterVolume = Float.parseFloat(raw);
            masterVolume = Math.max(0.0f, Math.min(2.0f, masterVolume));
            log.info("[Config] loaded masterVolume={}", masterVolume);
        } catch (Exception e) {
            log.warn("[Config] load failed", e);
        }
    }

    public static void save() {
        props.setProperty("masterVolume", String.valueOf(masterVolume));
        try (var out = Files.newOutputStream(CONFIG_PATH)) {
            props.store(out, "Cordlink Settings");
        } catch (IOException ignored) {}
    }
}
