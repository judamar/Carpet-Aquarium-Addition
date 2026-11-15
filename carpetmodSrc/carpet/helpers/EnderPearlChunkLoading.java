package carpet.helpers;

import carpet.CarpetSettings;
import net.minecraft.entity.item.EntityEnderPearl;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.WorldServer;
import net.minecraft.world.gen.ChunkProviderServer;

// AqCM: enderPearlChunkLoading
public final class EnderPearlChunkLoading
{
    // World.updateEntityWithOptionalForce needs isAreaLoaded(pos +- 32 blocks), which is always exactly 2 chunks each way
    public static final int RADIUS = 2;

    private EnderPearlChunkLoading() {}

    public static void tick(EntityEnderPearl pearl)
    {
        if (!CarpetSettings.enderPearlChunkLoading || !(pearl.world instanceof WorldServer) || pearl.isDead
                || (pearl.motionX == 0.0D && pearl.motionZ == 0.0D))
        {
            return;
        }

        ChunkProviderServer provider = ((WorldServer) pearl.world).getChunkProvider();
        int cx = MathHelper.floor(pearl.posX) >> 4;
        int cz = MathHelper.floor(pearl.posZ) >> 4;

        for (int dx = -RADIUS; dx <= RADIUS; ++dx)
        {
            for (int dz = -RADIUS; dz <= RADIUS; ++dz)
            {
                provider.provideChunk(cx + dx, cz + dz);
            }
        }
    }
}
