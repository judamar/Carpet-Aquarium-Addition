//ported from: https://github.com/CommandLeo/scarpet/blob/main/programs/getallitems.sc
package carpet.commands;

import carpet.utils.Messenger;
import carpet.helpers.GetAllItemsHelper;
import net.minecraft.command.*;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class CommandGetAllItems extends CommandCarpetBase {

    @Override
    public String getName() {
        return "getallitems";
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "/getallitems use\n" +
                "/getallitems use <obtainability>\n" +
                "/getallitems <obtainability> <stackability>";
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        if (command_enabled("commandGetAllItems", sender)) {
            if (!(sender instanceof EntityPlayerMP))
                throw new CommandException("Only players can use this command");

            EntityPlayerMP player = (EntityPlayerMP) sender;

            if (!player.interactionManager.getGameType().isCreative()) {
                throw new CommandException("You must be in Creative mode to use this command.");
            }

            if (args.length == 1 && args[0].equalsIgnoreCase("use")) {
                GetAllItemsHelper.selectObtainability(player);
                return;
            }

            if (args.length == 2 && args[0].equalsIgnoreCase("use")) {
                String ob = args[1].toLowerCase();
                if (!GetAllItemsHelper.getObtainabilityOptions().contains(ob)) {
                    Messenger.m(sender, "r Invalid obtainability mode: ", "w " + ob);
                    return;
                }
                GetAllItemsHelper.selectStackability(sender, ob);
                return;
            }

            if (args.length >= 1 && args.length <= 2) {
                String obtainability = args[0].toLowerCase();
                String stackability = args.length == 2 ? args[1].toLowerCase() : "stackables";

                if (!GetAllItemsHelper.getObtainabilityOptions().contains(obtainability))
                    throw new CommandException("Invalid obtainability: " + obtainability);
                if (!GetAllItemsHelper.getStackabilityOptions().contains(stackability))
                    throw new CommandException("Invalid stackability: " + stackability);

                int count = GetAllItemsHelper.generateItemDump(player.getEntityWorld(), player, obtainability, stackability);
                Messenger.m(player, "g Spawned " + count + " item variants.");
                return;
            }

            throw new WrongUsageException(getUsage(sender));
        }
    }

    @Override
    public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args, @Nullable BlockPos targetPos) {
        if (args.length == 1) {
            List<String> options = new ArrayList<>(GetAllItemsHelper.getObtainabilityOptions());
            return getListOfStringsMatchingLastWord(args, options);
        }

        if (args.length == 2) {
            if (args[0].equalsIgnoreCase("use")) {
                return getListOfStringsMatchingLastWord(args, GetAllItemsHelper.getObtainabilityOptions());
            } else {
                return getListOfStringsMatchingLastWord(args, GetAllItemsHelper.getStackabilityOptions());
            }
        }

        return Collections.emptyList();
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 2;
    }
}
