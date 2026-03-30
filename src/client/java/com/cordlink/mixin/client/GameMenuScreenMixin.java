package com.cordlink.mixin.client;

import com.cordlink.CordlinkScreen;
import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameMenuScreen.class)
public abstract class GameMenuScreenMixin extends Screen {
    protected GameMenuScreenMixin(Text title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void addCordlinkButton(CallbackInfo ci) {
        // Find the Statistics button and shrink it
        ClickableWidget statsBtn = null;
        for (var child : this.children()) {
            if (child instanceof ClickableWidget w) {
                String text = w.getMessage().getString();
                if (text.contains("Statistics") || text.contains("통계")) {
                    statsBtn = w;
                    break;
                }
            }
        }

        if (statsBtn != null) {
            int origX = statsBtn.getX();
            int origW = statsBtn.getWidth();
            int origY = statsBtn.getY();
            int origH = statsBtn.getHeight();
            int halfW = origW / 2 - 1;

            // Shrink stats button to left half
            statsBtn.setWidth(halfW);

            // Place Cordlink button in right half
            addDrawableChild(ButtonWidget.builder(Text.literal("Cordlink"), btn -> {
                if (this.client != null) {
                    this.client.setScreen(new CordlinkScreen());
                }
            }).dimensions(origX + halfW + 2, origY, halfW, origH).build());
        } else {
            // Fallback: bottom center
            addDrawableChild(ButtonWidget.builder(Text.literal("Cordlink"), btn -> {
                if (this.client != null) {
                    this.client.setScreen(new CordlinkScreen());
                }
            }).dimensions(this.width / 2 - 40, this.height - 28, 80, 20).build());
        }
    }
}
