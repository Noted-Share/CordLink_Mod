package com.cordlink;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Util;

public class CordlinkScreen extends Screen {
    private String statusMsg = "";
    private int statusColor = 0xFFFFFF;

    private static final String[] DISCORD_VARIANTS = {"discord.exe", "discordptb.exe", "discordcanary.exe"};
    private static final String[] DISCORD_LABELS = {"Discord", "Discord PTB", "Discord Canary"};
    private int variantIndex = 0;
    private ButtonWidget variantBtn;
    private ButtonWidget syncBtn;

    public CordlinkScreen() {
        super(Text.literal("Cordlink"));
    }

    @Override
    protected void init() {
        var mod = CordlinkClient.getInstance();
        int centerX = this.width / 2;
        int y = 50;

        // Restore selected variant from mod
        for (int i = 0; i < DISCORD_VARIANTS.length; i++) {
            if (DISCORD_VARIANTS[i].equals(mod.getDiscordVariant())) {
                variantIndex = i;
                break;
            }
        }

        // Discord variant selector
        variantBtn = ButtonWidget.builder(Text.literal("Target: " + DISCORD_LABELS[variantIndex]), btn -> {
            variantIndex = (variantIndex + 1) % DISCORD_VARIANTS.length;
            mod.setDiscordVariant(DISCORD_VARIANTS[variantIndex]);
            btn.setMessage(Text.literal("Target: " + DISCORD_LABELS[variantIndex]));
        }).dimensions(centerX - 105, y, 210, 20).build();
        addDrawableChild(variantBtn);

        y += 28;

        // Inject / Unload buttons
        syncBtn = ButtonWidget.builder(Text.literal("Sync"), btn -> {
            int result = mod.cmdInject();
            if (result == 0) {
                setStatus("Injection failed", 0xFF5555);
            } else {
                setStatus("Syncing...", 0xFFFF55);
            }
        }).dimensions(centerX - 105, y, 100, 20).build();
        addDrawableChild(syncBtn);

        addDrawableChild(ButtonWidget.builder(Text.literal("UnSync"), btn -> {
            int result = mod.cmdUnload();
            if (result == 0) {
                setStatus("Not injected", 0xFF5555);
            } else {
                refreshStatus();
            }
        }).dimensions(centerX + 5, y, 100, 20).build());

        y += 28;

        // Master volume slider (0% - 200%)
        double initVal = mod.getMasterVolume() / 2.0;
        addDrawableChild(new SliderWidget(centerX - 105, y, 210, 20, Text.literal("Volume: " + (int)(mod.getMasterVolume() * 100) + "%"), initVal) {
            @Override
            protected void updateMessage() {
                int pct = (int)(this.value * 200);
                this.setMessage(Text.literal("Volume: " + pct + "%"));
            }

            @Override
            protected void applyValue() {
                float vol = (float)(this.value * 2.0);
                CordlinkClient.getInstance().setMasterVolume(vol);
            }
        });

        y += 28;

        // Discord button
        addDrawableChild(ButtonWidget.builder(Text.literal("Discord"), btn -> {
            Util.getOperatingSystem().open(java.net.URI.create(CordlinkClient.DISCORD_INVITE));
        }).dimensions(centerX - 50, y, 100, 20).build());

        y += 28;

        // Done button
        addDrawableChild(ButtonWidget.builder(Text.literal("Done"), btn -> this.close())
                .dimensions(centerX - 50, y, 100, 20).build());

        refreshStatus();
    }

    private void refreshStatus() {
        var mod = CordlinkClient.getInstance();
        if (mod.isSyncing()) {
            setStatus("Syncing...", 0xFFFF55);
        } else if (mod.isInjected() && mod.isShmOpen()) {
            setStatus("Injected (SHM OK)", 0x55FF55);
        } else if (mod.isInjected()) {
            setStatus("Injected (SHM failed)", 0xFFFF55);
        } else if (!NativeExtractor.dllExists() || !NativeExtractor.openalExists()) {
            setStatus("Bundled natives unavailable", 0xFFAA00);
        } else {
            setStatus("Not injected", 0xFF5555);
        }
    }

    private void setStatus(String msg, int color) {
        this.statusMsg = msg;
        this.statusColor = color;
    }

    @Override
    public void tick() {
        var mod = CordlinkClient.getInstance();
        boolean locked = mod.isInjected() || mod.isSyncing();
        variantBtn.active = !locked;
        syncBtn.active = !locked;
        refreshStatus();
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        int centerX = this.width / 2;

        context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("Cordlink"), centerX, 20, 0xFFFFFF);
        context.drawCenteredTextWithShadow(this.textRenderer, Text.literal(statusMsg), centerX, 35, statusColor);

    }

    @Override
    public void close() {
        if (this.client != null) {
            this.client.setScreen(new GameMenuScreen(true));
        }
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
