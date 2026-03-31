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
        if (!Files.exists(CONFIG_PATH)) return;
        try (var in = Files.newInputStream(CONFIG_PATH)) {
            props.load(in);
            masterVolume = Float.parseFloat(props.getProperty("masterVolume", "1.0"));
            masterVolume = Math.max(0.0f, Math.min(2.0f, masterVolume));
        } catch (Exception ignored) {}
    }

    public static void save() {
        props.setProperty("masterVolume", String.valueOf(masterVolume));
        try (var out = Files.newOutputStream(CONFIG_PATH)) {
            props.store(out, "Cordlink Settings");
        } catch (IOException ignored) {}
    }
}
