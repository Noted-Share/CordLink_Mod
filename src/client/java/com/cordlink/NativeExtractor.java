package com.cordlink;

import net.minecraft.client.MinecraftClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public class NativeExtractor {
    private static final Logger LOGGER = LoggerFactory.getLogger("cordlink");
    private static final String RESOURCE_ROOT = "/natives/windows-x86_64/";
    private static final String[] BUNDLED_NATIVE_NAMES = {"PJ_DM_Core.dll", "OpenAL32.dll"};
    private static Path nativesDir;
    private static volatile boolean nativesReady = false;

    public static Path getNativesDir() {
        if (nativesDir == null) {
            nativesDir = MinecraftClient.getInstance().runDirectory.toPath().resolve("cordlink");
        }
        return nativesDir;
    }

    public static String getDllPath() {
        ensureBundledNatives();
        return getNativesDir().resolve("PJ_DM_Core.dll").toString();
    }

    public static boolean dllExists() {
        ensureBundledNatives();
        return Files.exists(getNativesDir().resolve("PJ_DM_Core.dll"));
    }

    public static boolean openalExists() {
        ensureBundledNatives();
        return Files.exists(getNativesDir().resolve("OpenAL32.dll"));
    }

    public static boolean ensureBundledNatives() {
        if (nativesReady) return true;

        synchronized (NativeExtractor.class) {
            if (nativesReady) return true;

            try {
                Files.createDirectories(getNativesDir());
            } catch (IOException e) {
                LOGGER.error("Failed to create native directory {}", getNativesDir(), e);
                return false;
            }

            for (String fileName : BUNDLED_NATIVE_NAMES) {
                if (!extractBundledNative(fileName)) {
                    return false;
                }
            }

            nativesReady = true;
            return true;
        }
    }

    private static boolean extractBundledNative(String fileName) {
        Path target = getNativesDir().resolve(fileName);

        try (var in = NativeExtractor.class.getResourceAsStream(RESOURCE_ROOT + fileName)) {
            if (in == null) {
                LOGGER.error("Bundled native {} not found inside mod jar", fileName);
                return false;
            }

            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            return true;
        } catch (IOException e) {
            LOGGER.error("Failed to extract bundled native {}", fileName, e);
            return false;
        }
    }

    /** Find the directory where the given Discord variant is running from. */
    public static Path findDiscordDir(String variant) {
        return ProcessHandle.allProcesses()
                .filter(p -> p.info().command().orElse("").toLowerCase().endsWith(variant.toLowerCase()))
                .map(p -> Path.of(p.info().command().get()).getParent())
                .findFirst()
                .orElse(null);
    }

    /** Copy OpenAL32.dll from cordlink folder to Discord's directory. */
    public static boolean deployOpenAL(String variant) {
        if (!ensureBundledNatives()) return false;

        Path src = getNativesDir().resolve("OpenAL32.dll");
        if (!Files.exists(src)) return false;

        Path discordDir = findDiscordDir(variant);
        if (discordDir == null) {
            LOGGER.warn("Discord directory not found");
            return false;
        }

        Path target = discordDir.resolve("OpenAL32.dll");
        try {
            Files.copy(src, target, StandardCopyOption.REPLACE_EXISTING);
            LOGGER.info("Deployed OpenAL32.dll to {}", discordDir);
            return true;
        } catch (IOException e) {
            LOGGER.error("Failed to copy OpenAL32.dll to Discord directory", e);
            return false;
        }
    }
}
