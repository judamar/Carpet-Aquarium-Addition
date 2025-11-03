//ported from: https://github.com/CommandLeo/scarpet/blob/main/programs/randomizer.sc
package carpet.commands;

import carpet.helpers.ItemRandomizerHelper;
import carpet.utils.Messenger;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.ILockableContainer;
import net.minecraft.world.WorldServer;

import java.io.File;
import java.util.*;

public class CommandItemRandomizer extends CommandCarpetBase {
    @Override
    public String getName() {
        return "itemrandomizer";
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "/itemrandomizer <create|insert|give|shulkerchest|list|use|info|delete|help>";
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        if (command_enabled("commandItemRandomizer", sender)) {
            if (!(sender instanceof EntityPlayerMP)) {
                throw new CommandException("Only players can use this command.");
            }

            EntityPlayerMP player = (EntityPlayerMP) sender;
            WorldServer world = player.getServerWorld();

            if (!player.interactionManager.getGameType().isCreative()) {
                throw new CommandException("You must be in Creative mode to use this command.");
            }


            if (!ItemRandomizerHelper.TABLE_DIR.exists()) {
                ItemRandomizerHelper.TABLE_DIR.mkdirs();
            }

            if (args.length == 0) {
                ItemRandomizerHelper.printHelpMessage(sender);
                return;
            }

            String sub = args[0];
            if (sub.equalsIgnoreCase("create")) {
                if (args.length == 3 && args[1].equalsIgnoreCase("looking")) {
                    TileEntity tile = ItemRandomizerHelper.getTargetedTile(player, 5.0);
                    if (tile instanceof IInventory) {
                        boolean success = ItemRandomizerHelper.saveInventoryToTable((IInventory) tile, args[2], sender);
                        if (success) Messenger.m(sender, "g Table created from looking container: ", "w " + args[2]);
                    } else {
                        Messenger.m(sender, "r No container found.");
                    }

                } else if (args.length == 9 && args[1].equalsIgnoreCase("containers")) {
                    int x1 = Integer.parseInt(args[2]);
                    int y1 = Integer.parseInt(args[3]);
                    int z1 = Integer.parseInt(args[4]);
                    int x2 = Integer.parseInt(args[5]);
                    int y2 = Integer.parseInt(args[6]);
                    int z2 = Integer.parseInt(args[7]);
                    String tableName = args[8];

                    boolean success = ItemRandomizerHelper.saveContainersInAreaToTable(player.getServerWorld(), x1, y1, z1, x2, y2, z2, tableName, sender);
                    if (success) {
                        Messenger.m(sender, "g Table created from containers in area: ", "w " + tableName);
                    } else {
                        Messenger.m(sender, "r No containers found in area.");
                    }

                } else if (args.length == 9 && args[1].equalsIgnoreCase("area")) {
                    int x1 = Integer.parseInt(args[2]);
                    int y1 = Integer.parseInt(args[3]);
                    int z1 = Integer.parseInt(args[4]);
                    int x2 = Integer.parseInt(args[5]);
                    int y2 = Integer.parseInt(args[6]);
                    int z2 = Integer.parseInt(args[7]);
                    String tableName = args[8];

                    boolean success = ItemRandomizerHelper.saveBlockAreaToTable(player.getServerWorld(), x1, y1, z1, x2, y2, z2, tableName);
                    if (success) {
                        Messenger.m(sender, "g Table created from blocks in area: ", "w " + tableName);
                    } else {
                        Messenger.m(sender, "r No blocks found in area.");
                    }
                } else if ((args.length == 4 || args.length == 5) && args[1].equalsIgnoreCase("all_items")) {
                    String obtainability = args[2].toLowerCase();
                    String stackability = args.length == 5 ? args[3].toLowerCase() : "stackables";
                    String tableName = args.length == 5 ? args[4] : args[3];

                    List<ItemStack> items = ItemRandomizerHelper.getFilteredAllItems(obtainability, stackability);

                    if (items == null || items.isEmpty()) {
                        Messenger.m(sender, "r No items found for the specified filters.");
                        return;
                    }

                    boolean success = ItemRandomizerHelper.writeStacksToTable(items, tableName);
                    if (success) {
                        Messenger.m(sender, "g Table created from all_items: ", "w " + tableName);
                    } else {
                        Messenger.m(sender, "r Could not create table. Table may already exist.");
                    }
                } else {
                    throw new CommandException(getUsage(sender));
                }
            } else if (sub.equalsIgnoreCase("insert") && args.length >= 3) {
                String mode = args[1];
                String tableName = args[2];
                List<ItemStack> items = ItemRandomizerHelper.loadTable(tableName);
                if (items == null) {
                    Messenger.m(sender, "r Table not found.");
                    return;
                }

                TileEntity tile = ItemRandomizerHelper.getTargetedTile(player, 5.0);
                if (!(tile instanceof IInventory)) {
                    Messenger.m(sender, "r No container found.");
                    return;
                }

                boolean success = ItemRandomizerHelper.insertItems((IInventory) tile, items, mode, world);
                if (success) {
                    Messenger.m(sender, "g Items inserted using table ", "w " + tableName);
                }

            } else if ("insert_area".equalsIgnoreCase(args[0])) {
                if (args.length != 9) {
                    Messenger.m(sender, "r Usage: /itemrandomizer insert_area <mode> x1 y1 z1 x2 y2 z2 <table>");
                    return;
                }

                String mode = args[1].toLowerCase();
                if (!ItemRandomizerHelper.FILL_MODE.contains(mode)) {
                    Messenger.m(sender, "r Invalid mode: ", "w " + mode);
                    return;
                }

                int x1 = CommandBase.parseInt(args[2]);
                int y1 = CommandBase.parseInt(args[3]);
                int z1 = CommandBase.parseInt(args[4]);
                int x2 = CommandBase.parseInt(args[5]);
                int y2 = CommandBase.parseInt(args[6]);
                int z2 = CommandBase.parseInt(args[7]);
                String table = args[8];

                List<ItemStack> items = ItemRandomizerHelper.loadTable(table);
                if (items == null || items.isEmpty()) {
                    Messenger.m(sender, "r Table not found or is empty: ", "w " + table);
                    return;
                }

                BlockPos pos1 = new BlockPos(Math.min(x1, x2), Math.min(y1, y2), Math.min(z1, z2));
                BlockPos pos2 = new BlockPos(Math.max(x1, x2), Math.max(y1, y2), Math.max(z1, z2));

                int containersFilled = 0;

                for (BlockPos pos : BlockPos.getAllInBox(pos1, pos2)) {
                    TileEntity tile = world.getTileEntity(pos);
                    if (tile instanceof IInventory) {
                        IInventory inv = (IInventory) tile;
                        ItemRandomizerHelper.insertItems(inv, items, mode, world);
                        containersFilled++;
                    }
                }

                Messenger.m(sender, "g Inserted items using table ", "y " + table, "g into ", "w " + containersFilled, "g containers in area.");
            } else if (sub.equalsIgnoreCase("give") && args.length >= 4) {
                String itemId = args[1];
                String mode = args[2];
                String tableName = args[3];

                Item containerItem = Item.getByNameOrId(itemId);
                if (containerItem == null) {
                    Messenger.m(sender, "r Invalid item: ", "w " + itemId);
                    return;
                }

                List<ItemStack> items = ItemRandomizerHelper.loadTable(tableName);
                if (items == null) {
                    Messenger.m(sender, "r Table not found.");
                    return;
                }

                ItemStack shulker = ItemRandomizerHelper.createContainerWithItems(containerItem, items, mode, tableName);
                if (!player.inventory.addItemStackToInventory(shulker)) {
                    player.dropItem(shulker, false);
                }

                Messenger.m(sender, "g Given item with content from ", "w " + tableName);

            } else if (sub.equals("shulkerchest")) {
                if (args.length != 2) {
                    Messenger.m(sender, "r Usage: /itemrandomizer shulkerchest <table>");
                    return;
                }

                String tableName = args[1];
                List<ItemStack> allItems = ItemRandomizerHelper.loadTable(tableName);

                if (allItems == null || allItems.isEmpty()) {
                    Messenger.m(sender, "r Table not found or is empty: ", "w " + tableName);
                    return;
                }

                if (allItems == null || allItems.isEmpty()) {
                    Messenger.m(sender, "r No items found.");
                    return;
                }

                EnumFacing facing = player.getHorizontalFacing();
                BlockPos startPos = player.getPosition().add(facing.getXOffset(), 0, facing.getZOffset());

                BlockPos otherHalf = startPos.add(1, 0, 0);

                world.setBlockState(startPos, Blocks.CHEST.getDefaultState().withProperty(net.minecraft.block.BlockChest.FACING, facing));
                world.setBlockState(otherHalf, Blocks.CHEST.getDefaultState().withProperty(net.minecraft.block.BlockChest.FACING, facing));

                TileEntity tile1 = world.getTileEntity(startPos);
                TileEntity tile2 = world.getTileEntity(otherHalf);

                if (tile1 instanceof IInventory && tile2 instanceof IInventory) {
                    IInventory largeChest = new net.minecraft.inventory.InventoryLargeChest("container.chestDouble", (ILockableContainer) tile1, (ILockableContainer) tile2);

                    for (int i = 0; i < largeChest.getSizeInventory(); i++) {
                        List<ItemStack> shuffled = new ArrayList<>(allItems);
                        Collections.shuffle(shuffled);
                        List<ItemStack> contents = shuffled.subList(0, Math.min(27, shuffled.size()));
                        ItemStack shulker = ItemRandomizerHelper.createContainerWithItems(
                                Item.getItemFromBlock(Blocks.PURPLE_SHULKER_BOX), contents, "random", "auto");
                        largeChest.setInventorySlotContents(i, shulker);
                    }

                    ((TileEntity) tile1).markDirty();
                    ((TileEntity) tile2).markDirty();
                    world.markChunkDirty(startPos, tile1);
                    world.markChunkDirty(otherHalf, tile2);

                    Messenger.m(sender, "g Double chest filled with random shulkers created.");
                } else {
                    Messenger.m(sender, "r Could not place or access double chest.");
                }
            }
            else if (sub.equalsIgnoreCase("list")) {
                List<String> tables = ItemRandomizerHelper.getSavedTables(sender);
                if (tables.isEmpty()) {
                    Messenger.m(sender, "r No tables found.");
                } else {
                    Messenger.m(sender, "wb Tables:");
                    tables.forEach(name -> Messenger.m(sender, "w - " + name,
                            "l  [i]", "^g Display info from table", "!/itemrandomizer info " + name,
                            "r  [x]", "^g Delete table", "!/itemrandomizer delete " + name));
                }

            } else if (sub.equalsIgnoreCase("info") && args.length == 2) {
                List<ItemStack> items = ItemRandomizerHelper.loadTable(args[1]);
                if (items == null) {
                    Messenger.m(sender, "r Table not found.");
                } else {
                    Messenger.m(sender, "wb Table ", "gb " + args[1], "wb  contents:");
                    for (ItemStack item : items) {
                        Messenger.m(sender, "w - " + Item.REGISTRY.getNameForObject(item.getItem()) + ":" + item.getMetadata());
                    }
                }

            } else if (sub.equalsIgnoreCase("use")) {
                if (args.length == 2) {
                    ItemRandomizerHelper.useTables(sender, args[1], null);
                } else if (args.length == 3) {
                    ItemRandomizerHelper.useTables(sender, args[1], args[2]);
                } else {
                    Messenger.m(sender, "r Usage: /itemrandomizer use <insert|give> [container]");
                }

            } else if (sub.equalsIgnoreCase("delete") && args.length == 2) {
                File file = new File(ItemRandomizerHelper.TABLE_DIR, args[1] + ".txt");
                if (file.exists()) {
                    file.delete();
                    Messenger.m(sender, "g Table deleted: ", "r " + args[1]);
                } else {
                    Messenger.m(sender, "r Table not found.");
                }

            } else if (sub.equalsIgnoreCase("help")) {
                ItemRandomizerHelper.printHelpMessage(sender);
            } else {
                Messenger.m(sender, "r Invalid usage. Try /itemrandomizer help");
            }
        }
    }

    @Override
    public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args, BlockPos pos) {
        return ItemRandomizerHelper.getTabCompletions(sender, args);
    }
}
