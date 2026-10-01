package com.jsorrell.carpetskyadditions.mixin;

import com.jsorrell.carpetskyadditions.util.AdvancementTranslator;
import java.util.Map;
import net.minecraft.advancements.Advancement;
import net.minecraft.resources.Identifier;
import net.minecraft.server.ServerAdvancementManager;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerAdvancementManager.class)
public class ServerAdvancementManagerMixin {
    @Inject(
            method =
                    "apply(Ljava/util/Map;Lnet/minecraft/server/packs/resources/ResourceManager;"
                            + "Lnet/minecraft/util/profiling/ProfilerFiller;)V",
            at = @At("HEAD"))
    private void translateAdvancements(
            Map<Identifier, Advancement> advancements,
            ResourceManager resourceManager,
            ProfilerFiller profiler,
            CallbackInfo ci) {
        AdvancementTranslator.translateAll(advancements);
    }
}
