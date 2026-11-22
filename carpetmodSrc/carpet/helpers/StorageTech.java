//ported from: https://github.com/CommandLeo/scarpet/blob/main/programs/stx.sc (phase 1)
package carpet.helpers;

import net.minecraft.block.Block;
import net.minecraft.block.BlockChest;
import net.minecraft.block.BlockShulkerBox;
import net.minecraft.command.CommandException;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.InventoryBasic;
import net.minecraft.inventory.InventoryLargeChest;
import net.minecraft.item.EnumDyeColor;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.JsonToNBT;
import net.minecraft.nbt.NBTException;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Storage tech helper behind /stx (container fill modes, encoder chests, item lists). */
public class StorageTech {
    // global settings, not persisted
    public static boolean encoderMaxFilling = true;
    public static boolean encoderFillAllSlots = true;
    public static int encoderItemAmount = 2;
    public static EnumDyeColor shulkerBoxColor = EnumDyeColor.PURPLE;

    public static final String[] MODES = {"full", "full_boxes", "prefill", "prefill_unstackables", "single_item", "single_stack", "single_full_box"};

    // ---- items ----

    /** Dummy items are fixed: barrier named "Blocker" (stackable) and a plain wooden hoe (unstackable). */
    public static ItemStack dummy(boolean unstackable) {
        ItemStack s = new ItemStack(unstackable ? Items.WOODEN_HOE : Item.getItemFromBlock(Blocks.BARRIER));
        if (!unstackable) s.setStackDisplayName("Blocker");
        return s;
    }

    static boolean isDummy(ItemStack s) {
        if (s.getItem() == Items.WOODEN_HOE) return !s.hasDisplayName();
        return s.getItem() == Item.getItemFromBlock(Blocks.BARRIER) && s.hasDisplayName() && "Blocker".equals(s.getDisplayName());
    }

    static ItemStack withCount(ItemStack t, int n) {
        ItemStack s = t.copy();
        s.setCount(n);
        return s;
    }

    /** Parses item[:meta] (also minecraft:stone:1). Returns a stack of count 1. */
    public static ItemStack parseItem(String text) throws CommandException {
        Item item = Item.REGISTRY.getObject(new ResourceLocation(text));
        int meta = 0;
        int c = text.lastIndexOf(':');
        if (item == null && c > 0) {
            try {
                meta = Integer.parseInt(text.substring(c + 1));
                item = Item.REGISTRY.getObject(new ResourceLocation(text.substring(0, c)));
            } catch (NumberFormatException ignored) {
            }
        }
        if (item == null) throw new CommandException("Unknown item: " + text);
        return new ItemStack(item, 1, meta);
    }

    // ---- inventories ----

    /** Inventory at pos (double chests as one 54 slot inventory, once), or null. */
    public static IInventory inventoryAt(World w, BlockPos p, Set<BlockPos> done) {
        if (done.contains(p)) return null;
        TileEntity te = w.getTileEntity(p);
        if (te instanceof TileEntityChest && w.getBlockState(p).getBlock() instanceof BlockChest) {
            BlockChest b = (BlockChest) w.getBlockState(p).getBlock();
            IInventory c = b.getContainer(w, p, true);
            if (c instanceof InventoryLargeChest) {
                for (EnumFacing f : EnumFacing.HORIZONTALS) {
                    BlockPos q = p.offset(f);
                    if (w.getBlockState(q).getBlock() == b
                            && w.getBlockState(q).getValue(BlockChest.FACING) == w.getBlockState(p).getValue(BlockChest.FACING))
                        done.add(q);
                }
            }
            return c;
        }
        return te instanceof IInventory ? (IInventory) te : null;
    }

    static ItemStack fullBox(ItemStack item) {
        Block b = BlockShulkerBox.getBlockByColor(shulkerBoxColor);
        ItemStack box = new ItemStack(Item.getItemFromBlock(b));
        NBTTagList list = new NBTTagList();
        ItemStack full = withCount(item, item.getMaxStackSize());
        for (int i = 0; i < 27; i++) {
            NBTTagCompound t = full.writeToNBT(new NBTTagCompound());
            t.setByte("Slot", (byte) i);
            list.appendTag(t);
        }
        NBTTagCompound be = new NBTTagCompound();
        be.setTag("Items", list);
        NBTTagCompound tag = new NBTTagCompound();
        tag.setTag("BlockEntityTag", be);
        box.setTagCompound(tag);
        return box;
    }

    // ---- /stx container ----

    /** Fills every inventory in the cuboid according to mode. Returns the number of containers affected. */
    public static int container(World w, BlockPos a, BlockPos b, String mode, List<ItemStack> items) {
        Set<BlockPos> done = new HashSet<>();
        int n = 0;
        for (BlockPos p : BlockPos.getAllInBox(a, b)) {
            if (!w.isBlockLoaded(p)) continue;
            IInventory inv = inventoryAt(w, p, done);
            if (inv == null) continue;
            fill(inv, mode, items.get(n % items.size())); // several items: one per container, cycling
            n++;
        }
        return n;
    }

    static void fill(IInventory inv, String mode, ItemStack item) {
        int size = inv.getSizeInventory();
        int lim = item.getMaxStackSize();
        for (int i = 0; i < size; i++) {
            ItemStack s;
            switch (mode) {
                case "full": s = withCount(item, lim); break;
                case "full_boxes": s = fullBox(item); break;
                case "prefill": s = i == 0 ? withCount(item, lim) : dummy(false); break;
                case "prefill_unstackables": s = i == 0 ? withCount(item, lim) : dummy(true); break;
                case "single_item": s = i == 0 ? withCount(item, 1) : ItemStack.EMPTY; break;
                case "single_stack": s = i == 0 ? withCount(item, lim) : ItemStack.EMPTY; break;
                case "single_full_box": s = i == 0 ? fullBox(item) : ItemStack.EMPTY; break;
                default: throw new IllegalArgumentException(mode);
            }
            inv.setInventorySlotContents(i, s);
        }
        inv.markDirty();
    }

    // ---- /stx encoder ----

    /** Result of the slot assignment: the stacks per slot and the items that did not fit. */
    static class Plan {
        ItemStack[] slots;
        List<ItemStack> skipped = new ArrayList<>();
    }

    /**
     * Step 1: total "units" (1/64 of a 64-stack) the inventory must hold.
     * Vanilla signal = floor(14 * units / (64 * size)) + 1 for a non empty inventory, so the
     * lowest amount giving exactly `signal` is ceil((signal - 1) * 64 * size / 14).
     * (stx uses ceil(size * 64 / 14 * signal), which is one step higher in 1.12.2.)
     */
    static int targetUnits(int size, int signal) {
        return ((signal - 1) * 64 * size + 13) / 14;
    }

    /** Step 2: assign stacks to slots (port of stx _encoderChest, without touching the inventory). */
    static Plan assign(int size, List<ItemStack> items, int target) {
        Plan plan = new Plan();
        plan.slots = new ItemStack[size];
        int amount = target;
        int i = 0;
        for (int slot = 0; slot < size; slot++) {
            ItemStack s;
            if ((size - slot - 1) * 64 < amount) {
                // tail: fill with whatever is left of the amount, up to a full stack
                int units = Math.min(amount, 64);
                ItemStack t = encoderMaxFilling && i < items.size() ? items.get(i) : null;
                int w = t == null ? 1 : 64 / t.getMaxStackSize();
                if (t != null && units % w == 0) {
                    s = withCount(t, units / w);
                    i++;
                } else {
                    s = withCount(dummy(false), units); // units not divisible by the item's weight (16-stacks)
                }
                amount -= units;
            } else {
                ItemStack t;
                if (i < items.size()) t = items.get(i++);
                else if (encoderFillAllSlots) t = dummy(false);
                else {
                    plan.slots[slot] = ItemStack.EMPTY;
                    continue;
                }
                int lim = t.getMaxStackSize();
                int n = Math.min(encoderItemAmount, lim);
                s = withCount(t, n);
                amount -= n * 64 / lim;
            }
            plan.slots[slot] = s;
        }
        for (; i < items.size(); i++) plan.skipped.add(items.get(i));
        return plan;
    }

    static int signalOf(Plan plan) {
        InventoryBasic scratch = new InventoryBasic("scratch", false, plan.slots.length);
        for (int i = 0; i < plan.slots.length; i++) scratch.setInventorySlotContents(i, plan.slots[i].copy());
        return Container.calcRedstoneFromInventory(scratch);
    }

    /**
     * Encodes inv to the given comparator signal using items (stackable templates).
     * The signal of the plan is checked with vanilla calcRedstoneFromInventory and the amount is nudged up if needed.
     */
    static Plan encode(IInventory inv, List<ItemStack> items, int signal) {
        int size = inv.getSizeInventory();
        int target = targetUnits(size, signal);
        Plan best = assign(size, items, target);
        Plan plan = best;
        for (int d = 0; d <= size * 5 && signalOf(plan) != signal; d++) plan = assign(size, items, target + d + 1);
        if (signalOf(plan) == signal) best = plan;
        // step 3: write
        for (int i = 0; i < size; i++) inv.setInventorySlotContents(i, best.slots[i]);
        inv.markDirty();
        return best;
    }

    public static class EncodeResult {
        public int affected;
        public List<String> warnings = new ArrayList<>();
    }

    /** One list applies to every chest; several lists: the i-th chest gets the i-th list, extra chests are skipped. */
    public static EncodeResult encodeFill(World w, BlockPos a, BlockPos b, int signal, List<List<ItemStack>> lists) {
        EncodeResult r = new EncodeResult();
        Set<BlockPos> done = new HashSet<>();
        List<List<ItemStack>> usable = new ArrayList<>();
        for (List<ItemStack> l : lists) {
            List<ItemStack> u = new ArrayList<>();
            for (ItemStack s : l) {
                if (s.getMaxStackSize() == 1) r.warnings.add("Skipping unstackable item " + s.getDisplayName());
                else addUnique(u, s);
            }
            usable.add(u);
        }
        int idx = 0;
        for (BlockPos p : BlockPos.getAllInBox(a, b)) {
            if (!w.isBlockLoaded(p)) continue;
            IInventory inv = inventoryAt(w, p, done);
            if (inv == null) continue;
            if (usable.size() > 1 && idx >= usable.size()) continue;
            report(r, p, encode(inv, usable.get(usable.size() > 1 ? idx : 0), signal), inv, signal);
            idx++;
        }
        return r;
    }

    /** Rebuilds encoder chests from the non-dummy stackable items already inside them. */
    public static EncodeResult encodeFix(World w, BlockPos a, BlockPos b, int signal) {
        EncodeResult r = new EncodeResult();
        Set<BlockPos> done = new HashSet<>();
        for (BlockPos p : BlockPos.getAllInBox(a, b)) {
            if (!w.isBlockLoaded(p)) continue;
            IInventory inv = inventoryAt(w, p, done);
            if (inv == null) continue;
            List<ItemStack> items = new ArrayList<>();
            for (int i = 0; i < inv.getSizeInventory(); i++) {
                ItemStack s = inv.getStackInSlot(i);
                if (s.isEmpty() || s.getMaxStackSize() == 1 || isDummy(s)) continue;
                addUnique(items, s);
            }
            if (items.isEmpty()) continue;
            report(r, p, encode(inv, items, signal), inv, signal);
        }
        return r;
    }

    private static void report(EncodeResult r, BlockPos p, Plan plan, IInventory inv, int signal) {
        r.affected++;
        String pos = p.getX() + " " + p.getY() + " " + p.getZ();
        if (!plan.skipped.isEmpty()) {
            List<String> names = new ArrayList<>();
            for (ItemStack s : plan.skipped) names.add(s.getDisplayName());
            r.warnings.add("Not enough space in the encoder chest at " + pos + " for " + names.size() + " items: " + String.join(", ", names));
        }
        int got = Container.calcRedstoneFromInventory(inv);
        if (got != signal) r.warnings.add("Encoder chest at " + pos + " outputs signal " + got + " instead of " + signal + " (too many items for that signal)");
    }

    // ---- item lists: <world>/stx/item_lists/<name>.txt, one SNBT stack (count 1) per line ----

    public static void addUnique(List<ItemStack> list, ItemStack s) {
        for (ItemStack o : list) if (ItemStack.areItemsEqual(o, s) && ItemStack.areItemStackTagsEqual(o, s)) return;
        list.add(withCount(s, 1));
    }

    public static File listFile(File world, String name) throws CommandException {
        if (!name.matches("[a-z0-9_-]+")) throw new CommandException("Invalid item list name (use a-z, 0-9, _ and -): " + name);
        return new File(new File(world, "stx/item_lists"), name + ".txt");
    }

    public static boolean listExists(File world, String name) throws CommandException {
        return listFile(world, name).isFile();
    }

    public static List<ItemStack> readList(File world, String name) throws CommandException {
        File f = listFile(world, name);
        if (!f.isFile()) throw new CommandException("The item list " + name + " doesn't exist");
        List<ItemStack> r = new ArrayList<>();
        try {
            for (String line : Files.readAllLines(f.toPath(), StandardCharsets.UTF_8)) {
                if (line.trim().isEmpty()) continue;
                ItemStack s = new ItemStack(JsonToNBT.getTagFromJson(line));
                if (!s.isEmpty()) r.add(s);
            }
        } catch (IOException | NBTException e) {
            throw new CommandException("Could not read item list " + name + ": " + e.getMessage());
        }
        return r;
    }

    public static void writeList(File world, String name, List<ItemStack> items) throws CommandException {
        File f = listFile(world, name);
        List<String> lines = new ArrayList<>();
        for (ItemStack s : items) lines.add(withCount(s, 1).writeToNBT(new NBTTagCompound()).toString());
        try {
            Files.createDirectories(f.getParentFile().toPath());
            Files.write(f.toPath(), lines, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new CommandException("Could not write item list " + name + ": " + e.getMessage());
        }
    }

    public static List<String> listNames(File world) {
        List<String> r = new ArrayList<>();
        File[] fs = new File(world, "stx/item_lists").listFiles();
        if (fs != null) for (File f : fs) if (f.getName().endsWith(".txt")) r.add(f.getName().substring(0, f.getName().length() - 4));
        Collections.sort(r);
        return r;
    }

    /** Distinct items found inside the inventories of the cuboid (dummies ignored). */
    public static List<ItemStack> fromContainers(World w, BlockPos a, BlockPos b) {
        List<ItemStack> r = new ArrayList<>();
        Set<BlockPos> done = new HashSet<>();
        for (BlockPos p : BlockPos.getAllInBox(a, b)) {
            if (!w.isBlockLoaded(p)) continue;
            IInventory inv = inventoryAt(w, p, done);
            if (inv == null) continue;
            for (int i = 0; i < inv.getSizeInventory(); i++) {
                ItemStack s = inv.getStackInSlot(i);
                if (!s.isEmpty() && !isDummy(s)) addUnique(r, s);
            }
        }
        return r;
    }

    /** All registry items (and their variants) matching the same filters as /getallitems. */
    public static List<ItemStack> allItems(String obtainability, String stackability) {
        List<Item> items = new ArrayList<>();
        for (Item i : Item.REGISTRY) if (GetAllItemsHelper.filterItem(i, obtainability, stackability)) items.add(i);
        items.sort(Comparator.comparing(Item::getTranslationKey));
        List<ItemStack> r = new ArrayList<>();
        for (Item i : items) for (ItemStack s : GetAllItemsHelper.getItemVariants(i)) addUnique(r, s);
        return r;
    }
}
