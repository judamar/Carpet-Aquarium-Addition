package carpet.helpers;

import carpet.utils.Messenger;
import net.minecraft.command.CommandBase;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Items;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.WorldServer;
import net.minecraft.command.ICommandSender;

import javax.annotation.Nullable;
import java.io.*;
import java.util.*;
import java.util.stream.Collectors;

public class ItemRandomizerHelper {
    public static final File TABLE_DIR = new File("item_randomizer_tables");
    public static final List<String> MODES = Arrays.asList("create", "insert", "insert_area", "give", "list", "info", "use", "delete", "help");
    public static final List<String> FILL_MODE = Arrays.asList("full", "random", "random_single", "single_stack", "single_random", "single_stack");
    public static final List<String> CONTAINERS = Arrays.asList("shulker", "chest", "trapped_chest", "hopper", "dropper", "dispenser", "furnace");

    public static TileEntity getTargetedTile(EntityPlayerMP player, double maxDistance) {
        Vec3d eyePosition = player.getPositionEyes(1.0F);
        Vec3d look = player.getLook(1.0F);
        Vec3d reach = eyePosition.add(look.x * maxDistance, look.y * maxDistance, look.z * maxDistance);

        RayTraceResult ray = player.world.rayTraceBlocks(eyePosition, reach, false, false, false);
        return (ray != null && ray.typeOfHit == RayTraceResult.Type.BLOCK)
                ? player.world.getTileEntity(ray.getBlockPos()) : null;
    }

    public static boolean saveInventoryToTable(IInventory inv, String tableName, ICommandSender sender) {
        List<String> lines = new ArrayList<>();
        for (int i = 0; i < inv.getSizeInventory(); i++) {
            ItemStack stack = inv.getStackInSlot(i);
            if (!stack.isEmpty()) {
                ResourceLocation id = Item.REGISTRY.getNameForObject(stack.getItem());
                if (id != null) {
                    lines.add(id.toString() + ":" + stack.getMetadata());
                }
            }
        }
        if (lines.isEmpty()) return false;

        File out = new File(TABLE_DIR, tableName + ".txt");
        try (PrintWriter writer = new PrintWriter(new FileWriter(out))) {
            for (String line : lines) writer.println(line);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    public static boolean saveContainersInAreaToTable(WorldServer world, int x1, int y1, int z1, int x2, int y2, int z2, String tableName, ICommandSender sender) {
        List<ItemStack> stacks = new ArrayList<>();
        BlockPos pos1 = new BlockPos(Math.min(x1, x2), Math.min(y1, y2), Math.min(z1, z2));
        BlockPos pos2 = new BlockPos(Math.max(x1, x2), Math.max(y1, y2), Math.max(z1, z2));

        for (BlockPos pos : BlockPos.getAllInBox(pos1, pos2)) {
            TileEntity tile = world.getTileEntity(pos);
            if (tile instanceof IInventory) {
                IInventory inv = (IInventory) tile;
                for (int i = 0; i < inv.getSizeInventory(); i++) {
                    ItemStack stack = inv.getStackInSlot(i);
                    if (!stack.isEmpty()) {
                        stacks.add(stack.copy());
                    }
                }
            }
        }

        if (stacks.isEmpty()) return false;

        return writeStacksToTable(stacks, tableName);
    }

    public static boolean saveBlockAreaToTable(WorldServer world, int x1, int y1, int z1, int x2, int y2, int z2, String tableName) {
        List<ItemStack> stacks = new ArrayList<>();
        BlockPos pos1 = new BlockPos(Math.min(x1, x2), Math.min(y1, y2), Math.min(z1, z2));
        BlockPos pos2 = new BlockPos(Math.max(x1, x2), Math.max(y1, y2), Math.max(z1, z2));

        for (BlockPos pos : BlockPos.getAllInBox(pos1, pos2)) {
            Item item = Item.getItemFromBlock(world.getBlockState(pos).getBlock());
            if (item != null) {
                stacks.add(new ItemStack(item));
            }
        }

        if (stacks.isEmpty()) return false;

        return writeStacksToTable(stacks, tableName);
    }

    public static List<ItemStack> getFilteredAllItems(String obtainability, String stackability) {
        if (!GetAllItemsHelper.getObtainabilityOptions().contains(obtainability) || !GetAllItemsHelper.getStackabilityOptions().contains(stackability)) {
            return Collections.emptyList();
        }

        Set<String> seen = new HashSet<>();
        List<ItemStack> result = new ArrayList<>();

        for (Object obj : Item.REGISTRY) {
            Item item = (Item) obj;
            if (!GetAllItemsHelper.filterItem(item, obtainability, stackability)) continue;

            for (ItemStack variant : GetAllItemsHelper.getItemVariants(item)) {
                if (variant.isEmpty()) continue;
                ResourceLocation id = Item.REGISTRY.getNameForObject(variant.getItem());
                if (id == null) continue;

                String key = id.toString() + ":" + variant.getMetadata();
                if (seen.add(key)) {
                    result.add(new ItemStack(variant.getItem(), 1, variant.getMetadata()));
                }
            }
        }

        return result;
    }



    public static boolean writeStacksToTable(List<ItemStack> stacks, String tableName) {
        File out = new File(TABLE_DIR, tableName + ".txt");

        if (out.exists()) {
            return false;
        }

        Set<String> uniqueItems = new HashSet<>();

        try (PrintWriter writer = new PrintWriter(new FileWriter(out))) {
            for (ItemStack stack : stacks) {
                if (stack == null || stack.getItem() == null || stack.getItem() == Items.AIR) continue;

                ResourceLocation id = Item.REGISTRY.getNameForObject(stack.getItem());
                if (id == null) continue;

                String line = id.toString() + ":" + stack.getMetadata();

                if (uniqueItems.add(line)) {
                    writer.println(line);
                }
            }
            return true;
        } catch (IOException e) {
            return false;
        }
    }


    public static boolean insertItems(IInventory inv, List<ItemStack> items, String mode, WorldServer world) {
        for (int i = 0; i < inv.getSizeInventory(); i++) inv.setInventorySlotContents(i, ItemStack.EMPTY);

        fillInventoryWithPattern(inv, items, mode);

        if (inv instanceof TileEntity) {
            TileEntity tile = (TileEntity) inv;
            tile.markDirty();
            world.markChunkDirty(tile.getPos(), tile);
        }
        return true;
    }

    public static ItemStack createContainerWithItems(Item containerItem, List<ItemStack> items, String mode, String tableName) {
        TileEntity tile = getProperTileEntityForItem(containerItem);
        if (!(tile instanceof IInventory)) {
            return ItemStack.EMPTY;
        }

        IInventory inv = (IInventory) tile;

        fillInventoryWithPattern(inv, items, mode);

        ItemStack containerStack = new ItemStack(containerItem);
        NBTTagCompound tag = new NBTTagCompound();
        tile.writeToNBT(tag);
        containerStack.setTagInfo("BlockEntityTag", tag);
        containerStack.setStackDisplayName("§r" + tableName + "_" + mode);
        return containerStack;
    }

    public static List<ItemStack> loadTable(String name) {
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
                    int meta = parts.length == 3 ? Integer.parseInt(parts[2]) : 0;
                    if (item != null) {
                        result.add(new ItemStack(item, 1, meta));
                    }
                }
            }
        } catch (IOException | NumberFormatException e) {
            return null;
        }
        return result;
    }

    public static List<String> getSavedTables(ICommandSender sender) {
        if (!TABLE_DIR.exists()) return Collections.emptyList();
        String[] files = TABLE_DIR.list((dir, name) -> name.endsWith(".txt"));
        if (files == null) return Collections.emptyList();
        List<String> names = new ArrayList<>();
        for (String f : files) {
            names.add(f.replace(".txt", ""));
        }
        return names;
    }

    public static void useTables(ICommandSender sender, String mode, @Nullable String container) {
        List<String> tables = getSavedTables(sender);

        Messenger.m(sender, "wb Tables for use in ", "gb " + mode, "wb  mode:");

        if (mode.equals("insert")) {
            for (String table : tables) {
                if (table.isEmpty()) continue;
                printTableUsage(sender, table, "insert", null);
            }
        } else if (mode.equals("give")) {
            Messenger.m(sender, "wb Container: ", "gb " + container);

            Map<String, String> containerMap = new HashMap<>();
            containerMap.put("shulker", "minecraft:purple_shulker_box");
            containerMap.put("chest", "minecraft:chest");
            containerMap.put("trapped_chest", "minecraft:trapped_chest");
            containerMap.put("hopper", "minecraft:hopper");
            containerMap.put("dropper", "minecraft:dropper");
            containerMap.put("dispenser", "minecraft:dispenser");
            containerMap.put("furnace", "minecraft:furnace");

            String containerId = containerMap.get(container);
            if (containerId == null) {
                Messenger.m(sender, "r Unknown container: ", "w " + container);
                return;
            }

            for (String table : tables) {
                if (table.isEmpty()) continue;
                printTableUsage(sender, table, "give", containerId);
            }
        }
    }

    private static void printTableUsage(ICommandSender sender, String table, String mode, @Nullable String containerId) {
        String baseCommand = mode.equals("insert") ? "/itemrandomizer insert" : "/itemrandomizer give " + containerId;

        Messenger.m(sender,
                "w - " + table,
                "l  [F]",  "^g " + mode.toUpperCase() + " FULL",  "!" + baseCommand + " full " + table,
                "l  [R]",  "^g " + mode.toUpperCase() + " RANDOM",  "!" + baseCommand + " random " + table,
                "l  [RS]", "^g " + mode.toUpperCase() + " RANDOM SINGLE",  "!" + baseCommand + " random_single " + table,
                "l  [S]",  "^g " + mode.toUpperCase() + " SINGLE",  "!" + baseCommand + " single " + table,
                "l  [SR]", "^g " + mode.toUpperCase() + " SINGLE RANDOM",  "!" + baseCommand + " single_random " + table,
                "l  [SS]", "^g " + mode.toUpperCase() + " SINGLE STACK",  "!" + baseCommand + " single_stack " + table
        );
    }


    private static void fillInventoryWithPattern(IInventory inv, List<ItemStack> items, String mode) {
        Random rand = new Random();

        switch (mode.toLowerCase()) {
            case "full":
                for (int i = 0; i < inv.getSizeInventory(); i++) {
                    ItemStack stack = items.get(rand.nextInt(items.size())).copy();
                    stack.setCount(stack.getItem().getItemStackLimit());
                    inv.setInventorySlotContents(i, stack);
                }
                break;
            case "random":
                for (int i = 0; i < inv.getSizeInventory(); i++) {
                    ItemStack base = items.get(rand.nextInt(items.size())).copy();
                    base.setCount(1 + rand.nextInt(base.getItem().getItemStackLimit()));
                    inv.setInventorySlotContents(i, base);
                }
                break;
            case "random_single":
                for (int i = 0; i < inv.getSizeInventory(); i++) {
                    ItemStack base = items.get(rand.nextInt(items.size())).copy();
                    base.setCount(1);
                    inv.setInventorySlotContents(i, base);
                }
            case "single":
                inv.setInventorySlotContents(0, items.get(rand.nextInt(items.size())).copy());
                break;
            case "single_random":
                ItemStack base = items.get(rand.nextInt(items.size())).copy();
                base.setCount(1 + rand.nextInt(base.getItem().getItemStackLimit()));
                inv.setInventorySlotContents(0, base);
                break;
            case "single_stack":
                ItemStack stack = items.get(rand.nextInt(items.size())).copy();
                stack.setCount(stack.getItem().getItemStackLimit());
                inv.setInventorySlotContents(0, stack);
                break;
        }
    }

    private static TileEntity getProperTileEntityForItem(Item item) {
        String name = Item.REGISTRY.getNameForObject(item).toString();

        if (name.endsWith("shulker_box")) {
            return new net.minecraft.tileentity.TileEntityShulkerBox();
        } else if (item == Item.getByNameOrId("minecraft:chest") || item == Item.getByNameOrId("minecraft:trapped_chest")) {
            return new net.minecraft.tileentity.TileEntityChest();
        } else if (item == Item.getByNameOrId("minecraft:hopper")) {
            return new net.minecraft.tileentity.TileEntityHopper();
        } else if (item == Item.getByNameOrId("minecraft:dispenser")) {
            return new net.minecraft.tileentity.TileEntityDispenser();
        } else if (item == Item.getByNameOrId("minecraft:dropper")) {
            return new net.minecraft.tileentity.TileEntityDropper();
        } else if (item == Item.getByNameOrId("minecraft:furnace")) {
            return new net.minecraft.tileentity.TileEntityFurnace();
        }
        return null;
    }

    public static void printHelpMessage(ICommandSender sender) {
        Messenger.m(sender, "wb ItemRandomizer - Available Commands:");
        Messenger.m(sender, "gb ▸ /itemrandomizer create looking <name>", "y - Save the container you're looking at");
        Messenger.m(sender, "gb ▸ /itemrandomizer create containers x1 y1 z1 x2 y2 z2 <name>", "y - Save all containers in the specified area");
        Messenger.m(sender, "gb ▸ /itemrandomizer create area x1 y1 z1 x2 y2 z2 <name>", "y - Save all blocks in area as item table");
        Messenger.m(sender, "gb ▸ /itemrandomizer create all_items <obtainability> <stackability> <name>", "y - Save filtered items into a new table");
        Messenger.m(sender, "gb ▸ /itemrandomizer insert <mode> <table>", "y - Insert items into container you're looking at");
        Messenger.m(sender, "gb ▸ /itemrandomizer insert_area <mode> x1 y1 z1 x2 y2 z2 <table>", "y - Insert items into containers in area");
        Messenger.m(sender, "gb ▸ /itemrandomizer give <container> <mode> <table>", "y - Give container with items from table");
        Messenger.m(sender, "gb ▸ /itemrandomizer list", "y - List all saved tables");
        Messenger.m(sender, "gb ▸ /itemrandomizer info <table>", "y - Show items inside a table");
        Messenger.m(sender, "gb ▸ /itemrandomizer use <insert|give> [container]", "y - Show clickable actions for tables");
        Messenger.m(sender, "gb ▸ /itemrandomizer delete <table>", "y - Delete a saved table");
        Messenger.m(sender, "gb ▸ /itemrandomizer help", "y - Show this help message");
    }


    public static List<String> getTabCompletions(ICommandSender sender, String[] args) {
        if (args.length == 1) {
            return getListOfStringsMatchingLastWord(args, MODES);
        } else if (args.length == 2) {
            switch (args[0].toLowerCase()) {
                case "info":
                case "delete":
                    return getListOfStringsMatchingLastWord(args, getSavedTables(sender));
                case "use":
                    return getListOfStringsMatchingLastWord(args, Arrays.asList("insert", "give"));
                case "insert":
                    return getListOfStringsMatchingLastWord(args, FILL_MODE);
                case "insert_area":
                    return getListOfStringsMatchingLastWord(args, FILL_MODE);
                case "give":
                    return getListOfStringsMatchingLastWord(args, Item.REGISTRY.getKeys().stream().map(ResourceLocation::toString).collect(Collectors.toList()));
                case "create":
                    return getListOfStringsMatchingLastWord(args, Arrays.asList("looking", "containers", "area", "all_items"));
            }
        } else if (args.length >= 3 && args[0].equalsIgnoreCase("create")) {
            String sub = args[1].toLowerCase();
            switch (sub) {
                case "containers":
                case "area":
                    if (args.length >= 3 && args.length <= 8) {
                        return getCoordinateSuggestions(sender, args);
                    } else if (args.length == 9) {
                        return Collections.singletonList("table_name");
                    }
                    break;
                case "looking":
                    if (args.length == 3) return Collections.singletonList("table_name");
                    break;
                case "all_items":
                    if (args.length == 3)
                        return getListOfStringsMatchingLastWord(args, GetAllItemsHelper.getObtainabilityOptions());
                    else if (args.length == 4)
                        return getListOfStringsMatchingLastWord(args, GetAllItemsHelper.getStackabilityOptions());
                    else if (args.length == 5)
                        return Collections.singletonList("table_name");
                    break;
            }
        } else if (args.length == 3 && args[0].equalsIgnoreCase("use") && args[1].equalsIgnoreCase("give")) {
            return getListOfStringsMatchingLastWord(args, CONTAINERS);
        } else if (args.length == 3 && args[0].equalsIgnoreCase("insert")) {
            return getListOfStringsMatchingLastWord(args, getSavedTables(sender));
        } else if (args.length >= 3 && args[0].equalsIgnoreCase("insert_area")) {
            if (args.length >= 3 && args.length <= 8) {
                return getCoordinateSuggestions(sender, args);
            } else if (args.length == 9) {
                return getListOfStringsMatchingLastWord(args, getSavedTables(sender));
            }
        } else if (args.length == 4 && args[0].equalsIgnoreCase("give")) {
            return getListOfStringsMatchingLastWord(args, FILL_MODE);
        } else if (args.length == 5 && args[0].equalsIgnoreCase("give")) {
            return getListOfStringsMatchingLastWord(args, getSavedTables(sender));
        }

        return Collections.emptyList();
    }

    private static List<String> getCoordinateSuggestions(ICommandSender sender, String[] args) {
        if (!(sender instanceof EntityPlayerMP)) return Collections.emptyList();

        EntityPlayerMP player = (EntityPlayerMP) sender;

        Vec3d eyePos = player.getPositionEyes(1.0F);
        Vec3d lookVec = player.getLook(1.0F);
        Vec3d reachVec = eyePos.add(lookVec.scale(20.0D));

        RayTraceResult ray = player.world.rayTraceBlocks(eyePos, reachVec, false, false, false);
        BlockPos pos = (ray != null && ray.typeOfHit == RayTraceResult.Type.BLOCK)
                ? ray.getBlockPos()
                : player.getPosition();

        int argIndex = args.length - 1;
        int coordSetStart = (argIndex >= 5) ? 5 : 2;

        return CommandBase.getTabCompletionCoordinate(args, coordSetStart, pos);
    }





    private static List<String> getListOfStringsMatchingLastWord(String[] args, Collection<String> options) {
        String last = args[args.length - 1].toLowerCase();
        return options.stream()
                .filter(option -> option.toLowerCase().startsWith(last))
                .sorted()
                .collect(Collectors.toList());
    }
}
