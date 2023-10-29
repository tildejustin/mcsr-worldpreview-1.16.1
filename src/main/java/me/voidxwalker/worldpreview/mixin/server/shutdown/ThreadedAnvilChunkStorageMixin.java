package me.voidxwalker.worldpreview.mixin.server.shutdown;

import me.voidxwalker.worldpreview.interfaces.FastCloseable;
import me.voidxwalker.worldpreview.mixin.access.SerializingRegionBasedStorageAccessor;
import me.voidxwalker.worldpreview.mixin.access.VersionedChunkStorageAccessor;
import net.minecraft.server.world.ChunkTaskPrioritySystem;
import net.minecraft.server.world.ThreadedAnvilChunkStorage;
import net.minecraft.world.poi.PointOfInterestStorage;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import java.io.IOException;

@Mixin(ThreadedAnvilChunkStorage.class)
public abstract class ThreadedAnvilChunkStorageMixin implements FastCloseable {
    @Shadow
    @Final
    private ChunkTaskPrioritySystem chunkTaskPrioritySystem;

    @Shadow
    @Final
    private PointOfInterestStorage pointOfInterestStorage;

    @Override
    public void worldpreview$fastClose() throws IOException {
        try {
            this.chunkTaskPrioritySystem.close();
            ((FastCloseable) ((SerializingRegionBasedStorageAccessor) this.pointOfInterestStorage).getWorker()).worldpreview$fastClose();
        } finally {
            ((FastCloseable) ((VersionedChunkStorageAccessor) this).getWorker()).worldpreview$fastClose();
        }
    }
}
