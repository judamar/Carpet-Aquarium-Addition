package carpet.commands;

import carpet.utils.Messenger;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityShulkerBox;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.WorldServer;

import java.io.*;
import java.util.*;
import java.util.stream.Collectors;

public class CommandItemRandomizer extends CommandCarpetBase {
    private static final File TABLE_DIR = new File("item_randomizer_tables");

    @Override
    public String getName() {
        return "itemrandomizer";
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "/itemrandomizer <create|insert|give|list|info|delete> [...]";
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        if (!(sender instanceof EntityPlayerMP)) {
            Messenger.m(sender, "r Only players can use this command.");
            return;
        }

        EntityPlayerMP player = (EntityPlayerMP) sender;
        WorldServer world = player.getServerWorld();

        if (!TABLE_DIR.exists()) {
            TABLE_DIR.mkdirs();
        }

        if (args.length < 1) {
            Messenger.m(sender, "r Missing subcommand.");
            return;
        }

        String sub = args[0];

        if (sub.equalsIgnoreCase("create") && args.length == 2) {
            TileEntity tile = getTargetedTile(player, 5.0);

            if (tile instanceof IInventory) {
                String tableName = args[1];
                List<String> lines = new ArrayList<>();
                IInventory inv = (IInventory) tile;
                for (int i = 0; i < inv.getSizeInventory(); i++) {
                    ItemStack stack = inv.getStackInSlot(i);
                    if (!stack.isEmpty()) {
                        ResourceLocation id = Item.REGISTRY.getNameForObject(stack.getItem());
                        if (id != null) {
                            lines.add(id.toString() + ":" + stack.getMetadata());
                        }
                    }
                }
                if (!lines.isEmpty()) {
                    File out = new File(TABLE_DIR, tableName + ".txt");
                    try (PrintWriter writer = new PrintWriter(new FileWriter(out))) {
                        for (String line : lines) {
                            writer.println(line);
                        }
                        Messenger.m(sender, "g Table created: ", "w " + tableName);
                    } catch (IOException e) {
                        Messenger.m(sender, "r Error writing table.");
                    }
                } else {
                    Messenger.m(sender, "r No items found in container.");
                }
            } else {
                Messenger.m(sender, "r No container found.");
            }

        } else if (sub.equalsIgnoreCase("insert") && args.length >= 3) {
            String mode = args[1];
            String tableName = args[2];
            List<ItemStack> items = loadTable(tableName);
            if (items == null) {
                Messenger.m(sender, "r Table not found.");
                return;
            }

            TileEntity tile = getTargetedTile(player, 5.0);
            if (!(tile instanceof IInventory)) {
                Messenger.m(sender, "r No container found.");
                return;
            }

            IInventory inv = (IInventory) tile;
            Random rand = new Random();

            for (int i = 0; i < inv.getSizeInventory(); i++) {
                inv.setInventorySlotContents(i, ItemStack.EMPTY);
            }

            switch (mode.toLowerCase()) {
                case "single":
                    inv.setInventorySlotContents(0, items.get(rand.nextInt(items.size())).copy());
                    break;
                case "random":
                    for (int i = 0; i < inv.getSizeInventory(); i++) {
                        ItemStack base = items.get(rand.nextInt(items.size())).copy();
                        int max = base.getItem().getItemStackLimit();
                        base.setCount(1 + rand.nextInt(max));
                        inv.setInventorySlotContents(i, base);
                    }
                    break;
                case "box_full":
                    for (int i = 0; i < inv.getSizeInventory(); i++) {
                        ItemStack stack = items.get(rand.nextInt(items.size())).copy();
                        int max = stack.getItem().getItemStackLimit();
                        stack.setCount(max);
                        inv.setInventorySlotContents(i, stack);
                    }
                    break;
                default:
                    Messenger.m(sender, "r Invalid mode.");
                    return;
            }

            tile.markDirty();
            world.markChunkDirty(tile.getPos(), tile);
            Messenger.m(sender, "g Items inserted using table ", "w " + tableName);

        } else if (sub.equalsIgnoreCase("give") && args.length >= 4) {
            String itemId = args[1];
            String mode = args[2];
            String tableName = args[3];

            Item containerItem = Item.getByNameOrId(itemId);
            if (containerItem == null) {
                Messenger.m(sender, "r Invalid item: ", "w " + itemId);
                return;
            }

            List<ItemStack> items = loadTable(tableName);
            if (items == null) {
                Messenger.m(sender, "r Table not found.");
                return;
            }

            Random rand = new Random();
            TileEntityShulkerBox fakeBox = new TileEntityShulkerBox();
            IInventory inv = fakeBox;

            switch (mode.toLowerCase()) {
                case "single":
                    inv.setInventorySlotContents(0, items.get(rand.nextInt(items.size())).copy());
                    break;
                case "random":
                    for (int i = 0; i < inv.getSizeInventory(); i++) {
                        ItemStack base = items.get(rand.nextInt(items.size())).copy();
                        int max = base.getItem().getItemStackLimit();
                        base.setCount(1 + rand.nextInt(max));
                        inv.setInventorySlotContents(i, base);
                    }
                    break;
                case "box_full":
                    for (int i = 0; i < inv.getSizeInventory(); i++) {
                        ItemStack stack = items.get(rand.nextInt(items.size())).copy();
                        int max = stack.getItem().getItemStackLimit();
                        stack.setCount(max);
                        inv.setInventorySlotContents(i, stack);
                    }
                    break;
                default:
                    Messenger.m(sender, "r Invalid mode.");
                    return;
            }

            ItemStack shulker = new ItemStack(containerItem);
            net.minecraft.nbt.NBTTagCompound tag = new net.minecraft.nbt.NBTTagCompound();
            fakeBox.writeToNBT(tag);
            shulker.setTagInfo("BlockEntityTag", tag);
            shulker.setStackDisplayName("§r" + tableName);

            if (!player.inventory.addItemStackToInventory(shulker)) {
                player.dropItem(shulker, false);
            }

            Messenger.m(sender, "g Given item with content from ", "w " + tableName);

        } else if (sub.equalsIgnoreCase("list")) {
            String[] files = TABLE_DIR.list((dir, name) -> name.endsWith(".txt"));
            if (files == null || files.length == 0) {
                Messenger.m(sender, "y No tables found.");
            } else {
                Messenger.m(sender, "g Tables:");
                for (String f : files) {
                    Messenger.m(sender, "w - " + f.replace(".txt", ""));
                }
            }

        } else if (sub.equalsIgnoreCase("info") && args.length == 2) {
            List<ItemStack> items = loadTable(args[1]);
            if (items == null) {
                Messenger.m(sender, "r Table not found.");
            } else {
                Messenger.m(sender, "g Table contents:");
                for (ItemStack item : items) {
                    Messenger.m(sender, "w - " + Item.REGISTRY.getNameForObject(item.getItem()) + ":" + item.getMetadata());
                }
            }

        } else if (sub.equalsIgnoreCase("delete") && args.length == 2) {
            File file = new File(TABLE_DIR, args[1] + ".txt");
            if (file.exists()) {
                file.delete();
                Messenger.m(sender, "g Table deleted: ", "w " + args[1]);
            } else {
                Messenger.m(sender, "r Table not found.");
            }

        } else {
            Messenger.m(sender, "r Invalid usage. Try /itemrandomizer help");
        }
    }

    private TileEntity getTargetedTile(EntityPlayerMP player, double maxDistance) {
        Vec3d eyePosition = player.getPositionEyes(1.0F);
        Vec3d look = player.getLook(1.0F);
        Vec3d reach = eyePosition.add(look.x * maxDistance, look.y * maxDistance, look.z * maxDistance);

        RayTraceResult ray = player.world.rayTraceBlocks(eyePosition, reach, false, false, false);
        if (ray != null && ray.typeOfHit == RayTraceResult.Type.BLOCK) {
            BlockPos pos = ray.getBlockPos();
            return player.world.getTileEntity(pos);
        }
        return null;
    }

    private List<ItemStack> loadTable(String name) {
        File file = new File(TABLE_DIR, name + ".txt");
        if (!file.exists()) return null;

        List<ItemStack> result = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.trim().split(":");
                if (parts.length >= 2) {
                    String itemId = parts[0] + ":" + parts[1];
                    Item item = Item.getByNameOrId(itemId);
                    int meta = 0;
                    if (parts.length == 3) {
                        try {
                            meta = Integer.parseInt(parts[2]);
                        } catch (NumberFormatException ignored) {}
                    }
                    if (item != null) {
                        result.add(new ItemStack(item, 1, meta));
                    }
                }
            }
        } catch (IOException e) {
            return null;
        }
        return result;
    }

    @Override
    public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args, BlockPos pos) {
        if (args.length == 1) {
            return getListOfStringsMatchingLastWord(args, Arrays.asList("create", "insert", "give", "list", "info", "delete"));
        }
        else if (args.length == 2) {
            if (args[0].equalsIgnoreCase("create") || args[0].equalsIgnoreCase("info") || args[0].equalsIgnoreCase("delete")) {
                return getListOfStringsMatchingLastWord(args, getSavedTables());
            }
            else if (args[0].equalsIgnoreCase("insert")) {
                return getListOfStringsMatchingLastWord(args, Arrays.asList("single", "random", "box_full"));
            }
            else if (args[0].equalsIgnoreCase("give")) {
                return getListOfStringsMatchingLastWord(args, Item.REGISTRY.getKeys().stream().map(ResourceLocation::toString).collect(Collectors.toList()));
            }
        }
        else if (args.length == 3) {
            if (args[0].equalsIgnoreCase("insert")) {
                return getListOfStringsMatchingLastWord(args, getSavedTables());
            }
            else if (args[0].equalsIgnoreCase("give")) {
                return getListOfStringsMatchingLastWord(args, Arrays.asList("single", "random", "box_full"));
            }
        }
        else if (args.length == 4 && args[0].equalsIgnoreCase("give")) {
            return getListOfStringsMatchingLastWord(args, getSavedTables());
        }
        return Collections.emptyList();
    }

    private List<String> getSavedTables() {
        if (!TABLE_DIR.exists()) return Collections.emptyList();
        String[] files = TABLE_DIR.list((dir, name) -> name.endsWith(".txt"));
        if (files == null) return Collections.emptyList();
        List<String> names = new ArrayList<>();
        for (String f : files) {
            names.add(f.replace(".txt", ""));
        }
        return names;
    }

}
