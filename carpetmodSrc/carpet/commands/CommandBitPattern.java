//ported from: https://discord.com/channels/748542142347083868/756677711111389236/823683541048098818 dc: storage tech
package carpet.commands;

import carpet.utils.Messenger;
import net.minecraft.block.state.IBlockState;
import net.minecraft.command.*;
import net.minecraft.init.Blocks;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import javax.annotation.Nullable;
import java.util.*;

public class CommandBitPattern extends CommandCarpetBase {

    private static final Map<UUID, List<BlockPos>> lastPatterns = new HashMap<>();

    @Override
    public String getName() {
        return "bitpattern";
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "/bitpattern <up|down|undo> <bits> [size]";
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        if (command_enabled("commandBitPattern", sender))
        {
            if (!(sender instanceof EntityPlayerMP)) throw new CommandException("Only players can use this command");

            EntityPlayerMP player = (EntityPlayerMP) sender;
            World world = player.world;

            if (!player.interactionManager.getGameType().isCreative()) {
                throw new CommandException("You must be in Creative mode to use this command.");
            }

            if (args.length == 1 && args[0].equalsIgnoreCase("undo"))
            {
                UUID uuid = player.getUniqueID();
                List<BlockPos> last = lastPatterns.get(uuid);
                if (last == null || last.isEmpty())
                {
                    Messenger.m(sender, "r No previous pattern to undo.");
                    return;
                }
                for (BlockPos pos : last)
                {
                    player.world.setBlockState(pos, Blocks.AIR.getDefaultState(), 3);
                }
                lastPatterns.remove(uuid);
                Messenger.m(sender, "gi Previous pattern undone.");
                return;
            }

            if (args.length < 2 || args.length > 3) throw new WrongUsageException(getUsage(sender));

            if (!args[0].equalsIgnoreCase("up") && !args[0].equalsIgnoreCase("down")) {
                throw new CommandException("First argument must be 'up', 'down' or 'undo'");
            }
            boolean isUp = args[0].equalsIgnoreCase("up");

            int bits = parseInt(args[1]);
            if (bits < 1 || bits > 20)
                throw new CommandException("Bit count must be between 1 and 20");

            int size = args.length == 3 ? parseInt(args[2]) : 1;

            List<IBlockState> blocks = isUp
                    ? Arrays.asList(
                    Blocks.QUARTZ_BLOCK.getDefaultState(),
                    Blocks.OBSERVER.getDefaultState().withProperty(net.minecraft.block.BlockObserver.FACING, EnumFacing.UP))
                    : Arrays.asList(
                    Blocks.QUARTZ_BLOCK.getDefaultState(),
                    Blocks.OBSERVER.getDefaultState().withProperty(net.minecraft.block.BlockObserver.FACING, EnumFacing.DOWN)
            );

            BlockPos origin = getLookingPos(player);
            EnumFacing facing = player.getHorizontalFacing();
            EnumFacing offset = getOffsetFromFacing(facing);

            List<List<Boolean>> pattern = generatePattern(bits);
            List<BlockPos> placedBlocks = new ArrayList<>();
            boolean reverseBits = (facing == EnumFacing.SOUTH || facing == EnumFacing.WEST);

            for (int row = 0; row < pattern.size(); row++) {
                List<Boolean> line = pattern.get(row);
                BlockPos base = origin.offset(facing, (pattern.size() - 1 - row) * size);
                for (int col = 0; col < bits; col++) {
                    int bitIndex = reverseBits ? col : (bits - 1 - col);
                    BlockPos pos = base.offset(offset, col);
                    IBlockState state = blocks.get(line.get(bitIndex) ? 1 : 0);
                    world.setBlockState(pos, state, 3);
                    placedBlocks.add(pos);
                }
            }

            lastPatterns.put(player.getUniqueID(), placedBlocks);

            msg(sender, Messenger.m(null, "gi Generated ", "wi " + (1 << bits), "gi  combinations of ", "wi " + bits + "-bit ", "gi pattern."));

        }
    }

    private BlockPos getLookingPos(EntityPlayerMP player) {
        double reach = 5.0;
        Vec3d eye = player.getPositionEyes(1.0F);
        Vec3d look = player.getLook(1.0F);
        Vec3d reachVec = eye.add(look.x * reach, look.y * reach, look.z * reach);

        RayTraceResult result = player.world.rayTraceBlocks(eye, reachVec, false, false, true);

        if (result != null && result.typeOfHit == RayTraceResult.Type.BLOCK) {
        }

        return player.getPosition().down();
    }

    private EnumFacing getOffsetFromFacing(EnumFacing facing) {
        switch (facing) {
            case NORTH: return EnumFacing.EAST;
            case SOUTH: return EnumFacing.EAST;
            case EAST: return EnumFacing.SOUTH;
            case WEST: return EnumFacing.SOUTH;
            default: return EnumFacing.EAST;
        }
    }

    private List<List<Boolean>> generatePattern(int bits) {
        List<List<Boolean>> result = new ArrayList<>();
        int total = 1 << bits;
        for (int i = 0; i < total; i++) {
            List<Boolean> line = new ArrayList<>();
            for (int j = 0; j < bits; j++) {
                line.add((i & (1 << j)) != 0);
            }
            result.add(line);
        }
        return result;
    }



    public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args, @Nullable BlockPos targetPos)
    {
        if (args.length == 1){
            return getListOfStringsMatchingLastWord(args, "up", "down", "undo");
        } else {
            return Collections.emptyList();
        }
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 2;
    }
}
