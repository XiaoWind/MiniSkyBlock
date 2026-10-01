package com.jsorrell.carpetskyadditions.util;

import com.jsorrell.carpetskyadditions.gen.SkyBlockChunkGenerator;
import com.jsorrell.carpetskyadditions.settings.SkyAdditionsSettings;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.SignBlockEntity;

/** Writes the dynamic text of the two spawn platform signs. */
public final class SpawnSigns {
    private SpawnSigns() {}

    /**
     * Writes the world seed onto the back of the spawn platform sign (line 1 "minihud-seed", line 2 the seed).
     * The seed sign sits at relative (3,1,3) inside the 9x9x9 platform.
     */
    public static void setSeedSign(ServerLevel level, BlockPos worldSpawn) {
        BlockPos origin = platformOrigin(worldSpawn);
        BlockPos seedSignPos = origin.offset(3, 1, 3);
        String seed = String.valueOf(level.getSeed());
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = 0; x < 9; x++) {
            for (int y = 0; y < 9; y++) {
                for (int z = 0; z < 9; z++) {
                    pos.set(origin.getX() + x, origin.getY() + y, origin.getZ() + z);
                    if (level.getBlockEntity(pos) instanceof SignBlockEntity sign && pos.equals(seedSignPos)) {
                        sign.setText(
                                sign.getBackText()
                                        .setMessage(0, Component.literal("minihud-seed"))
                                        .setMessage(1, Component.literal(seed))
                                        .setMessage(2, Component.empty())
                                        .setMessage(3, Component.empty()),
                                false);
                    }
                }
            }
        }
    }

    /**
     * Writes the structure coordinates onto the mirrored info sign (relative (5,1,5) inside the platform): ocean
     * monument coordinates on its front, pillager outpost coordinates on its back.
     */
    public static void setInfoSign(ServerLevel level, SkyBlockChunkGenerator generator) {
        BlockPos worldSpawn = level.getRespawnData().pos();
        BlockPos origin = platformOrigin(worldSpawn);
        BlockPos infoSignPos = origin.offset(5, 1, 5);
        BlockPos monumentPos = generator.getMonumentDisplayPos(level.getSeed());
        BlockPos outpostPos = generator.getOutpostDisplayPos(level.getSeed());
        if (level.getBlockEntity(infoSignPos) instanceof SignBlockEntity sign) {
            sign.setText(
                    sign.getFrontText()
                            .setMessage(0, Component.literal("海底神殿"))
                            .setMessage(1, Component.literal(coordinates(monumentPos)))
                            .setMessage(2, Component.empty())
                            .setMessage(3, Component.empty()),
                    true);
            sign.setText(
                    sign.getBackText()
                            .setMessage(0, Component.literal("掠夺者前哨站"))
                            .setMessage(1, Component.literal(coordinates(outpostPos)))
                            .setMessage(2, Component.empty())
                            .setMessage(3, Component.empty()),
                    false);
        } else {
            SkyAdditionsSettings.LOG.error("Couldn't find spawn platform info sign at {}", infoSignPos);
        }
    }

    /** World position of the spawn platform structure origin; matches the spawn_platform.json config. */
    private static BlockPos platformOrigin(BlockPos worldSpawn) {
        return worldSpawn.atY(0).offset(-4, 63, -1);
    }

    private static String coordinates(BlockPos pos) {
        return "X: " + pos.getX() + ", Z: " + pos.getZ();
    }
}
