package com.jsorrell.carpetskyadditions.util;

/**
 * Tracks whether the spawn platform was generated during the current world load. Mixin classes may
 * not contain non-private static fields, so this per-load flag lives in a plain class instead.
 */
public final class SpawnPlatformState {
    private static volatile boolean placed = false;

    private SpawnPlatformState() {}

    /** Resets the flag; called at the start of every world load. */
    public static void reset() {
        placed = false;
    }

    /** Marks the spawn platform as having just been generated. */
    public static void markPlaced() {
        placed = true;
    }

    /** Whether the spawn platform was generated during the current world load. */
    public static boolean wasPlaced() {
        return placed;
    }
}
