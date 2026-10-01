package com.jsorrell.carpetskyadditions.util;

import com.jsorrell.carpetskyadditions.settings.SkyAdditionsSettings;

/** Defines the geometry of the fixed SkyBlock border (a 23x23 chunk square centered on the origin). */
public final class SkyBlockBorder {
    private SkyBlockBorder() {}

    /** Half-width of the playable area (negative side). 30 chunks -> 15, i.e. chunk coords [-15, 14]. */
    public static int chunkRadius() {
        return Math.max(1, SkyAdditionsSettings.skyblockBorderChunks / 2);
    }

    /** Maximum chunk coordinate of the playable area (positive side). */
    public static int chunkMax() {
        return SkyAdditionsSettings.skyblockBorderChunks - 1 - chunkRadius();
    }
}
