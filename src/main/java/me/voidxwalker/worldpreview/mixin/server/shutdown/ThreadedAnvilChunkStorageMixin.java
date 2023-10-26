package me.voidxwalker.worldpreview.mixin.server.shutdown;

import com.mojang.datafixers.DataFixer;
import me.voidxwalker.worldpreview.IFastCloseable;
import me.voidxwalker.worldpreview.mixin.access.SerializingRegionBasedStorageAccessor;
import me.voidxwalker.worldpreview.mixin.access.VersionedChunkStorageAccessor;
import net.minecraft.server.world.ChunkTaskPrioritySystem;
import net.minecraft.server.world.ThreadedAnvilChunkStorage;
import net.minecraft.world.poi.PointOfInterestStorage;
import net.minecraft.world.storage.VersionedChunkStorage;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import java.io.File;
import java.io.IOException;

@Mixin(ThreadedAnvilChunkStorage.class)
public abstract class ThreadedAnvilChunkStorageMixin extends VersionedChunkStorage implements IFastCloseable {
    @Shadow
    @Final
    private ChunkTaskPrioritySystem chunkTaskPrioritySystem;

    @Shadow
    @Final
    private PointOfInterestStorage pointOfInterestStorage;

    public ThreadedAnvilChunkStorageMixin(File file, DataFixer dataFixer, boolean bl) {
        super(file, dataFixer, bl);
    }

    @Override
    public void fastClose() throws IOException {
        try {
            this.chunkTaskPrioritySystem.close();
            ((IFastCloseable) ((SerializingRegionBasedStorageAccessor) this.pointOfInterestStorage).getWorker()).fastClose();
        } finally {
            ((IFastCloseable) ((VersionedChunkStorageAccessor) this).getWorker()).fastClose();
        }
    }
}
