package carpet.network;

import carpet.CarpetSettings;
import io.netty.buffer.Unpooled;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagDouble;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagLongArray;
import net.minecraft.nbt.NBTUtil;
import net.minecraft.network.PacketBuffer;
import net.minecraft.network.play.client.CPacketCustomPayload;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.WorldServer;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Server side of Litematica's "save schematic on server" protocol (litematica:save, AllAtOnce only),
 * so block entity NBT (chest contents etc.) can be saved from a dedicated server. //AqCM
 *
 * Request and response formats mirror litematica.network.SchematicSavePacketHandler and
 * litematica.task.MultiplayerCreateSchematicTask (liteloader_1.12.2 branch).
 * Entities (mobs, minecarts with chests, item frames...) are sent with their full NBT.
 * Not supported: scheduled ticks, ExposedBlocksOnly.
 */
public class LitematicaSaveHandler implements PluginChannelHandler
{
    public static final String CHANNEL = "litematica:save";
    // ponytail: the whole request is handled in one tick; spread it over ticks if bigger areas are needed
    private static final long MAX_VOLUME = 4_000_000L;

    @Override
    public String[] getChannels()
    {
        return new String[] { CHANNEL };
    }

    @Override
    public void onCustomPayload(CPacketCustomPayload packet, EntityPlayerMP player)
    {
        if (!CarpetSettings.modLitematicaServerSave) return;

        try
        {
            PacketBuffer buf = PacketSplitter.receive(player, packet);
            if (buf != null) handle(player, buf.readCompoundTag());
        }
        catch (IOException | RuntimeException e)
        {
            tell(player, "request failed: " + e);
        }
    }

    private static void handle(EntityPlayerMP player, NBTTagCompound req)
    {
        if (req == null || !req.hasKey("Regions", 10)) return;
        if (!"AllAtOnce".equals(req.getString("SaveMethod")))
        {
            tell(player, "only AllAtOnce saving is supported");
            return;
        }

        NBTTagCompound want = req.getCompoundTag("RequestedData");
        NBTTagCompound regionsIn = req.getCompoundTag("Regions");
        long total = 0;

        for (String name : regionsIn.getKeySet())
        {
            long volume = volume(regionsIn.getIntArray(name));
            total += volume;
            if (volume <= 0 || total > MAX_VOLUME)
            {
                tell(player, "invalid selection or more than " + MAX_VOLUME + " blocks");
                return;
            }
        }

        WorldServer world = player.getServerWorld();
        NBTTagCompound regionsOut = new NBTTagCompound();

        for (String name : regionsIn.getKeySet())
        {
            regionsOut.setTag(name, readRegion(world, regionsIn.getIntArray(name),
                    want.getBoolean("Blocks"), want.getBoolean("BlockEntities"), want.getBoolean("Entities")));
        }

        NBTTagCompound out = new NBTTagCompound();
        out.setLong("UUIDM", req.getLong("UUIDM"));
        out.setLong("UUIDL", req.getLong("UUIDL"));
        out.setString("SaveMethod", "AllAtOnce");
        out.setTag("Regions", regionsOut);

        PacketBuffer buf = new PacketBuffer(Unpooled.buffer());
        buf.writeCompoundTag(out);
        PacketSplitter.send(player, CHANNEL, buf);
    }

    /** b = minX, minY, minZ, maxX, maxY, maxZ (inclusive). Returns 0 if invalid or too large. */
    private static long volume(int[] b)
    {
        if (b.length != 6) return 0;
        long dx = (long) b[3] - b[0] + 1, dy = (long) b[4] - b[1] + 1, dz = (long) b[5] - b[2] + 1;
        if (dx <= 0 || dy <= 0 || dz <= 0 || dx * dy > MAX_VOLUME) return 0;
        return dx * dy * dz;
    }

    private static NBTTagCompound readRegion(WorldServer world, int[] b, boolean blocks, boolean tileEntities,
                                           boolean entities)
    {
        int sx = b[3] - b[0] + 1, sy = b[4] - b[1] + 1, sz = b[5] - b[2] + 1;
        int[] ids = new int[sx * sy * sz];
        Map<IBlockState, Integer> palette = new LinkedHashMap<>();
        palette.put(Blocks.AIR.getDefaultState(), 0); // id 0 is air, like Litematica's container
        NBTTagList tiles = new NBTTagList();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

        for (int z = 0; z < sz; z++)
        {
            for (int x = 0; x < sx; x++)
            {
                // unloaded chunks are left as air; never generate or load them for a read
                if (world.getChunkProvider().getLoadedChunk((b[0] + x) >> 4, (b[2] + z) >> 4) == null) continue;

                for (int y = 0; y < sy; y++)
                {
                    pos.setPos(b[0] + x, b[1] + y, b[2] + z);
                    IBlockState state = world.getBlockState(pos).getActualState(world, pos);

                    if (blocks)
                    {
                        Integer id = palette.get(state);
                        if (id == null) palette.put(state, id = palette.size());
                        ids[(y * sz + z) * sx + x] = id;
                    }

                    if (tileEntities && state.getBlock().hasTileEntity())
                    {
                        TileEntity te = world.getTileEntity(pos.toImmutable());
                        if (te != null)
                        {
                            NBTTagCompound tag = te.writeToNBT(new NBTTagCompound());
                            // positions are relative to the region's minimum corner
                            tag.setInteger("x", x);
                            tag.setInteger("y", y);
                            tag.setInteger("z", z);
                            tiles.appendTag(tag);
                        }
                    }
                }
            }
        }

        NBTTagList entityList = new NBTTagList();
        if (entities)
        {
            AxisAlignedBB box = new AxisAlignedBB(b[0], b[1], b[2], b[3] + 1, b[4] + 1, b[5] + 1);

            // only looks at loaded chunks; players and dead entities are skipped by writeToNBTOptional,
            // riders are saved inside their vehicle
            for (Entity entity : world.getEntitiesWithinAABB(Entity.class, box))
            {
                NBTTagCompound tag = new NBTTagCompound();
                if (!entity.writeToNBTOptional(tag)) continue;

                // positions are relative to the region's minimum corner
                NBTTagList posTag = new NBTTagList();
                posTag.appendTag(new NBTTagDouble(entity.posX - b[0]));
                posTag.appendTag(new NBTTagDouble(entity.posY - b[1]));
                posTag.appendTag(new NBTTagDouble(entity.posZ - b[2]));
                tag.setTag("Pos", posTag);
                entityList.appendTag(tag);
            }
        }

        NBTTagList paletteTag = new NBTTagList();
        for (IBlockState state : palette.keySet())
        {
            paletteTag.appendTag(NBTUtil.writeBlockState(new NBTTagCompound(), state));
        }

        NBTTagCompound blocksTag = new NBTTagCompound();
        blocksTag.setInteger("sizeX", sx);
        blocksTag.setInteger("sizeY", sy);
        blocksTag.setInteger("sizeZ", sz);
        blocksTag.setTag("BlockStatePalette", paletteTag);
        blocksTag.setTag("BlockStates", new NBTTagLongArray(pack(ids, bitsFor(palette.size()))));

        NBTTagCompound region = new NBTTagCompound();
        region.setTag("Blocks", blocksTag);
        region.setTag("BlockEntities", tiles);
        if (entities) region.setTag("Entities", entityList);
        return region;
    }

    static int bitsFor(int paletteSize)
    {
        return Math.max(2, 32 - Integer.numberOfLeadingZeros(paletteSize - 1));
    }

    /** Same layout as Litematica's LitematicaBitArray: entries span long boundaries, lowest bits first. */
    static long[] pack(int[] ids, int bits)
    {
        long[] arr = new long[(int) (((long) ids.length * bits + 63) / 64)];

        for (int i = 0; i < ids.length; i++)
        {
            long start = (long) i * bits;
            int idx = (int) (start >> 6), off = (int) (start & 63);
            arr[idx] |= (long) ids[i] << off;
            if (off + bits > 64) arr[idx + 1] |= (long) ids[i] >>> (64 - off);
        }

        return arr;
    }

    private static void tell(EntityPlayerMP player, String msg)
    {
        player.sendMessage(new TextComponentString("[" + CHANNEL + "] " + msg));
    }
}
