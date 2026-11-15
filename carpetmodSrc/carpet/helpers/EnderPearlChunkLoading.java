package carpet.helpers;

import carpet.CarpetSettings;
import it.unimi.dsi.fastutil.longs.Long2LongMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import net.minecraft.entity.item.EntityEnderPearl;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.ChunkProviderServer;

// AqCM: enderPearlChunkLoading
public final class EnderPearlChunkLoading
{
    // World.updateEntityWithOptionalForce needs isAreaLoaded(pos +- 32 blocks), which is always exactly 2 chunks each way
    public static final int RADIUS = 2;
    // Same lifetime as the vanilla 1.14+ pearl ticket
    public static final int TICKET_TICKS = 2;

    private EnderPearlChunkLoading() {}

    public static void tick(EntityEnderPearl pearl)
    {
        if (!CarpetSettings.enderPearlChunkLoading || !(pearl.world instanceof WorldServer) || pearl.isDead
                || (pearl.motionX == 0.0D && pearl.motionZ == 0.0D))
        {
            return;
        }

        WorldServer world = (WorldServer) pearl.world;
        ChunkProviderServer provider = world.getChunkProvider();
        long validUntil = world.getTotalWorldTime() + TICKET_TICKS;
        int cx = MathHelper.floor(pearl.posX) >> 4;
        int cz = MathHelper.floor(pearl.posZ) >> 4;

        for (int dx = -RADIUS; dx <= RADIUS; ++dx)
        {
            for (int dz = -RADIUS; dz <= RADIUS; ++dz)
            {
                provider.provideChunk(cx + dx, cz + dz);
                provider.pearlTickets.put(ChunkPos.asLong(cx + dx, cz + dz), validUntil);
            }
        }
    }

    /** True while a flying pearl holds this chunk, so ChunkProviderServer.queueUnload must refuse to drop it. */
    public static boolean isHeld(WorldServer world, ChunkProviderServer provider, int x, int z)
    {
        return provider.pearlTickets.get(ChunkPos.asLong(x, z)) >= world.getTotalWorldTime();
    }

    /** Forgets expired tickets and queues their chunks for unload unless a player watches them. Runs even with the rule off. */
    public static void expire(WorldServer world, ChunkProviderServer provider)
    {
        long now = world.getTotalWorldTime();
        LongArrayList expired = new LongArrayList();

        for (Long2LongMap.Entry entry : provider.pearlTickets.long2LongEntrySet())
        {
            if (entry.getLongValue() < now)
            {
                expired.add(entry.getLongKey());
            }
        }

        // Remove first: queueUnload asks isHeld, and an expired ticket must not hold the chunk
        for (int i = 0; i < expired.size(); ++i)
        {
            provider.pearlTickets.remove(expired.getLong(i));
        }

        for (int i = 0; i < expired.size(); ++i)
        {
            long key = expired.getLong(i);
            int x = (int) key;
            int z = (int) (key >>> 32);
            Chunk chunk = provider.loadedChunks.get(key);

            if (chunk != null && !world.playerChunkMap.contains(x, z))
            {
                provider.queueUnload(chunk);
            }
        }
    }
}
