package carpet.commands;

import carpet.CarpetSettings;
import carpet.utils.Messenger;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.List;

public class CommandDelimit extends CommandCarpetBase{
    @Override
    public String getName() {return "delimit";}

    @Override
    public String getUsage(ICommandSender sender) {return "/delimit <X> <Z> <radius>";}

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        if (command_enabled("commandDelimit", sender))
        {
            if (args.length != 3) throw new WrongUsageException("/delimit <X> <Z> <radius>");

            BlockPos ne, nw, se, sw = new BlockPos(0, 0, 0);
            int x = Integer.parseInt(args[0]);
            int z = Integer.parseInt(args[1]);
            int radius = Integer.parseInt(args[2]);
            boolean valid = false;

            //Noth = -Z
            //East = +X
            //South = +Z
            //West = -X

            try {
                ne = new BlockPos(x + radius, 0, z - radius);
                nw = new BlockPos(x - radius, 0, z - radius);
                se = new BlockPos(x + radius, 0, z + radius);
                sw = new BlockPos(x - radius, 0, z + radius);
                valid = true;
            }
            catch (NumberFormatException nfe)
            {
                throw new WrongUsageException("<X> <Z> <radius> must be a number");
            }

            if (valid)
            {
                msg(sender, Messenger.m(null, "g --------------------------------"));
                msg(sender, Messenger.m(null, "b PERIMETER LIMITS:"));
                msg(sender, Messenger.m(null, "w  ▸ NE: ", "y " + ne.getX() + " / " + ne.getZ(), "^g Summons a vertical particle beam", "!/particle endRod " + ne.getX() + " ~ " + ne.getZ() + " 0 15 0 0.01 1000 force"));
                msg(sender, Messenger.m(null, "w  ▸ NW: ", "y " + nw.getX() + " / " + nw.getZ(), "^g Summons a vertical particle beam", "!/particle endRod " + nw.getX() + " ~ " + nw.getZ() + " 0 15 0 0.01 1000 force"));
                msg(sender, Messenger.m(null, "w  ▸ SE: ", "y " + se.getX() + " / " + se.getZ(), "^g Summons a vertical particle beam", "!/particle endRod " + se.getX() + " ~ " + se.getZ() + " 0 15 0 0.01 1000 force"));
                msg(sender, Messenger.m(null, "w  ▸ SW: ", "y " + sw.getX() + " / " + sw.getZ(), "^g Summons a vertical particle beam", "!/particle endRod " + sw.getX() + " ~ " + sw.getZ() + " 0 15 0 0.01 1000 force"));
                msg(sender, Messenger.m(null, "g -------------------------------- "));
            }
        }
    }

    @Override
    public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args, @Nullable BlockPos pos)
    {
        if (!CarpetSettings.commandDelimit)
        {
            return Collections.<String>emptyList();
        }
        if (args.length > 0 && args.length <= 2)
        {
            return getTabCompletionCoordinateXZ(args, 0, pos);
        }
        return Collections.<String>emptyList();
    }
}
