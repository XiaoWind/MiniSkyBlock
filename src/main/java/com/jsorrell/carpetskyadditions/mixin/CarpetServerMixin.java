package com.jsorrell.carpetskyadditions.mixin;

import carpet.CarpetServer;
import com.jsorrell.carpetskyadditions.gen.SkyBlockChunkGenerator;
import com.jsorrell.carpetskyadditions.util.SpawnPlatformState;
import com.jsorrell.carpetskyadditions.util.SpawnSigns;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Runs right after carpet has loaded its settings, so the info sign coordinates are computed with the configured
 * border size instead of the compiled-in default.
 */
@Mixin(CarpetServer.class)
public class CarpetServerMixin {
    @Inject(method = "onServerLoadedWorlds", at = @At("TAIL"))
    private static void setupInfoSignCoordinates(MinecraftServer server, CallbackInfo ci) {
        // Only fill in the coordinates when the spawn platform was just generated (first startup).
        if (!SpawnPlatformState.wasPlaced()) {
            return;
        }
        ServerLevel overworld = server.overworld();
        if (overworld == null) {
            return;
        }
        ChunkGenerator generator = overworld.getChunkSource().getGenerator();
        if (generator instanceof SkyBlockChunkGenerator skyBlockGenerator) {
            SpawnSigns.setInfoSign(overworld, skyBlockGenerator);
        }
    }
}
