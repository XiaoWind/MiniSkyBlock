package com.jsorrell.carpetskyadditions.gen;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.stream.Stream;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;

/**
 * A SkyBlock biome source that guarantees every biome in the configured list appears within a
 * {@code grid_size} x {@code grid_size} chunk area around the origin. The arrangement of the biomes
 * is a seeded shuffle of a balanced multiset, so it varies with the world seed while still
 * guaranteeing full coverage. Chunks outside the grid tile the same layout.
 */
public class SkyBlockBiomeSource extends BiomeSource {
    public static final MapCodec<SkyBlockBiomeSource> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                    Biome.LIST_CODEC.fieldOf("biomes").forGetter(source -> source.allowedBiomes),
                    Codec.INT.optionalFieldOf("grid_size", 23).forGetter(source -> source.gridSize))
            .apply(instance, (biomes, gridSize) -> new SkyBlockBiomeSource(biomes, gridSize)));

    private final HolderSet<Biome> allowedBiomes;
    private final int gridSize;

    private volatile int[] layout;

    public SkyBlockBiomeSource(HolderSet<Biome> allowedBiomes, int gridSize) {
        this.allowedBiomes = allowedBiomes;
        this.gridSize = Math.max(1, gridSize);
    }

    @Override
    protected MapCodec<? extends BiomeSource> codec() {
        return CODEC;
    }

    @Override
    protected Stream<Holder<Biome>> collectPossibleBiomes() {
        return this.allowedBiomes.stream();
    }

    @Override
    public Holder<Biome> getNoiseBiome(int x, int y, int z, Climate.Sampler sampler) {
        int[] cells = ensureLayout(sampler);
        int gx = Math.floorMod(SectionPos.blockToSectionCoord(x), gridSize);
        int gz = Math.floorMod(SectionPos.blockToSectionCoord(z), gridSize);
        return this.allowedBiomes.get(cells[gx * gridSize + gz]);
    }

    private int[] ensureLayout(Climate.Sampler sampler) {
        int[] cells = this.layout;
        if (cells == null) {
            synchronized (this) {
                if (this.layout == null) {
                    this.layout = buildLayout(deriveSeed(sampler));
                }
                cells = this.layout;
            }
        }
        return cells;
    }

    /** Derives a stable, world-seed-dependent value from the climate sampler. */
    private long deriveSeed(Climate.Sampler sampler) {
        Climate.TargetPoint point = sampler.sample(0, 64, 0);
        long seed = point.temperature();
        seed ^= Long.rotateLeft(point.humidity(), 11);
        seed ^= Long.rotateLeft(point.continentalness(), 23);
        seed ^= Long.rotateLeft(point.erosion(), 37);
        seed ^= Long.rotateLeft(point.depth(), 47);
        seed ^= Long.rotateLeft(point.weirdness(), 59);
        return seed;
    }

    /** Builds a balanced shuffled layout so every biome is guaranteed to appear. */
    private int[] buildLayout(long seed) {
        int n = gridSize * gridSize;
        int biomeCount = allowedBiomes.size();
        List<Integer> cells = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            cells.add(i % biomeCount);
        }
        Collections.shuffle(cells, new Random(seed));
        int[] layout = new int[n];
        for (int i = 0; i < n; i++) {
            layout[i] = cells.get(i);
        }
        return layout;
    }
}
