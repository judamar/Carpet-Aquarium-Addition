//ported from: https://github.com/CommandLeo/scarpet/blob/main/programs/getfullbox.sc
package carpet.commands;

import carpet.utils.Messenger;
import net.minecraft.block.Block;
import net.minecraft.block.BlockShulkerBox;
import net.minecraft.command.*;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.item.*;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ResourceLocation;

public class CommandGetFullBox extends CommandCarpetBase {

    @Override
    public String getName() {
        return "getfullbox";
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "/getfullbox <item> [meta] [sb-color]";
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        if (command_enabled("commandGetFullBox", sender)) {
            if (!(sender instanceof EntityPlayerMP))
                throw new CommandException("Only players can use this command");

            if (args.length < 1 || args.length > 3)
                throw new WrongUsageException(getUsage(sender));

            EntityPlayerMP player = (EntityPlayerMP) sender;

            if (!player.interactionManager.getGameType().isCreative()) {
                throw new CommandException("You must be in Creative mode to use this command.");
            }

            String itemId = args[0];
            Item item = Item.REGISTRY.getObject(new ResourceLocation(itemId));
            if (item == null)
                throw new CommandException("Unknown item: " + itemId);

            int meta = 0;
            EnumDyeColor color = null;

            if (args.length >= 2) {
                try {
                    meta = Integer.parseInt(args[1]);
                } catch (NumberFormatException e) {
                    try {
                        color = EnumDyeColor.valueOf(args[1].toUpperCase());
                    } catch (IllegalArgumentException ex) {
                        throw new CommandException("Invalid metadata or color: " + args[1]);
                    }
                }
            }

            if (args.length == 3) {
                try {
                    color = EnumDyeColor.valueOf(args[2].toUpperCase());
                } catch (IllegalArgumentException e) {
                    throw new CommandException("Invalid color: " + args[2]);
                }
            }

            Block shulkerBlock = (color == null)
                    ? Blocks.PURPLE_SHULKER_BOX
                    : BlockShulkerBox.getBlockByColor(color);
            Item shulkerItem = Item.getItemFromBlock(shulkerBlock);

            ItemStack shulkerStack = new ItemStack(shulkerItem);
            NBTTagCompound shulkerTag = new NBTTagCompound();
            NBTTagCompound blockEntityTag = new NBTTagCompound();
            NBTTagList items = new NBTTagList();

            int maxStackSize = item.getItemStackLimit();
            for (int i = 0; i < 27; i++) {
                NBTTagCompound tag = new NBTTagCompound();
                tag.setByte("Slot", (byte) i);
                tag.setString("id", Item.REGISTRY.getNameForObject(item).toString());
                tag.setByte("Count", (byte) maxStackSize);
                tag.setShort("Damage", (short) meta);
                items.appendTag(tag);
            }

            blockEntityTag.setTag("Items", items);
            shulkerTag.setTag("BlockEntityTag", blockEntityTag);
            shulkerStack.setTagCompound(shulkerTag);

            boolean added = player.inventory.addItemStackToInventory(shulkerStack);
            if (!added)
                throw new CommandException("No space in inventory to add shulker box");

            Messenger.m(player, "g Gave a full shulker box of ", "w " + itemId, "g  with color ", "w " + (color == null ? "purple" : color.getName()));
        }
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 2;
    }
}
