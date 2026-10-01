package com.jsorrell.carpetskyadditions.gen;

import com.jsorrell.carpetskyadditions.util.SkyAdditionsResourceLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;

/**
 * A structure piece that only carries a bounding box (no actual blocks). It is used to give the fixed
 * SkyBlock structures (ocean monument, pillager outpost, nether fortress) real, locatable structure
 * starts so that {@code /locate} and the vanilla mob-spawn overrides work, while the world itself stays
 * empty (SkyBlock).
 */
public class SkyBlockBoundingBoxPiece extends StructurePiece {
    public static final StructurePieceType TYPE = Registry.register(
            BuiltInRegistries.STRUCTURE_PIECE,
            new SkyAdditionsResourceLocation("bounding_box").getResourceLocation(),
            (context, tag) -> new SkyBlockBoundingBoxPiece(tag));

    public SkyBlockBoundingBoxPiece(BoundingBox boundingBox) {
        super(TYPE, 0, boundingBox);
    }

    private SkyBlockBoundingBoxPiece(CompoundTag tag) {
        super(TYPE, tag);
    }

    /** Forces registration of {@link #TYPE} during mod init (before registries are frozen). */
    public static void init() {}

    @Override
    protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {}

    @Override
    public void postProcess(
            WorldGenLevel level,
            StructureManager structureManager,
            ChunkGenerator generator,
            RandomSource random,
            BoundingBox box,
            ChunkPos chunkPos,
            BlockPos pos) {}
}
