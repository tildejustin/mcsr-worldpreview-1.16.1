package me.voidxwalker.worldpreview.mixin.access;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import net.minecraft.server.network.EntityTrackerEntry;
import net.minecraft.server.world.ChunkHolder;
import net.minecraft.server.world.ThreadedAnvilChunkStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ThreadedAnvilChunkStorage.class)
public interface ThreadedAnvilChunkStorageAccessor {
    @Accessor
    Long2ObjectLinkedOpenHashMap<ChunkHolder> getChunkHolders();

    @Accessor
    Int2ObjectMap<?> getEntityTrackers();

    @Mixin(targets = "net.minecraft.server.world.ThreadedAnvilChunkStorage$EntityTracker")
    interface EntityTrackerAccessor {
        @Accessor
        EntityTrackerEntry getEntry();
    }
}
