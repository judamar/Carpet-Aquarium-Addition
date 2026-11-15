package carpet.commands;

import carpet.CarpetSettings;
import carpet.utils.Messenger;
import net.minecraft.block.Block;
import net.minecraft.block.BlockRedstoneOre;
import net.minecraft.block.BlockRedstoneTorch;
import net.minecraft.block.state.IBlockState;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import javax.annotation.Nullable;
import java.util.*;

/**
 * Port of the Scarpet "Period" app by CommandLeo & Firigion to Carpet 1.12.2.
 *
 * Monitors redstone components and reports their period (on/off cycle length in gt),
 * on-time, and off-time whenever a change is detected.
 *
 * Usage:
 *   /period monitor [x y z]     - Start monitoring target block or specified pos
 *   /period unmonitor [x y z]   - Stop monitoring target block or specified pos
 *   /period clear                - Stop monitoring all blocks
 *   /period list                 - List all monitored blocks
 *   /period help                 - Show usage
 */
public class CommandPeriod extends CommandCarpetBase
{
    // -------------------------------------------------------------------------
    // Per-player monitoring state
    // -------------------------------------------------------------------------

    /** Monitored positions per player UUID */
    private static final Map<UUID, Set<BlockPos>> monitoredByPlayer = new HashMap<>();

    /**
     * Per-block tracking data per player UUID.
     * Key: playerUUID -> blockPos -> BlockData
     */
    private static final Map<UUID, Map<BlockPos, BlockData>> dataByPlayer = new HashMap<>();

    private static class BlockData
    {
        boolean wasActive   = false;
        boolean wasInactive = false;
        long    onTick      = 0;
        long    offTick     = 0;

        // Last reported [period, onTime, offTime] — avoids re-printing same value
        long[] lastPeriod = null;
    }

    // -------------------------------------------------------------------------
    // Valid block detection (mirrors isValid / isActive from the Scarpet app)
    // -------------------------------------------------------------------------

    private static boolean isValidBlock(IBlockState state)
    {
        Block block = state.getBlock();
        return isRedstoneComponent(block) || isLittable(block) || isCommandBlock(block);
    }

    private static boolean isCommandBlock(Block block)
    {
        return block == Blocks.COMMAND_BLOCK
                || block == Blocks.CHAIN_COMMAND_BLOCK
                || block == Blocks.REPEATING_COMMAND_BLOCK;
    }

    private static boolean isLittable(Block block)
    {
        return block == Blocks.REDSTONE_TORCH
                || block == Blocks.UNLIT_REDSTONE_TORCH
                || block == Blocks.REDSTONE_LAMP
                || block == Blocks.LIT_REDSTONE_LAMP
                || block == Blocks.REDSTONE_ORE
                || block == Blocks.LIT_REDSTONE_ORE;
    }

    private static boolean isRedstoneComponent(Block block)
    {
        return block == Blocks.POWERED_REPEATER
                || block == Blocks.UNPOWERED_REPEATER
                || block == Blocks.POWERED_COMPARATOR
                || block == Blocks.UNPOWERED_COMPARATOR
                || block == Blocks.REDSTONE_WIRE
                || block == Blocks.LEVER
                || block == Blocks.STONE_BUTTON
                || block == Blocks.WOODEN_BUTTON
                || block == Blocks.STONE_PRESSURE_PLATE
                || block == Blocks.WOODEN_PRESSURE_PLATE
                || block == Blocks.LIGHT_WEIGHTED_PRESSURE_PLATE
                || block == Blocks.HEAVY_WEIGHTED_PRESSURE_PLATE
                || block == Blocks.PISTON
                || block == Blocks.STICKY_PISTON
                || block == Blocks.DROPPER
                || block == Blocks.DISPENSER
                || block == Blocks.HOPPER
                || block == Blocks.TNT
                || block == Blocks.DAYLIGHT_DETECTOR
                || block == Blocks.DAYLIGHT_DETECTOR_INVERTED
                || block == Blocks.TRAPPED_CHEST
                || block == Blocks.TRIPWIRE_HOOK
                || block == Blocks.TRIPWIRE
                || block == Blocks.OBSERVER
                || block == Blocks.GOLDEN_RAIL
                || block == Blocks.DETECTOR_RAIL
                || block == Blocks.ACTIVATOR_RAIL
                || block == Blocks.OAK_DOOR
                || block == Blocks.SPRUCE_DOOR
                || block == Blocks.BIRCH_DOOR
                || block == Blocks.JUNGLE_DOOR
                || block == Blocks.ACACIA_DOOR
                || block == Blocks.DARK_OAK_DOOR
                || block == Blocks.IRON_DOOR
                || block == Blocks.IRON_TRAPDOOR
                || block == Blocks.TRAPDOOR
                || block == Blocks.OAK_FENCE_GATE
                || block == Blocks.SPRUCE_FENCE_GATE
                || block == Blocks.BIRCH_FENCE_GATE
                || block == Blocks.JUNGLE_FENCE_GATE
                || block == Blocks.DARK_OAK_FENCE_GATE
                || block == Blocks.ACACIA_FENCE_GATE
                || block == Blocks.NOTEBLOCK
                || block == Blocks.STRUCTURE_BLOCK;
    }

    /**
     * Returns true if the block is currently "active" (powered, extended, lit, etc.)
     * Mirrors isActive() from the Scarpet app.
     */
    private static boolean isActive(IBlockState state)
    {
        Block block = state.getBlock();

        // Lit redstone ore / lamp / torch
        if (block == Blocks.LIT_REDSTONE_ORE
                || block == Blocks.LIT_REDSTONE_LAMP
                || block == Blocks.REDSTONE_TORCH)
        {
            return true;
        }

        // Unlit variants are inactive
        if (block == Blocks.REDSTONE_ORE
                || block == Blocks.UNLIT_REDSTONE_TORCH
                || block == Blocks.REDSTONE_LAMP)
        {
            return false;
        }

        // Extended piston
        if (block == Blocks.PISTON || block == Blocks.STICKY_PISTON)
        {
            try
            {
                return (boolean) state.getValue(
                        net.minecraft.block.BlockPistonBase.EXTENDED);
            }
            catch (Exception ignored) {}
        }

        // Powered repeater / comparator
        if (block == Blocks.POWERED_REPEATER || block == Blocks.POWERED_COMPARATOR)
        {
            return true;
        }

        // Redstone wire power > 0
        if (block == Blocks.REDSTONE_WIRE)
        {
            try
            {
                return (int) state.getValue(
                        net.minecraft.block.BlockRedstoneWire.POWER) > 0;
            }
            catch (Exception ignored) {}
        }

        // Powered rail / activator rail — POWERED property
        if (block == Blocks.GOLDEN_RAIL || block == Blocks.ACTIVATOR_RAIL)
        {
            try { return (boolean) state.getValue(net.minecraft.block.BlockRailPowered.POWERED); }
            catch (Exception ignored) {}
        }

        // Detector rail — POWERED when a minecart is on top
        if (block == Blocks.DETECTOR_RAIL)
        {
            try { return (boolean) state.getValue(net.minecraft.block.BlockRailDetector.POWERED); }
            catch (Exception ignored) {}
        }

        // Doors and trapdoors — OPEN property
        if (block == Blocks.OAK_DOOR
                || block == Blocks.SPRUCE_DOOR
                || block == Blocks.BIRCH_DOOR
                || block == Blocks.JUNGLE_DOOR
                || block == Blocks.ACACIA_DOOR
                || block == Blocks.DARK_OAK_DOOR
                || block == Blocks.IRON_DOOR
                || block == Blocks.IRON_TRAPDOOR
                || block == Blocks.TRAPDOOR)
        {
            try { return (boolean) state.getValue(net.minecraft.block.BlockDoor.OPEN); }
            catch (Exception ignored) {}
        }

        // Fence gates — OPEN property
        if (block == Blocks.OAK_FENCE_GATE || block == Blocks.SPRUCE_FENCE_GATE
                || block == Blocks.BIRCH_FENCE_GATE || block == Blocks.JUNGLE_FENCE_GATE
                || block == Blocks.DARK_OAK_FENCE_GATE || block == Blocks.ACACIA_FENCE_GATE)
        {
            try { return (boolean) state.getValue(net.minecraft.block.BlockFenceGate.OPEN); }
            catch (Exception ignored) {}
        }

        // Lever, buttons, pressure plates, tripwire, observer — check POWERED property
        try
        {
            return (boolean) state.getValue(
                    net.minecraft.block.BlockLever.POWERED);
        }
        catch (Exception ignored) {}

        return false;
    }

    // -------------------------------------------------------------------------
    // Tick hook — called every tick from CarpetServer or WorldServer
    // -------------------------------------------------------------------------

    /**
     * Call this every server tick, e.g. from WorldServer.tick() or CarpetServer.
     *
     *   // In WorldServer.tick(), after super.tick():
     *   if (CarpetSettings.commandPeriod)
     *       CommandPeriod.tick(this);
     */
    public static void tick(World world)
    {
        if (world.isRemote) return;

        long currentTick = world.getWorldInfo().getWorldTotalTime();

        for (Map.Entry<UUID, Set<BlockPos>> playerEntry : monitoredByPlayer.entrySet())
        {
            UUID playerUUID = playerEntry.getKey();
            Set<BlockPos> monitored = playerEntry.getValue();

            // Find the online player
            EntityPlayer player = world.getPlayerEntityByUUID(playerUUID);

            Map<BlockPos, BlockData> dataMap = dataByPlayer
                    .computeIfAbsent(playerUUID, k -> new HashMap<>());

            Iterator<BlockPos> it = monitored.iterator();
            while (it.hasNext())
            {
                BlockPos pos = it.next();

                if (!world.isBlockLoaded(pos)) continue;

                IBlockState state = world.getBlockState(pos);
                Block block = state.getBlock();

                // Remove if block is no longer a valid redstone component
                // (mirrors the moving_piston exception from the Scarpet app)
                if (!isValidBlock(state) && block != Blocks.PISTON_EXTENSION)
                {
                    it.remove();
                    dataMap.remove(pos);
                    if (player != null)
                    {
                        Messenger.m((EntityPlayerMP) player,
                                "r [Period] ",
                                "w Block at " + pos.getX() + " " + pos.getY() + " " + pos.getZ()
                                        + " is no longer a valid redstone component. Unmonitored.");
                    }
                    continue;
                }

                BlockData data = dataMap.computeIfAbsent(pos, k -> new BlockData());
                boolean active = isActive(state);

                if (active)
                {
                    if (!data.wasActive)
                    {
                        // Rising edge — block just turned on
                        data.wasActive   = true;
                        data.wasInactive = false;
                        data.onTick      = currentTick - 1;
                    }
                    else if (data.wasInactive)
                    {
                        // Full cycle complete: on -> off -> on again
                        long period  = currentTick - data.onTick;
                        long onTime  = data.offTick - data.onTick;
                        long offTime = currentTick - data.offTick;

                        long[] periodData = {period, onTime, offTime};

                        if (!Arrays.equals(data.lastPeriod, periodData))
                        {
                            data.lastPeriod = periodData;

                            if (player != null)
                            {
                                double freq = 20.0 / period;
                                Messenger.m((EntityPlayerMP) player,
                                        "c [Period] ",
                                        "gi " + block.getLocalizedName(),
                                        "w  at ",
                                        "c " + pos.getX() + " " + pos.getY() + " " + pos.getZ(),
                                        "w  » ",
                                        "c " + period + "gt",
                                        "w  | ",
                                        "g Freq: " + String.format("%.2f", freq) + "Hz",
                                        "w  | On: ",
                                        "g " + onTime + "gt",
                                        "w  | Off: ",
                                        "g " + offTime + "gt"
                                );
                            }
                        }

                        data.wasActive   = false;
                        data.wasInactive = false;
                    }
                }
                else
                {
                    // Falling edge — block just turned off
                    if (data.wasActive && !data.wasInactive)
                    {
                        data.offTick     = currentTick;
                        data.wasInactive = true;
                    }
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // Command plumbing
    // -------------------------------------------------------------------------

    @Override
    public String getName()
    {
        return "period";
    }

    @Override
    public String getUsage(ICommandSender sender)
    {
        return "/period <monitor|unmonitor|clear|list|help> [x y z]";
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args)
            throws CommandException
    {
        if (!command_enabled("commandPeriod", sender)) return;

        EntityPlayerMP player = getCommandSenderAsPlayer(sender);

        if (args.length == 0 || args[0].equalsIgnoreCase("help"))
        {
            sendHelp(player);
            return;
        }

        switch (args[0].toLowerCase())
        {
            case "monitor":
                cmdMonitor(server, player, args);
                break;
            case "unmonitor":
                cmdUnmonitor(server, player, args);
                break;
            case "clear":
                cmdClear(player);
                break;
            case "list":
                cmdList(player);
                break;
            default:
                Messenger.m(player, "r Unknown subcommand. Use /period help");
        }
    }

    // -------------------------------------------------------------------------
    // Subcommands
    // -------------------------------------------------------------------------

    private void cmdMonitor(MinecraftServer server, EntityPlayerMP player, String[] args)
            throws CommandException
    {
        BlockPos pos = resolvePos(player, args, 1);
        if (pos == null) return;

        IBlockState state = player.world.getBlockState(pos);
        if (!isValidBlock(state))
        {
            Messenger.m(player, "r [Period] That block is not a valid redstone component.");
            return;
        }

        Set<BlockPos> monitored = monitoredByPlayer
                .computeIfAbsent(player.getUniqueID(), k -> new LinkedHashSet<>());

        if (monitored.contains(pos))
        {
            Messenger.m(player, "r [Period] That block is already being monitored.");
            return;
        }

        monitored.add(pos);
        Messenger.m(player,
                "c [Period] ",
                "w Started monitoring ",
                "gi " + state.getBlock().getLocalizedName(),
                "w  at ",
                "c " + pos.getX() + " " + pos.getY() + " " + pos.getZ()
        );
    }

    private void cmdUnmonitor(MinecraftServer server, EntityPlayerMP player, String[] args)
            throws CommandException
    {
        BlockPos pos = resolvePos(player, args, 1);
        if (pos == null) return;

        Set<BlockPos> monitored = monitoredByPlayer.get(player.getUniqueID());
        if (monitored == null || !monitored.contains(pos))
        {
            Messenger.m(player, "r [Period] That block is not being monitored.");
            return;
        }

        monitored.remove(pos);
        Map<BlockPos, BlockData> dataMap = dataByPlayer.get(player.getUniqueID());
        if (dataMap != null) dataMap.remove(pos);

        Messenger.m(player,
                "c [Period] ",
                "w Stopped monitoring ",
                "c " + pos.getX() + " " + pos.getY() + " " + pos.getZ()
        );
    }

    private void cmdClear(EntityPlayerMP player)
    {
        Set<BlockPos> monitored = monitoredByPlayer.get(player.getUniqueID());
        int count = monitored == null ? 0 : monitored.size();

        if (count == 0)
        {
            Messenger.m(player, "r [Period] No blocks are being monitored.");
            return;
        }

        monitoredByPlayer.remove(player.getUniqueID());
        dataByPlayer.remove(player.getUniqueID());

        Messenger.m(player,
                "c [Period] ",
                "w Unmonitored ",
                "c " + count,
                "w " + (count == 1 ? " block." : " blocks.")
        );
    }

    private void cmdList(EntityPlayerMP player)
    {
        Set<BlockPos> monitored = monitoredByPlayer.get(player.getUniqueID());
        if (monitored == null || monitored.isEmpty())
        {
            Messenger.m(player, "r [Period] No blocks are being monitored.");
            return;
        }

        Messenger.m(player,
                "c [Period] ",
                "w Monitoring ",
                "c " + monitored.size(),
                "w " + (monitored.size() == 1 ? "block:" : "blocks:")
        );

        for (BlockPos pos : monitored)
        {
            IBlockState state = player.world.getBlockState(pos);
            String blockName = state.getBlock().getLocalizedName();
            Messenger.m(player,
                    "w  - ",
                    "gi " + blockName,
                    "w  at ",
                    "c " + pos.getX() + " " + pos.getY() + " " + pos.getZ()
            );
        }
    }

    private void sendHelp(EntityPlayerMP player)
    {
        Messenger.m(player, "c [Period] Commands:");
        Messenger.m(player, "w  /period monitor [x y z]   ", "g - Monitor a block");
        Messenger.m(player, "w  /period unmonitor [x y z] ", "g - Unmonitor a block");
        Messenger.m(player, "w  /period clear             ", "g - Unmonitor all blocks");
        Messenger.m(player, "w  /period list              ", "g - List monitored blocks");
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Resolves a BlockPos from:
     *   - args[startIdx], args[startIdx+1], args[startIdx+2]  if coordinates given
     *   - player's line-of-sight (ray trace) otherwise
     *
     * Returns null and sends an error message if no target could be found.
     */
    @Nullable
    private BlockPos resolvePos(EntityPlayerMP player, String[] args, int startIdx)
    {
        if (args.length >= startIdx + 3)
        {
            try
            {
                int x = Integer.parseInt(args[startIdx]);
                int y = Integer.parseInt(args[startIdx + 1]);
                int z = Integer.parseInt(args[startIdx + 2]);
                return new BlockPos(x, y, z);
            }
            catch (NumberFormatException e)
            {
                Messenger.m(player, "r [Period] Invalid coordinates.");
                return null;
            }
        }

        // Ray trace usando actionPack — igual que CommandEntityInfo
        RayTraceResult result = player.actionPack.mouseOver();
        if (result == null || result.typeOfHit != RayTraceResult.Type.BLOCK)
        {
            Messenger.m(player, "r [Period] You must be looking at a block.");
            return null;
        }

        return result.getBlockPos();
    }

    @Override
    public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender,
                                          String[] args, @Nullable BlockPos targetPos)
    {
        if (!CarpetSettings.commandPeriod)
        {
            return Collections.<String>emptyList();
        }
        if (args.length == 1)
        {
            return getListOfStringsMatchingLastWord(args,
                    "monitor", "unmonitor", "clear", "list", "help");
        }
        if (args.length >= 2 && args.length <= 4)
        {
            String sub = args[0].toLowerCase();
            if (sub.equals("monitor") || sub.equals("unmonitor"))
            {
                return getTabCompletionCoordinate(args, 1, targetPos);
            }
        }
        return Collections.<String>emptyList();
    }
}