package com.jsorrell.carpetskyadditions.gen;

import com.jsorrell.carpetskyadditions.mixin.ChunkGeneratorAccessor;
import com.jsorrell.carpetskyadditions.mixin.JigsawStructureAccessor;
import com.jsorrell.carpetskyadditions.mixin.SinglePoolElementAccessor;
import com.jsorrell.carpetskyadditions.settings.SkyAdditionsSettings;
import com.jsorrell.carpetskyadditions.util.SkyBlockBorder;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.ints.IntArraySet;
import it.unimi.dsi.fastutil.objects.ObjectArraySet;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import net.minecraft.CrashReport;
import net.minecraft.ReportedException;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.FeatureSorter;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.*;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.levelgen.structure.*;
import net.minecraft.world.level.levelgen.structure.pieces.PiecesContainer;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.minecraft.world.level.levelgen.structure.pools.SinglePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.structures.JigsawStructure;
import net.minecraft.world.level.levelgen.structure.structures.StrongholdStructure;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EndPortalFrameBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import org.jetbrains.annotations.NotNull;

public class SkyBlockChunkGenerator extends NoiseBasedChunkGenerator {
    public static final MapCodec<SkyBlockChunkGenerator> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                    BiomeSource.CODEC.fieldOf("biome_source").forGetter(SkyBlockChunkGenerator::getBiomeSource),
                    NoiseGeneratorSettings.CODEC
                            .fieldOf("settings")
                            .forGetter(SkyBlockChunkGenerator::generatorSettings))
            .apply(instance, instance.stable(SkyBlockChunkGenerator::new)));

    // Random (seed-dependent), non-overlapping positions for the overworld structures. Computed lazily per world.
    private volatile boolean positionsComputed = false;
    private ChunkPos portalChunk = new ChunkPos(2, 2);
    private FixedStructure monumentStructure =
            new FixedStructure(structureKey("monument"), new ChunkPos(5, 5), 64, 39, 64, 121, 61, 121);
    private FixedStructure outpostStructure =
            new FixedStructure(structureKey("pillager_outpost"), new ChunkPos(8, 8), 128, 63, 128, 151, 85, 151);

    private static final List<FixedStructure> NETHER_STRUCTURES = List.of(
            // Nether fortress: large bounding box around the nether surface (Y 32-96).
            new FixedStructure(structureKey("fortress"), new ChunkPos(4, 4), 32, 32, 32, 111, 96, 111));

    private record FixedStructure(
            ResourceKey<Structure> key,
            ChunkPos anchor,
            int minX,
            int minY,
            int minZ,
            int maxX,
            int maxY,
            int maxZ) {}

    private static ResourceKey<Structure> structureKey(String path) {
        return ResourceKey.create(Registries.STRUCTURE, Identifier.withDefaultNamespace(path));
    }

    /**
     * Picks random, seed-dependent, non-overlapping positions for the end portal, ocean monument and pillager outpost.
     * Footprints in chunks: portal 1x1, monument 4x4 (58x58 blocks), outpost 2x2 (24x24 blocks).
     */
    private void ensureStructurePositions(long worldSeed) {
        if (positionsComputed) {
            return;
        }
        synchronized (this) {
            if (positionsComputed) {
                return;
            }
            Random random = new Random(worldSeed);
            int minC = -SkyBlockBorder.chunkRadius();
            int maxC = SkyBlockBorder.chunkMax();

            // End portal: 1x1 chunk footprint, with a one-chunk margin inside the border.
            int px = minC + 1 + random.nextInt(maxC - minC - 1);
            int pz = minC + 1 + random.nextInt(maxC - minC - 1);
            portalChunk = new ChunkPos(px, pz);

            // Ocean monument: 4x4 chunk footprint, not overlapping the portal.
            int mx;
            int mz;
            do {
                mx = minC + random.nextInt(maxC - minC - 2);
                mz = minC + random.nextInt(maxC - minC - 2);
            } while (chunksOverlap(px, pz, 1, 1, mx, mz, 4, 4));
            monumentStructure = new FixedStructure(
                    structureKey("monument"),
                    new ChunkPos(mx + 1, mz + 1),
                    mx * 16, 39, mz * 16,
                    mx * 16 + 57, 61, mz * 16 + 57);

            // Pillager outpost: 2x2 chunk footprint, not overlapping the portal or the monument.
            int ox;
            int oz;
            do {
                ox = minC + random.nextInt(maxC - minC);
                oz = minC + random.nextInt(maxC - minC);
            } while (chunksOverlap(px, pz, 1, 1, ox, oz, 2, 2)
                    || chunksOverlap(mx, mz, 4, 4, ox, oz, 2, 2));
            outpostStructure = new FixedStructure(
                    structureKey("pillager_outpost"),
                    new ChunkPos(ox, oz),
                    ox * 16, 63, oz * 16,
                    ox * 16 + 23, 85, oz * 16 + 23);

            positionsComputed = true;
        }
    }

    private static boolean chunksOverlap(int ax, int az, int aw, int ad, int bx, int bz, int bw, int bd) {
        return ax < bx + bw && bx < ax + aw && az < bz + bd && bz < az + ad;
    }

    /** Block position (bounding box center) shown on the spawn info sign for the ocean monument. */
    public BlockPos getMonumentDisplayPos(long worldSeed) {
        ensureStructurePositions(worldSeed);
        return centerOf(monumentStructure);
    }

    /** Block position (bounding box center) shown on the spawn info sign for the pillager outpost. */
    public BlockPos getOutpostDisplayPos(long worldSeed) {
        ensureStructurePositions(worldSeed);
        return centerOf(outpostStructure);
    }

    private static BlockPos centerOf(FixedStructure structure) {
        return new BlockPos(
                (structure.minX() + structure.maxX()) / 2,
                (structure.minY() + structure.maxY()) / 2,
                (structure.minZ() + structure.maxZ()) / 2);
    }

    public SkyBlockChunkGenerator(BiomeSource biomeSource, Holder<NoiseGeneratorSettings> settings) {
        super(biomeSource, settings);
    }

    @Override
    protected @NotNull MapCodec<? extends ChunkGenerator> codec() {
        return CODEC;
    }


    @Override
    public void buildSurface(
            WorldGenRegion level, StructureManager structureManager, RandomState random, ChunkAccess chunk) {}

    @Override
    public CompletableFuture<ChunkAccess> fillFromNoise(
            Blender blender,
            RandomState random,
            StructureManager structureManager,
            ChunkAccess chunk) {
        fillBorderWalls(chunk);
        return CompletableFuture.completedFuture(chunk);
    }

    /** Fills everything outside the 23x23 chunk playable area with bedrock (top to bottom). */
    private void fillBorderWalls(ChunkAccess chunk) {
        if (!(biomeSource instanceof SkyBlockBiomeSource)) {
            return; // The End dimension is unrestricted.
        }
        int radius = SkyBlockBorder.chunkRadius();
        int maxChunk = SkyBlockBorder.chunkMax();
        int cx = chunk.getPos().x();
        int cz = chunk.getPos().z();
        if (cx >= -radius && cx <= maxChunk && cz >= -radius && cz <= maxChunk) {
            return; // Inside the playable area: leave void.
        }
        int minY = chunk.getMinY();
        int maxY = minY + chunk.getHeight() - 1;
        int x0 = chunk.getPos().getMinBlockX();
        int z0 = chunk.getPos().getMinBlockZ();
        BlockState bedrock = Blocks.BEDROCK.defaultBlockState();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = minY; y <= maxY; y++) {
                    chunk.setBlockState(pos.set(x0 + x, y, z0 + z), bedrock);
                }
            }
        }
    }

    @Override
    public void applyCarvers(
        WorldGenRegion worldGenRegion, long l, RandomState randomState, BiomeManager biomeManager, StructureManager structureManager, ChunkAccess chunkAccess) {}

    @Override
    public void applyBiomeDecoration(WorldGenLevel level, ChunkAccess chunk, StructureManager structureManager) {
        ChunkPos chunkPos = chunk.getPos();
        if (chunkPos.equals(portalChunk) && isOverworldSource()) {
            placeEndPortal(level, portalChunk);
        }
        SectionPos sectionPos = SectionPos.of(chunkPos, level.getMinSectionY());
        BlockPos minChunkPos = sectionPos.origin();
        Registry<Structure> structureRegistry = level.registryAccess().lookupOrThrow(Registries.STRUCTURE);
        Map<Integer, List<Structure>> structuresPerStep = structureRegistry.stream()
                .collect(Collectors.groupingBy(
                        structureType -> structureType.step().ordinal()));
        List<FeatureSorter.StepFeatureData> featuresPerStep =
                ((ChunkGeneratorAccessor) this).getFeaturesPerStep().get();

        WorldgenRandom random = new WorldgenRandom(new XoroshiroRandomSource(RandomSupport.generateUniqueSeed()));
        long decorationSeed = random.setDecorationSeed(level.getSeed(), minChunkPos.getX(), minChunkPos.getZ());

        // Get all surrounding biomes for biome-based structures
        Set<Holder<Biome>> biomeSet = new ObjectArraySet<>();
        ChunkPos.rangeClosed(sectionPos.chunk(), 1).forEach(curChunkPos -> {
            ChunkAccess curChunk = level.getChunk(curChunkPos.x(), curChunkPos.z());
            for (LevelChunkSection chunkSection : curChunk.getSections()) {
                chunkSection.getBiomes().getAll(biomeSet::add);
            }
        });
        biomeSet.retainAll(biomeSource.possibleBiomes());

        int numFeatures = featuresPerStep.size();
        try {
            Registry<PlacedFeature> placedFeatures = level.registryAccess().lookupOrThrow(Registries.PLACED_FEATURE);
            int numSteps = Math.max(GenerationStep.Decoration.values().length, numFeatures);
            for (int genStep = 0; genStep < numSteps; ++genStep) {
                int structureInStep = 0;
                if (structureManager.shouldGenerateStructures()) {
                    List<Structure> structuresForStep =
                            structuresPerStep.getOrDefault(genStep, Collections.emptyList());
                    for (Structure structure : structuresForStep) {
                        random.setFeatureSeed(decorationSeed, structureInStep, genStep);
                        Supplier<String> structureNameSupplier = () -> structureRegistry
                                .getResourceKey(structure)
                                .map(Object::toString)
                                .orElseGet(structure::toString);
                        try {
                            // Stronghold
                            if (structure instanceof StrongholdStructure
                                    && (SkyAdditionsSettings.generateEndPortals
                                            || SkyAdditionsSettings.generateSilverfishSpawners)) {
                                level.setCurrentlyGenerating(structureNameSupplier);
                                structureManager
                                        .startsForStructure(sectionPos, structure)
                                        .forEach(structureStart -> {
                                            for (StructurePiece piece : structureStart.getPieces()) {
                                                if (piece.isCloseToChunk(chunkPos, 0)
                                                        && piece.getType()
                                                                == StructurePieceType.STRONGHOLD_PORTAL_ROOM) {
                                                    BoundingBox chunkBox =
                                                            ChunkGeneratorAccessor.getWritableArea(chunk);
                                                    if (SkyAdditionsSettings.generateEndPortals) {
                                                        new SkyBlockStructures.EndPortalStructure(piece)
                                                                .generate(level, chunkBox, random);
                                                    }

                                                    if (SkyAdditionsSettings.generateSilverfishSpawners) {
                                                        new SkyBlockStructures.SilverfishSpawnerStructure(piece)
                                                                .generate(level, chunkBox, random);
                                                    }
                                                }
                                            }
                                        });
                            } else if (structure instanceof JigsawStructure) {
                                Holder<StructureTemplatePool> startPool = ((JigsawStructureAccessor) structure).getStartPool();
                                // Bastion Remnants
                                if (SkyAdditionsSettings.generateMagmaCubeSpawners
                                        && startPool.is(Identifier.withDefaultNamespace("bastion/starts"))) {
                                    level.setCurrentlyGenerating(structureNameSupplier);
                                    structureManager
                                            .startsForStructure(sectionPos, structure)
                                            .forEach(structureStart -> {
                                                for (StructurePiece piece : structureStart.getPieces()) {
                                                    if (piece.isCloseToChunk(chunkPos, 0)
                                                            && piece instanceof PoolElementStructurePiece poolPiece) {
                                                        if (poolPiece.getElement()
                                                                instanceof SinglePoolElement singlePoolElement) {
                                                            Identifier pieceId = ((SinglePoolElementAccessor)
                                                                            singlePoolElement)
                                                                    .getTemplate()
                                                                    .left()
                                                                    .orElseThrow(AssertionError::new);
                                                            if (pieceId.equals(Identifier.withDefaultNamespace(
                                                                    "bastion/treasure/bases/lava_basin"))) {
                                                                new SkyBlockStructures.MagmaCubeSpawner(piece)
                                                                        .generate(
                                                                                level,
                                                                                ChunkGeneratorAccessor.getWritableArea(
                                                                                        chunk),
                                                                                random);
                                                            }
                                                        }
                                                    }
                                                }
                                            });
                                    // Ancient Cities
                                } else if (SkyAdditionsSettings.generateAncientCityPortals
                                        && startPool.is(Identifier.withDefaultNamespace("ancient_city/city_center"))) {
                                    level.setCurrentlyGenerating(structureNameSupplier);
                                    structureManager
                                            .startsForStructure(sectionPos, structure)
                                            .forEach(structureStart -> {
                                                for (StructurePiece piece : structureStart.getPieces()) {
                                                    if (piece.isCloseToChunk(chunkPos, 0)
                                                            && piece instanceof PoolElementStructurePiece poolPiece) {
                                                        if (poolPiece.getElement()
                                                                instanceof SinglePoolElement singlePoolElement) {
                                                            Identifier pieceId = ((SinglePoolElementAccessor)
                                                                            singlePoolElement)
                                                                    .getTemplate()
                                                                    .left()
                                                                    .orElseThrow(AssertionError::new);
                                                            if (pieceId.getNamespace()
                                                                            .equals("minecraft")
                                                                    && pieceId.getPath()
                                                                            .startsWith(
                                                                                    "ancient_city/city_center/city_center")) {
                                                                new SkyBlockStructures.AncientCityPortalStructure(piece)
                                                                        .generate(
                                                                                level,
                                                                                ChunkGeneratorAccessor.getWritableArea(
                                                                                        chunk),
                                                                                random);
                                                            }
                                                        }
                                                    }
                                                }
                                            });
                                }
                                //Trial Chambers
                                else if (SkyAdditionsSettings.generateTrialChambers && startPool.is(Identifier.withDefaultNamespace("trial_chambers/chamber/end"))){
                                    level.setCurrentlyGenerating(structureNameSupplier);
                                    structureManager
                                        .startsForStructure(sectionPos, structure)
                                        .forEach(structureStart -> {
                                            for (StructurePiece piece : structureStart.getPieces()) {
                                                if (piece.isCloseToChunk(chunkPos, 0)
                                                    && piece instanceof PoolElementStructurePiece poolPiece) {
                                                    if (poolPiece.getElement()
                                                        instanceof SinglePoolElement singlePoolElement) {
                                                        Identifier pieceId = ((SinglePoolElementAccessor)
                                                            singlePoolElement)
                                                            .getTemplate()
                                                            .left()
                                                            .orElseThrow(AssertionError::new);
                                                        if (pieceId.getNamespace()
                                                            .equals("minecraft")
                                                            && pieceId.getPath()
                                                            .startsWith(
                                                                "trial_chambers/corridor/entrance")) {
                                                            new SkyBlockStructures.TrialChamberEntrance(piece)
                                                                .generate(
                                                                    level,
                                                                    ChunkGeneratorAccessor.getWritableArea(
                                                                        chunk),
                                                                    random);
                                                        }/*else if (pieceId.getNamespace()
                                                            .equals("minecraft")
                                                            && pieceId.getPath()
                                                            .startsWith(
                                                                "trial_chambers/corridor/atrium")) {
                                                            new SkyBlockStructures.TrialChamberAtrium(piece)
                                                                .generate(
                                                                    level,
                                                                    ChunkGeneratorAccessor.getWritableArea(
                                                                        chunk),
                                                                    random);
                                                        }else if (pieceId.getNamespace()
                                                            .equals("minecraft")
                                                            && pieceId.getPath()
                                                            .startsWith(
                                                                "trial_chambers")) {
                                                            new SkyBlockStructures.TrialChamber(piece)
                                                                .generate(
                                                                    level,
                                                                    ChunkGeneratorAccessor.getWritableArea(
                                                                        chunk),
                                                                    random);
                                                        }*/
                                                    }
                                                }
                                            }
                                        });
                                }

                            }
                        } catch (Exception e) {
                            CrashReport crashReport = CrashReport.forThrowable(e, "Feature placement");
                            crashReport.addCategory("Feature").setDetail("Description", structureNameSupplier::get);
                            throw new ReportedException(crashReport);
                        }
                        ++structureInStep;
                    }
                }
                if (genStep >= numFeatures) continue;
                IntArraySet intSet = new IntArraySet();
                for (Holder<Biome> biome : biomeSet) {
                    List<HolderSet<PlacedFeature>> biomeFeatureStepList = ((ChunkGeneratorAccessor) this)
                            .getGenerationSettingsGetter()
                            .apply(biome)
                            .features();
                    if (genStep < biomeFeatureStepList.size()) {
                        HolderSet<PlacedFeature> biomeFeaturesForStep = biomeFeatureStepList.get(genStep);
                        FeatureSorter.StepFeatureData indexedFeature = featuresPerStep.get(genStep);
                        biomeFeaturesForStep.stream()
                                .map(Holder::value)
                                .forEach(placedFeature ->
                                        intSet.add(indexedFeature.indexMapping().applyAsInt(placedFeature)));
                    }
                }
                int n = intSet.size();
                int[] is = intSet.toIntArray();
                Arrays.sort(is);
                FeatureSorter.StepFeatureData indexedFeature = featuresPerStep.get(genStep);
                for (int o = 0; o < n; ++o) {
                    int p = is[o];
                    PlacedFeature placedFeature = indexedFeature.features().get(p);
                    Supplier<String> placedFeatureNameSupplier = () -> placedFeatures
                            .getResourceKey(placedFeature)
                            .map(Object::toString)
                            .orElseGet(placedFeature::toString);
                    random.setFeatureSeed(decorationSeed, p, genStep);
                    try {
                        // Random End Gateways
                        if (SkyAdditionsSettings.generateRandomEndGateways
                                && placedFeature.feature().is(Identifier.withDefaultNamespace("end_gateway_return"))) {
                            level.setCurrentlyGenerating(placedFeatureNameSupplier);
                            placedFeature.placeWithBiomeCheck(level, this, random, minChunkPos);
                        }
                    } catch (Exception e) {
                        CrashReport crashReport = CrashReport.forThrowable(e, "Feature placement");
                        crashReport.addCategory("Feature").setDetail("Description", placedFeatureNameSupplier::get);
                        throw new ReportedException(crashReport);
                    }
                }
            }
            level.setCurrentlyGenerating(null);
        } catch (Exception e) {
            CrashReport crashReport = CrashReport.forThrowable(e, "Biome decoration");
            crashReport
                    .addCategory("Generation")
                    .setDetail("CenterX", chunkPos.x())
                    .setDetail("CenterZ", chunkPos.z())
                    .setDetail("Seed", decorationSeed);
            throw new ReportedException(crashReport);
        }
    }

    @Override
    public void spawnOriginalMobs(WorldGenRegion level) {}

    /**
     * Places the SkyBlock structures (ocean monument, pillager outpost, nether fortress) as real structure starts at
     * random seed-dependent positions. Vanilla seed-dependent structure generation (including trial chambers) is
     * disabled; references are added by the vanilla {@link ChunkGenerator#createReferences}.
     */
    @Override
    public void createStructures(
            RegistryAccess registryAccess,
            ChunkGeneratorStructureState structureState,
            StructureManager structureManager,
            ChunkAccess chunk,
            StructureTemplateManager templateManager,
            ResourceKey<Level> dimensionKey) {
        ensureStructurePositions(structureState.getLevelSeed());
        List<FixedStructure> structures = dimensionKey == Level.OVERWORLD
                ? List.of(monumentStructure, outpostStructure)
                : dimensionKey == Level.NETHER ? NETHER_STRUCTURES : List.of();
        if (structures.isEmpty()) {
            return;
        }

        Registry<Structure> registry = registryAccess.lookupOrThrow(Registries.STRUCTURE);
        ChunkPos chunkPos = chunk.getPos();
        for (FixedStructure fixed : structures) {
            if (chunkPos.equals(fixed.anchor())) {
                Structure structure = registry.getValue(fixed.key());
                BoundingBox boundingBox = new BoundingBox(
                        fixed.minX(), fixed.minY(), fixed.minZ(), fixed.maxX(), fixed.maxY(), fixed.maxZ());
                StructureStart start = new StructureStart(
                        structure,
                        chunkPos,
                        0,
                        new PiecesContainer(List.of(new SkyBlockBoundingBoxPiece(boundingBox))));
                chunk.setStartForStructure(structure, start);
            }
        }
    }

    private boolean isOverworldSource() {
        if (!(biomeSource instanceof SkyBlockBiomeSource)) {
            return false;
        }
        return biomeSource.possibleBiomes().stream().anyMatch(h -> h.is(Biomes.PLAINS) || h.is(Biomes.BEACH));
    }

    /** Places only the 12 end portal frames (no eyes, no portal blocks) so the portal is not pre-activated. */
    private void placeEndPortal(WorldGenLevel level, ChunkPos portalChunk) {
        int minX = portalChunk.x() * 16 + 6;
        int minZ = portalChunk.z() * 16 + 6;
        int y = 64;

        BlockState frame = Blocks.END_PORTAL_FRAME.defaultBlockState().setValue(EndPortalFrameBlock.HAS_EYE, false);
        BlockState facingSouth = frame.setValue(EndPortalFrameBlock.FACING, Direction.SOUTH);
        BlockState facingNorth = frame.setValue(EndPortalFrameBlock.FACING, Direction.NORTH);
        BlockState facingEast = frame.setValue(EndPortalFrameBlock.FACING, Direction.EAST);
        BlockState facingWest = frame.setValue(EndPortalFrameBlock.FACING, Direction.WEST);

        // North edge (z = minZ) faces south toward the center.
        for (int i = 0; i < 3; i++) {
            level.setBlock(new BlockPos(minX + 1 + i, y, minZ), facingSouth, Block.UPDATE_CLIENTS);
        }
        // South edge (z = minZ + 4) faces north toward the center.
        for (int i = 0; i < 3; i++) {
            level.setBlock(new BlockPos(minX + 1 + i, y, minZ + 4), facingNorth, Block.UPDATE_CLIENTS);
        }
        // West edge (x = minX) faces east toward the center.
        for (int i = 0; i < 3; i++) {
            level.setBlock(new BlockPos(minX, y, minZ + 1 + i), facingEast, Block.UPDATE_CLIENTS);
        }
        // East edge (x = minX + 4) faces west toward the center.
        for (int i = 0; i < 3; i++) {
            level.setBlock(new BlockPos(minX + 4, y, minZ + 1 + i), facingWest, Block.UPDATE_CLIENTS);
        }
    }

    public int getBaseHeightInEquivalentNoiseWorld(int x, int z, Heightmap.Types heightmap, WorldGenLevel level) {
        RandomState randomState = RandomState.create(
                generatorSettings().value(),
                level.registryAccess().lookupOrThrow(Registries.NOISE),
                level.getSeed());
        return super.getBaseHeight(x, z, heightmap, level, randomState);
    }
}
