//ported from: https://github.com/CommandLeo/scarpet/blob/main/programs/stx.sc (phase 1)
package carpet.commands;

import carpet.CarpetSettings;
import carpet.helpers.GetAllItemsHelper;
import carpet.helpers.StorageTech;
import carpet.utils.Messenger;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.item.EnumDyeColor;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;

import javax.annotation.Nullable;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class CommandStx extends CommandCarpetBase {

    private static final String[] SETTINGS = {"encoder_max_filling", "encoder_fill_all_slots", "encoder_item_amount", "prefer_unstackables", "shulker_box_color"};
    private static final String[] SOURCES = {"items", "containers", "item_frames", "area", "item_list", "all_items"};

    @Override
    public String getName() {
        return "stx";
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "/stx container <mode> <from> <to> <item[:meta]...|item_list <name>...> | /stx encoder <signal> fill <from> <to> <item[:meta]...|item_list <name>...> | /stx encoder <signal|auto> binary <chests_per_bit> <from> <to> <item[:meta]...|item_list <name>...> [suffix <bits>] [chest <n>] (chest = 1-based chest of every bit group where the chain starts, default 1, earlier chests untouched unless chest 1 of a group is completely empty, then that group starts at chest 1; auto/pre-encoded chests keep their signal and get new items appended; suffix = last groups are fixed bits 0/1 (1 = whole list); any cuboid; double chests count once; row runs along the longest axis starting at from; first group of chests_per_bit = bit 0; a group is a chain: the bit's items fill its first chest, only the overflow goes to the next chest, untouched chests are left as they are) | /stx encoder <signal> fix <from> [to] | /stx undo (restores the inventories of your last 10 container/encoder operations, newest first) | /stx item_list <create|list|info|delete|rename|clone|edit> | /stx settings [name value] (16-stack items consume more of the head budget and overflow to the next chest; encoder_max_filling is off by default: the last slots of an encoder chest are always the Blocker + wooden axe dummies; on lets real items fill them)";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 2;
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        if (!command_enabled("commandStx", sender)) return;
        String key = sender instanceof net.minecraft.entity.player.EntityPlayer ? ((net.minecraft.entity.player.EntityPlayer) sender).getUniqueID().toString() : "console"; //AqCM
        if (args.length > 0 && args[0].equals("undo")) {
            int[] u = StorageTech.undo(key);
            if (u == null) Messenger.m(sender, "r Nothing to undo");
            else Messenger.m(sender, "g Undone: restored ", "w " + u[0], "g  inventories, skipped ", "w " + u[1]);
            return;
        }
        boolean writes = args.length > 0 && (args[0].equals("container") || args[0].equals("encoder"));
        if (writes) StorageTech.beginUndo();
        try {
            run(server, sender, args);
        } finally {
            if (writes) StorageTech.endUndo(key);
        }
    }

    private void run(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        if (args.length == 0) throw new WrongUsageException(getUsage(sender));

        switch (args[0]) {
            case "container": {
                if (args.length < 9 || !Arrays.asList(StorageTech.MODES).contains(args[1]))
                    throw new WrongUsageException("/stx container <" + String.join("|", StorageTech.MODES) + "> <from> <to> <item[:meta]... | item_list <name>...>");
                BlockPos from = parseBlockPos(sender, args, 2, false);
                BlockPos to = parseBlockPos(sender, args, 5, false);
                checkVolume(from, to);
                List<ItemStack> items = new ArrayList<>();
                for (List<ItemStack> l : parseLists(server, args, 8)) items.addAll(l);
                if (items.isEmpty()) throw new CommandException("No items provided");
                int n = StorageTech.container(sender.getEntityWorld(), from, to, args[1], items);
                Messenger.m(sender, "g Filled ", "w " + n, "g  container" + (n == 1 ? "" : "s") + " (" + args[1] + ")");
                return;
            }
            case "encoder": {
                if (args.length < 6) throw new WrongUsageException(getUsage(sender));
                String suffix = ""; //AqCM
                int startChest = 1;
                boolean sawSuffix = false, sawChest = false;
                while (args.length >= 4 && args[2].equals("binary") && args.length >= 6 && (args[args.length - 2].equals("suffix") || args[args.length - 2].equals("chest"))) {
                    String v = args[args.length - 1];
                    if (args[args.length - 2].equals("suffix")) {
                        if (sawSuffix) break;
                        sawSuffix = true;
                        suffix = v;
                        if (!suffix.matches("[01]+")) throw new CommandException("suffix must be a string of 0 and 1, got " + suffix);
                    } else {
                        if (sawChest) break;
                        sawChest = true;
                        try { startChest = Integer.parseInt(v); } catch (NumberFormatException e) { throw new CommandException("chest must be a number, got " + v); }
                        if (startChest < 1) throw new CommandException("chest must be at least 1, got " + startChest);
                    }
                    args = Arrays.copyOf(args, args.length - 2);
                }
                int signal = args[1].equals("auto") && args[2].equals("binary") ? 0 : parseInt(args[1], 1, 15);
                if (args[2].equals("fill") && args.length >= 10) {
                    BlockPos from = parseBlockPos(sender, args, 3, false);
                    BlockPos to = parseBlockPos(sender, args, 6, false);
                    checkVolume(from, to);
                    done(sender, "Filled", StorageTech.encodeFill(sender.getEntityWorld(), from, to, signal, parseLists(server, args, 9)));
                    return;
                }
                if (args[2].equals("binary") && args.length >= 11) { //AqCM
                    int perBit = parseInt(args[3], 1);
                    BlockPos from = parseBlockPos(sender, args, 4, false);
                    BlockPos to = parseBlockPos(sender, args, 7, false);
                    checkVolume(from, to);
                    List<ItemStack> items = new ArrayList<>();
                    for (List<ItemStack> l : parseLists(server, args, 10)) items.addAll(l);
                    if (items.isEmpty()) throw new CommandException("No items provided");
                    StorageTech.EncodeResult r = StorageTech.encodeBinary(sender.getEntityWorld(), from, to, signal, perBit, items, suffix, startChest);
                    for (String w : r.warnings) Messenger.m(sender, "rb WARNING!", "g  " + w);
                    Messenger.m(sender, "g Binary encoded ", "w " + r.affected, "g  chests (", "w " + r.perChest.size(), "g  bits x " + perBit + " per bit; code bits " + r.codeBits + (suffix.isEmpty() ? "" : ", suffix " + suffix) + (startChest > 1 ? ", start chest " + startChest : "") + ") with ", "w " + items.size(), "g  items; items per bit: ", "w " + r.perChest, "g , chests used: ", "w " + r.chestsUsed,
                            "g " + (r.fallback > 0 ? "; chest 1 was unconfigured in " + r.fallback + " groups, started there" : "") + (r.kept > 0 ? "; " + r.kept + " chests kept their own signal" : "") + (r.dupes > 0 ? "; " + r.dupes + " items already present skipped" : ""));
                    return;
                }
                if (args[2].equals("fix") && (args.length == 6 || args.length == 9)) {
                    BlockPos from = parseBlockPos(sender, args, 3, false);
                    BlockPos to = args.length == 9 ? parseBlockPos(sender, args, 6, false) : from;
                    checkVolume(from, to);
                    done(sender, "Fixed", StorageTech.encodeFix(sender.getEntityWorld(), from, to, signal));
                    return;
                }
                throw new WrongUsageException(getUsage(sender));
            }
            case "item_list":
                itemList(server, sender, args);
                return;
            case "settings":
                settings(sender, args);
                return;
            default:
                throw new WrongUsageException(getUsage(sender));
        }
    }

    /** item[:meta]... (one list) or item_list <name>... (one list per name). */
    private List<List<ItemStack>> parseLists(MinecraftServer server, String[] args, int start) throws CommandException {
        List<List<ItemStack>> r = new ArrayList<>();
        if (args[start].equals("item_list")) {
            if (args.length == start + 1) throw new CommandException("No item list provided");
            for (int i = start + 1; i < args.length; i++) r.add(StorageTech.readList(world(server), args[i]));
        } else {
            List<ItemStack> l = new ArrayList<>();
            for (int i = start; i < args.length; i++) l.add(StorageTech.parseItem(args[i]));
            r.add(l);
        }
        return r;
    }

    private File world(MinecraftServer server) {
        return server.getWorld(0).getSaveHandler().getWorldDirectory();
    }

    /** Item source shared by item_list create / edit add. */
    private List<ItemStack> source(MinecraftServer server, ICommandSender sender, String[] args, int i) throws CommandException {
        if (args.length <= i) throw new WrongUsageException("... <items <items...>|containers <from> [to]|item_frames <from> [to]|area <from> [to] (blocks + item frames)|all_items <obtainability> [stackability]|item_list <names...>>");
        List<ItemStack> r = new ArrayList<>();
        switch (args[i]) {
            case "items":
                if (args.length == i + 1) throw new CommandException("No items provided");
                for (int j = i + 1; j < args.length; j++) StorageTech.addUnique(r, StorageTech.parseItem(args[j]));
                return r;
            case "containers":
            case "item_frames":
            case "area": {
                if (args.length != i + 4 && args.length != i + 7) throw new WrongUsageException("... " + args[i] + " <from> [to]");
                BlockPos from = parseBlockPos(sender, args, i + 1, false);
                BlockPos to = args.length == i + 7 ? parseBlockPos(sender, args, i + 4, false) : from;
                checkVolume(from, to);
                if (args[i].equals("item_frames")) return StorageTech.fromItemFrames(sender.getEntityWorld(), from, to);
                if (args[i].equals("area")) {
                    List<String> ignored = new ArrayList<>();
                    List<ItemStack> res = StorageTech.fromArea(sender.getEntityWorld(), from, to, ignored);
                    if (!ignored.isEmpty()) Messenger.m(sender, "g Skipped while reading the area (no item or duplicate): ", "w " + String.join(", ", ignored));
                    return res;
                }
                return StorageTech.fromContainers(sender.getEntityWorld(), from, to);
            }
            case "all_items": {
                if (args.length < i + 2 || args.length > i + 3) throw new WrongUsageException("... all_items <obtainability> [stackability]");
                String st = args.length == i + 3 ? args[i + 2] : "any";
                if (!GetAllItemsHelper.getObtainabilityOptions().contains(args[i + 1]) || !GetAllItemsHelper.getStackabilityOptions().contains(st))
                    throw new CommandException("Invalid obtainability or stackability");
                return StorageTech.allItems(args[i + 1], st);
            }
            case "item_list":
                for (int j = i + 1; j < args.length; j++) for (ItemStack s : StorageTech.readList(world(server), args[j])) StorageTech.addUnique(r, s);
                return r;
        }
        throw new WrongUsageException("... <items|containers|item_frames|area|all_items|item_list> ...");
    }

    private void itemList(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        File w = world(server);
        if (args.length < 2) throw new WrongUsageException("/stx item_list <create|list|info|delete|rename|clone|edit> ...");
        if (args[1].equals("list")) {
            List<String> names = StorageTech.listNames(w);
            Messenger.m(sender, "g Item lists (" + names.size() + "): ", "w " + String.join(", ", names));
            return;
        }
        if (args.length < 3) throw new WrongUsageException("/stx item_list " + args[1] + " <name> ...");
        String name = args[2];
        switch (args[1]) {
            case "create": {
                if (StorageTech.listExists(w, name)) throw new CommandException("The item list " + name + " already exists");
                List<ItemStack> items = source(server, sender, args, 3);
                if (items.isEmpty()) throw new CommandException("No items found");
                StorageTech.writeList(w, name, items);
                Messenger.m(sender, "g Created item list ", "w " + name, "g  with " + items.size() + " entries");
                return;
            }
            case "info": {
                List<ItemStack> l = StorageTech.readList(w, name);
                Messenger.m(sender, "g Item list ", "w " + name, "g  (" + l.size() + " entries):");
                for (int i = 0; i < l.size(); i++) {
                    ItemStack s = l.get(i);
                    Messenger.m(sender, "g  " + i + ": ", "w " + Item.REGISTRY.getNameForObject(s.getItem()) + (s.getMetadata() != 0 ? ":" + s.getMetadata() : "") + (s.hasTagCompound() ? "*" : ""));
                }
                return;
            }
            case "delete": {
                StorageTech.readList(w, name);
                if (args.length != 4 || !args[3].equals("confirm")) {
                    Messenger.m(sender, "g Run ", "wb /stx item_list delete " + name + " confirm", "g  to delete it");
                    return;
                }
                StorageTech.listFile(w, name).delete();
                Messenger.m(sender, "g Deleted item list ", "w " + name);
                return;
            }
            case "rename":
            case "clone": {
                if (args.length != 4) throw new WrongUsageException("/stx item_list " + args[1] + " <name> <new_name>");
                List<ItemStack> l = StorageTech.readList(w, name);
                if (StorageTech.listExists(w, args[3])) throw new CommandException("The item list " + args[3] + " already exists");
                StorageTech.writeList(w, args[3], l);
                if (args[1].equals("rename")) StorageTech.listFile(w, name).delete();
                Messenger.m(sender, "g Item list ", "w " + name, "g  " + (args[1].equals("rename") ? "renamed" : "cloned") + " to ", "w " + args[3]);
                return;
            }
            case "edit": {
                List<ItemStack> l = StorageTech.readList(w, name);
                if (args.length < 4) throw new WrongUsageException("/stx item_list edit <name> <add|remove> ...");
                if (args[3].equals("add")) {
                    boolean ordered = args.length > 4 && (args[4].equals("containers") || args[4].equals("item_frames") || args[4].equals("area")); //AqCM: no dedupe, index = item number
                    for (ItemStack s : source(server, sender, args, 4)) { if (ordered) l.add(s); else StorageTech.addUnique(l, s); }
                } else if (args[3].equals("remove") && args.length >= 6 && args[4].equals("index")) {
                    l.remove(parseInt(args[5], 0, l.size() - 1));
                } else if (args[3].equals("remove") && args.length >= 6 && args[4].equals("items")) {
                    for (int j = 5; j < args.length; j++) {
                        ItemStack t = StorageTech.parseItem(args[j]);
                        l.removeIf(s -> ItemStack.areItemsEqual(s, t));
                    }
                } else throw new WrongUsageException("/stx item_list edit <name> <add <source>|remove index <n>|remove items <items>>");
                StorageTech.writeList(w, name, l);
                Messenger.m(sender, "g Item list ", "w " + name, "g  now has " + l.size() + " entries");
                return;
            }
        }
        throw new WrongUsageException("/stx item_list <create|list|info|delete|rename|clone|edit> ...");
    }

    private void checkVolume(BlockPos a, BlockPos b) throws CommandException {
        long v = (long) (Math.abs(a.getX() - b.getX()) + 1) * (Math.abs(a.getY() - b.getY()) + 1) * (Math.abs(a.getZ() - b.getZ()) + 1);
        if (v > CarpetSettings.fillLimit) throw new CommandException("Too many blocks in the specified area (" + v + " > " + CarpetSettings.fillLimit + ")");
    }

    private void done(ICommandSender sender, String verb, StorageTech.EncodeResult r) {
        for (String w : r.warnings) Messenger.m(sender, "rb WARNING!", "g  " + w);
        Messenger.m(sender, "g " + verb + " ", "w " + r.affected, "g  encoder chest" + (r.affected == 1 ? "" : "s"));
    }

    private void settings(ICommandSender sender, String[] args) throws CommandException {
        if (args.length == 1) {
            Messenger.m(sender, "w encoder_max_filling: ", "g " + StorageTech.encoderMaxFilling, "g  (off by default: the last slots are always the Blocker + axe dummies so the config tail never changes; on = real items may fill the tail)"); //AqCM
            Messenger.m(sender, "w encoder_fill_all_slots: ", "g " + StorageTech.encoderFillAllSlots);
            Messenger.m(sender, "w encoder_item_amount: ", "g " + StorageTech.encoderItemAmount);
            Messenger.m(sender, "w prefer_unstackables: ", "g " + StorageTech.preferUnstackables);
            Messenger.m(sender, "w shulker_box_color: ", "g " + StorageTech.shulkerBoxColor.getName());
            return;
        }
        if (args.length != 3) throw new WrongUsageException("/stx settings [<" + String.join("|", SETTINGS) + "> <value>]");
        switch (args[1]) {
            case "encoder_max_filling": StorageTech.encoderMaxFilling = parseBoolean(args[2]); break;
            case "encoder_fill_all_slots": StorageTech.encoderFillAllSlots = parseBoolean(args[2]); break;
            case "encoder_item_amount": StorageTech.encoderItemAmount = parseInt(args[2], 1, 64); break;
            case "prefer_unstackables": StorageTech.preferUnstackables = parseBoolean(args[2]); break;
            case "shulker_box_color": {
                EnumDyeColor c = null;
                for (EnumDyeColor d : EnumDyeColor.values()) if (d.getName().equalsIgnoreCase(args[2])) c = d;
                if (c == null) throw new CommandException("Invalid color: " + args[2]);
                StorageTech.shulkerBoxColor = c;
                break;
            }
            default: throw new WrongUsageException("/stx settings [<" + String.join("|", SETTINGS) + "> <value>]");
        }
        Messenger.m(sender, "g Set ", "w " + args[1], "g  to ", "w " + args[2]);
    }

    private List<String> itemOrListCompletions(MinecraftServer server, String[] args, int start) {
        if (args.length == start + 1) {
            List<String> r = new ArrayList<>();
            for (ResourceLocation k : Item.REGISTRY.getKeys()) r.add(k.toString());
            r.add("item_list");
            return getListOfStringsMatchingLastWord(args, r);
        }
        if (args[start].equals("item_list")) return getListOfStringsMatchingLastWord(args, StorageTech.listNames(world(server)));
        return getListOfStringsMatchingLastWord(args, Item.REGISTRY.getKeys());
    }

    @Override
    public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args, @Nullable BlockPos pos) {
        if (!CarpetSettings.commandStx) return Collections.emptyList();
        if (args.length == 1) return getListOfStringsMatchingLastWord(args, "container", "encoder", "item_list", "undo", "settings");
        switch (args[0]) {
            case "container":
                if (args.length == 2) return getListOfStringsMatchingLastWord(args, StorageTech.MODES);
                if (args.length <= 5) return getTabCompletionCoordinate(args, 2, pos);
                if (args.length <= 8) return getTabCompletionCoordinate(args, 5, pos);
                return itemOrListCompletions(server, args, 8);
            case "encoder":
                if (args.length == 2) return getListOfStringsMatchingLastWord(args, "1", "8", "15", "auto");
                if (args.length == 3) return getListOfStringsMatchingLastWord(args, "fill", "binary", "fix");
                if (args[2].equals("binary")) {
                    if (args.length == 4) return getListOfStringsMatchingLastWord(args, "1", "2", "3", "6");
                    if (args.length <= 7) return getTabCompletionCoordinate(args, 4, pos);
                    if (args.length <= 10) return getTabCompletionCoordinate(args, 7, pos);
                    if (args.length >= 12 && (args[args.length - 2].equals("suffix") || args[args.length - 2].equals("chest"))) return Collections.emptyList();
                    List<String> r = itemOrListCompletions(server, args, 10);
                    if (args.length >= 12) {
                        r = new ArrayList<>(r);
                        for (String k : new String[]{"suffix", "chest"}) if (k.startsWith(args[args.length - 1])) r.add(k);
                    }
                    return r;
                }
                if (args.length <= 6) return getTabCompletionCoordinate(args, 3, pos);
                if (args.length <= 9) return getTabCompletionCoordinate(args, 6, pos);
                return args[2].equals("fill") ? itemOrListCompletions(server, args, 9) : Collections.<String>emptyList();
            case "item_list": {
                List<String> names = StorageTech.listNames(world(server));
                if (args.length == 2) return getListOfStringsMatchingLastWord(args, "create", "list", "info", "delete", "rename", "clone", "edit");
                if (args.length == 3 && !args[1].equals("create") && !args[1].equals("list")) return getListOfStringsMatchingLastWord(args, names);
                boolean edit = args[1].equals("edit");
                if (edit && args.length == 4) return getListOfStringsMatchingLastWord(args, "add", "remove");
                if (edit && args[3].equals("remove")) return args.length == 5 ? getListOfStringsMatchingLastWord(args, "index", "items") : getListOfStringsMatchingLastWord(args, Item.REGISTRY.getKeys());
                int src = args[1].equals("create") ? 3 : edit && args[3].equals("add") ? 4 : -1;
                if (src < 0 || args.length <= src) return Collections.emptyList(); //AqCM: args.length==3 is the new list name
                if (args.length == src + 1) return getListOfStringsMatchingLastWord(args, SOURCES);
                switch (args[src]) {
                    case "items": return getListOfStringsMatchingLastWord(args, Item.REGISTRY.getKeys());
                    case "item_list": return getListOfStringsMatchingLastWord(args, names);
                    case "all_items": return getListOfStringsMatchingLastWord(args, args.length == src + 2 ? GetAllItemsHelper.getObtainabilityOptions() : GetAllItemsHelper.getStackabilityOptions());
                    case "containers":
                    case "item_frames":
                    case "area": return getTabCompletionCoordinate(args, args.length <= src + 4 ? src + 1 : src + 4, pos);
                }
                return Collections.emptyList();
            }
            case "settings":
                if (args.length == 2) return getListOfStringsMatchingLastWord(args, SETTINGS);
                if (args.length == 3) {
                    if (args[1].equals("encoder_max_filling") || args[1].equals("encoder_fill_all_slots") || args[1].equals("prefer_unstackables")) return getListOfStringsMatchingLastWord(args, "true", "false");
                    if (args[1].equals("shulker_box_color")) {
                        List<String> n = new ArrayList<>();
                        for (EnumDyeColor d : EnumDyeColor.values()) n.add(d.getName());
                        return getListOfStringsMatchingLastWord(args, n);
                    }
                }
        }
        return Collections.emptyList();
    }
}
