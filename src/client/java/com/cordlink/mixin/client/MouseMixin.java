package com.cordlink.mixin.client;

import com.cordlink.CordlinkClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.Mouse;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Mouse.class)
public class MouseMixin {
    @Inject(method = "onCursorPos", at = @At("TAIL"))
    private void cordlink$writeLiveRotation(long window, double x, double y, CallbackInfo ci) {
        var cordlink = CordlinkClient.getInstance();
        if (cordlink == null || !cordlink.isShmOpen()) return;
        var client = MinecraftClient.getInstance();
        if (client.player == null) return;
        float yaw = (float) Math.toRadians(client.player.getYaw());
        float pitch = (float) Math.toRadians(client.player.getPitch());
        cordlink.writeLiveRotation(yaw, pitch);
    }
}
