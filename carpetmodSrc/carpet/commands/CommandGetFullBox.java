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
        return "/getfullbox <item> [sb color]";
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        if (!(sender instanceof EntityPlayerMP))
            throw new CommandException("Only players can use this command");

        if (args.length < 1 || args.length > 2)
            throw new WrongUsageException(getUsage(sender));

        EntityPlayerMP player = (EntityPlayerMP) sender;

        // 1. Obtener ítem
        String itemId = args[0];
        Item item = Item.REGISTRY.getObject(new ResourceLocation(itemId));
        if (item == null)
            throw new CommandException("Unknown item: " + itemId);

        // 2. Obtener color (si aplica)
        EnumDyeColor color = null;
        if (args.length == 2) {
            try {
                color = EnumDyeColor.valueOf(args[1].toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new CommandException("Invalid color: " + args[1]);
            }
        }

        // 3. Obtener ítem de shulker box
        Block shulkerBlock = (color == null)
                ? Blocks.PURPLE_SHULKER_BOX
                : BlockShulkerBox.getBlockByColor(color);
        Item shulkerItem = Item.getItemFromBlock(shulkerBlock);

        // 4. Crear shulker llena con el ítem
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
            items.appendTag(tag);
        }

        blockEntityTag.setTag("Items", items);
        shulkerTag.setTag("BlockEntityTag", blockEntityTag);
        shulkerStack.setTagCompound(shulkerTag);

        // 5. Entregar al jugador
        boolean added = player.inventory.addItemStackToInventory(shulkerStack);
        if (!added)
            throw new CommandException("No space in inventory to add shulker box");

        Messenger.m(player, "g Gave a full shulker box of ", "w " + itemId, "g  with color ", "w " + (color == null ? "purple" : color.getName()));
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 2;
    }
}
