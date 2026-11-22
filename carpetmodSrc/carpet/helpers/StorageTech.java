//ported from: https://github.com/CommandLeo/scarpet/blob/main/programs/stx.sc (phase 1)
package carpet.helpers;

import net.minecraft.block.Block;
import net.minecraft.block.BlockChest;
import net.minecraft.block.BlockShulkerBox;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.command.CommandException;
import net.minecraft.entity.item.EntityItemFrame;
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
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Storage tech helper behind /stx (container fill modes, encoder chests, item lists). */
public class StorageTech {
    // global settings, not persisted
    public static boolean encoderMaxFilling = false; //AqCM: off = the signal tail is only dummies (Blocker + axes); on = real items may fill the tail
    public static boolean encoderFillAllSlots = true;
    public static int encoderItemAmount = 1; //AqCM
    public static boolean preferUnstackables = true; //AqCM: full 64-unit dummy slots use the wooden axe
    public static EnumDyeColor shulkerBoxColor = EnumDyeColor.PURPLE;

    public static final String[] MODES = {"full", "full_boxes", "prefill", "prefill_unstackables", "single_item", "single_stack", "single_full_box"};

    // ---- items ----

    /** Dummy items are fixed: barrier named "Blocker" (stackable) and a plain wooden axe (unstackable). */
    public static ItemStack dummy(boolean unstackable) {
        ItemStack s = new ItemStack(unstackable ? Items.WOODEN_AXE : Item.getItemFromBlock(Blocks.BARRIER));
        if (!unstackable) s.setStackDisplayName("Blocker");
        return s;
    }

    static boolean isDummy(ItemStack s) {
        if (s.getItem() == Items.WOODEN_AXE) return !s.hasDisplayName();
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
        if (item == null || item == Items.AIR) throw new CommandException("Unknown item: " + text); //AqCM
        return new ItemStack(item, 1, meta);
    }

    // ---- undo (in memory, last UNDO_DEPTH writing operations per sender) ---- //AqCM

    static final int UNDO_DEPTH = 10;

    static class Snap {
        World w;
        BlockPos p;
        ItemStack[] slots;
    }

    private static final Map<String, Deque<List<Snap>>> undoStacks = new HashMap<>();
    private static List<Snap> recording; // snapshots of the running operation, null when not recording
    private static final Map<IInventory, BlockPos> posOf = new IdentityHashMap<>();
    private static World recWorld;

    public static void beginUndo() {
        recording = new ArrayList<>();
        posOf.clear();
    }

    /** Stores the operation under key if it wrote anything. */
    public static void endUndo(String key) {
        List<Snap> op = recording;
        recording = null;
        posOf.clear();
        if (op == null || op.isEmpty()) return;
        Deque<List<Snap>> d = undoStacks.computeIfAbsent(key, k -> new ArrayDeque<>());
        d.push(op);
        while (d.size() > UNDO_DEPTH) d.removeLast();
    }

    /** Snapshot of inv right before it is written (once per position per operation). */
    static void snap(IInventory inv) {
        BlockPos p = posOf.get(inv);
        if (recording == null || p == null) return;
        for (Snap o : recording) if (o.p.equals(p)) return;
        Snap sn = new Snap();
        sn.w = recWorld;
        sn.p = p;
        sn.slots = new ItemStack[inv.getSizeInventory()];
        for (int i = 0; i < sn.slots.length; i++) sn.slots[i] = inv.getStackInSlot(i).copy();
        recording.add(sn);
    }

    /** Pops the sender's latest operation; returns {restored, skipped} or null when there is nothing to undo. */
    public static int[] undo(String key) {
        Deque<List<Snap>> d = undoStacks.get(key);
        if (d == null || d.isEmpty()) return null;
        int ok = 0, skipped = 0;
        for (Snap sn : d.pop()) {
            IInventory inv = inventoryAt(sn.w, sn.p, new HashSet<>());
            if (inv == null || inv.getSizeInventory() != sn.slots.length) {
                skipped++;
                continue;
            }
            for (int i = 0; i < sn.slots.length; i++) inv.setInventorySlotContents(i, sn.slots[i].copy());
            inv.markDirty();
            ok++;
        }
        return new int[]{ok, skipped};
    }

    // ---- inventories ----

    /** Inventory at pos (double chests as one 54 slot inventory, once), or null. */
    public static IInventory inventoryAt(World w, BlockPos p, Set<BlockPos> done) {
        IInventory r = inventoryAt0(w, p, done);
        if (r != null && recording != null) {
            posOf.put(r, p);
            recWorld = w;
        }
        return r;
    }

    private static IInventory inventoryAt0(World w, BlockPos p, Set<BlockPos> done) {
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
            snap(inv);
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
        boolean full; //AqCM: keep > 0 and not one new item fits (slots == null)
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
        return assign(size, items, target, false);
    }

    /** uniform: head items weigh `count` units (canonical tail: partial slot, then full slots) instead of their real weight. */
    static Plan assign(int size, List<ItemStack> items, int target, boolean uniform) { //AqCM
        Plan plan = new Plan();
        plan.slots = new ItemStack[size];
        int amount = target;
        boolean tailStarted = false;
        int i = 0;
        for (int slot = 0; slot < size; slot++) {
            ItemStack s;
            if ((size - slot - 1) * 64 < amount) {
                // tail: fill with whatever is left of the amount, up to a full stack
                int units = Math.min(amount, 64);
                if (!tailStarted && amount % 64 != 0) units = amount % 64; //AqCM: partial slot first, full slots (axes) at the end
                tailStarted = true;
                ItemStack t = encoderMaxFilling && i < items.size() ? items.get(i) : null;
                int w = t == null ? 1 : 64 / t.getMaxStackSize();
                if (t != null && units % w == 0) {
                    s = withCount(t, units / w);
                    i++;
                } else {
                    s = preferUnstackables && units == 64 ? dummy(true) : withCount(dummy(false), units); // units not divisible by the item's weight (16-stacks)
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
                int n = Math.min(t.getCount() > 1 ? t.getCount() : encoderItemAmount, lim); //AqCM: existing items keep their count
                s = withCount(t, n);
                amount -= uniform ? n : n * 64 / lim; //AqCM
            }
            plan.slots[slot] = s;
        }
        for (; i < items.size(); i++) plan.skipped.add(items.get(i));
        return plan;
    }

    static int weight(ItemStack t) { //AqCM: real units of the stack encode would place
        int lim = t.getMaxStackSize();
        return Math.min(t.getCount() > 1 ? t.getCount() : encoderItemAmount, lim) * 64 / lim;
    }

    static int units(Plan p) { //AqCM
        int u = 0;
        for (ItemStack s : p.slots) u += s.getCount() * 64 / s.getMaxStackSize();
        return u;
    }

    /** Head slots are a weight budget (1 unit per slot): list items go in order while their real weight fits, the first that does not and all after it are skipped. */
    static Plan assignBudget(int size, List<ItemStack> items, int target) { //AqCM
        Plan plan = new Plan();
        plan.slots = new ItemStack[size];
        int head = 0;
        while (head < size && (size - head - 1) * 64 >= target - head) head++;
        int budget = head, i = 0;
        boolean blocked = false;
        for (int slot = 0; slot < head; slot++) {
            ItemStack t = i < items.size() && !blocked ? items.get(i) : null;
            if (t != null && weight(t) <= budget) {
                int lim = t.getMaxStackSize();
                plan.slots[slot] = withCount(t, Math.min(t.getCount() > 1 ? t.getCount() : encoderItemAmount, lim));
                budget -= weight(t);
                i++;
            } else {
                blocked |= t != null;
                if (budget > 0 && encoderFillAllSlots) { plan.slots[slot] = dummy(false); budget--; }
                else plan.slots[slot] = ItemStack.EMPTY;
            }
        }
        for (int amount = target - head, slot = head; slot < size; slot++) {
            int units = Math.min(amount, 64);
            if (slot == head && amount % 64 != 0) units = amount % 64;
            plan.slots[slot] = preferUnstackables && units == 64 ? dummy(true) : withCount(dummy(false), units);
            amount -= units;
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
        return encode(inv, items, signal, 0);
    }

    /** keep = how many leading items already exist in inv; if one of them would not fit, inv is left untouched (slots == null). */
    static Plan encode(IInventory inv, List<ItemStack> items, int signal, int keep) {
        int size = inv.getSizeInventory();
        int target = targetUnits(size, signal);
        Plan best = assign(size, items, target, true); //AqCM: canonical tail layout if it reads the exact signal
        Plan exact = assign(size, items, target);
        boolean light = false;
        for (ItemStack t : items) light |= t.getMaxStackSize() < 64;
        if (light && !encoderMaxFilling) { //AqCM: 16-stackers weigh more than a slot: keep the chest at the minimum units
            Plan bp = assignBudget(size, items, target);
            if (signalOf(bp) == signal && units(bp) == target && bp.skipped.size() <= items.size() - keep && !(keep > 0 && bp.skipped.size() == items.size() - keep && items.size() > keep)) return write(inv, bp);
        }
        if (signalOf(best) != signal || (encoderMaxFilling && best.skipped.size() > exact.skipped.size())) best = exact; //AqCM: dummy tail (max filling off) wins even if the adaptive plan skips fewer items
        Plan plan = best;
        for (int d = 0; d <= size * 5 && signalOf(plan) != signal; d++) plan = assign(size, items, target + d + 1);
        if (signalOf(plan) == signal) best = plan;
        if (best.skipped.size() > items.size() - keep || (keep > 0 && best.skipped.size() == items.size() - keep && items.size() > keep)) {
            best.full = best.skipped.size() == items.size() - keep;
            best.slots = null;
            return best;
        }
        return write(inv, best);
    }

    static Plan write(IInventory inv, Plan best) { //AqCM: step 3
        snap(inv);
        for (int i = 0; i < best.slots.length; i++) inv.setInventorySlotContents(i, best.slots[i]);
        inv.markDirty();
        return best;
    }

    public static class EncodeResult {
        public int affected;
        public List<Integer> perChest = new ArrayList<>(); //AqCM
        public List<Integer> chestsUsed = new ArrayList<>(); //AqCM: chests of each bit group that received new items
        public int kept, dupes, codeBits, fallback; //AqCM: chests that kept their own signal, items already present, code bit groups
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

    /**
     * Binary sorter over every inventory in the cuboid a..b (a double chest counts once). Row order: along the
     * cuboid's longest axis (ties: skip the axis the halves of a double chest lie on, then x, y, z), starting at
     * the end where a is; remaining axes break ties. Consecutive groups of perBit inventories form one bit (first
     * group = bit 0, least significant); the items of group i (those whose 1-based index k has bit i set) fill its chests as a chain (starting at chest startChest, 1-based, default first; earlier chests are never touched; overflow to the next, untouched chests left alone).
     * The last suffix.length() groups are suffix bits (chars 0/1 in row order): 1 = whole list, 0 = none.
     * signal 0 = auto: chests with dummies keep their signal. A chest with dummies keeps its current signal and only
     * gets the new items appended after its real items (already present ones skipped); others are encoded to signal.
     */
    public static EncodeResult encodeBinary(World w, BlockPos a, BlockPos b, int signal, int perBit, List<ItemStack> items, String suffix, int startChest) throws CommandException { //AqCM
        int[] ext = {Math.abs(b.getX() - a.getX()) + 1, Math.abs(b.getY() - a.getY()) + 1, Math.abs(b.getZ() - a.getZ()) + 1};
        int[] sgn = {b.getX() >= a.getX() ? 1 : -1, b.getY() >= a.getY() ? 1 : -1, b.getZ() >= a.getZ() ? 1 : -1};
        Set<BlockPos> done = new HashSet<>();
        List<IInventory> invs = new ArrayList<>();
        List<List<BlockPos>> groups = new ArrayList<>();
        boolean[] halfAxis = new boolean[3];
        for (BlockPos p : BlockPos.getAllInBox(a, b)) {
            if (done.contains(p) || !w.isBlockLoaded(p)) continue;
            Set<BlockPos> extra = new HashSet<>();
            IInventory inv = inventoryAt(w, p, extra);
            if (inv == null) continue;
            List<BlockPos> g = new ArrayList<>();
            g.add(p);
            g.addAll(extra);
            for (BlockPos q : g) {
                done.add(q);
                if (q.getX() != p.getX()) halfAxis[0] = true;
                if (q.getY() != p.getY()) halfAxis[1] = true;
                if (q.getZ() != p.getZ()) halfAxis[2] = true;
            }
            invs.add(inv);
            groups.add(g);
        }
        if (invs.isEmpty()) throw new CommandException("No inventories found in the cuboid");
        int max = Math.max(ext[0], Math.max(ext[1], ext[2]));
        int axis = -1;
        for (int pass = 0; pass < 2 && axis < 0; pass++)
            for (int ax = 0; ax < 3 && axis < 0; ax++)
                if (ext[ax] == max && (pass == 1 || !halfAxis[ax])) axis = ax;
        final int ax0 = axis;
        // sort key: position along the row axis (towards b), then the other axes (x, y, z)
        Comparator<BlockPos> cmp = (p, q) -> {
            int c = Integer.compare(coord(p, ax0) * sgn[ax0], coord(q, ax0) * sgn[ax0]);
            for (int ax = 0; c == 0 && ax < 3; ax++) c = Integer.compare(coord(p, ax) * sgn[ax], coord(q, ax) * sgn[ax]);
            return c;
        };
        List<Integer> order = new ArrayList<>();
        List<BlockPos> rep = new ArrayList<>();
        for (int i = 0; i < invs.size(); i++) {
            BlockPos best = groups.get(i).get(0);
            for (BlockPos q : groups.get(i)) if (cmp.compare(q, best) < 0) best = q;
            order.add(i);
            rep.add(best);
        }
        order.sort((i, j) -> cmp.compare(rep.get(i), rep.get(j)));
        if (invs.size() % perBit != 0) throw new CommandException(invs.size() + " inventories do not divide evenly into groups of " + perBit);
        int bits = invs.size() / perBit;
        if (startChest < 1 || startChest > perBit) throw new CommandException("chest must be between 1 and " + perBit + ", got " + startChest);
        if (suffix.length() >= bits) throw new CommandException("suffix " + suffix + " uses " + suffix.length() + " of " + bits + " bit groups; at least 1 code bit is needed");
        int codeBits = bits - suffix.length();
        int n = items.size();
        if (codeBits < 31 && n > (1L << codeBits) - 1) {
            int need = 1;
            while ((1L << need) - 1 < n) need++;
            throw new CommandException(n + " items need " + need + " code bits, but there are only " + codeBits + " (" + invs.size() + " inventories / " + perBit + " per bit" + (suffix.isEmpty() ? "" : ", minus " + suffix.length() + " suffix") + ")");
        }
        EncodeResult r = new EncodeResult();
        r.codeBits = codeBits;
        int[] start = new int[bits]; //AqCM: chest 1 completely empty = not configured, the chain starts there instead of at `chest <n>`
        for (int bit = 0; bit < bits; bit++) {
            start[bit] = startChest;
            IInventory first = invs.get(order.get(bit * perBit));
            boolean empty = startChest > 1;
            for (int s = 0; empty && s < first.getSizeInventory(); s++) empty = first.getStackInSlot(s).isEmpty();
            if (empty) { start[bit] = 1; r.fallback++; }
        }
        if (signal == 0)
            for (int oi = 0; oi < order.size(); oi++) {
                if (oi % perBit < start[oi / perBit] - 1) continue;
                int i = order.get(oi);
                boolean dummy = false;
                for (int s = 0; s < invs.get(i).getSizeInventory(); s++) dummy |= isDummy(invs.get(i).getStackInSlot(s));
                if (!dummy) throw new CommandException("chest at " + rep.get(i).getX() + " " + rep.get(i).getY() + " " + rep.get(i).getZ() + " has no signal to keep; pass a signal");
            }
        for (ItemStack s : items) if (s.getMaxStackSize() == 1) r.warnings.add("Skipping unstackable item " + s.getDisplayName());
        for (int bit = 0; bit < bits; bit++) {
            List<ItemStack> sub = new ArrayList<>();
            for (int k = 1; k <= n; k++) {
                boolean has = bit < codeBits ? bit < 31 && ((k >> bit) & 1) != 0 : suffix.charAt(bit - codeBits) == '1';
                if (has && items.get(k - 1).getMaxStackSize() != 1) sub.add(items.get(k - 1));
            }
            r.perChest.add(sub.size());
            // items already in any chest of the group count as placed
            List<ItemStack> present = new ArrayList<>();
            for (int c = 0; c < perBit; c++) {
                IInventory inv = invs.get(order.get(bit * perBit + c));
                for (int s = 0; s < inv.getSizeInventory(); s++) {
                    ItemStack st = inv.getStackInSlot(s);
                    if (!st.isEmpty() && !isDummy(st)) present.add(st);
                }
            }
            List<ItemStack> rest = new ArrayList<>();
            for (ItemStack s : sub) {
                boolean dup = false;
                for (ItemStack h : present) dup |= ItemStack.areItemsEqual(h, s) && ItemStack.areItemStackTagsEqual(h, s);
                if (dup) r.dupes++;
                else rest.add(s);
            }
            int used = 0;
            // chain: the first chest takes what fits, the remainder goes to the next chest of the group
            for (int c = start[bit] - 1; c < perBit && !rest.isEmpty(); c++) {
                int i = order.get(bit * perBit + c);
                IInventory inv = invs.get(i);
                List<ItemStack> have = new ArrayList<>();
                boolean pre = false;
                for (int s = 0; s < inv.getSizeInventory(); s++) {
                    ItemStack st = inv.getStackInSlot(s);
                    if (isDummy(st)) pre = true;
                    else if (!st.isEmpty()) have.add(st.copy());
                }
                int sig = pre ? Container.calcRedstoneFromInventory(inv) : signal;
                if (pre && signal > 0 && sig != signal) r.kept++;
                int keep = have.size();
                have.addAll(rest);
                Plan plan = encode(inv, have, sig, keep);
                if (plan.slots == null) {
                    if (!plan.full) report(r, rep.get(i), plan, inv, sig); // existing items do not fit: untouched + warning
                    continue;
                }
                if (plan.skipped.size() < rest.size()) used++;
                rest = new ArrayList<>(plan.skipped);
                plan.skipped.clear();
                report(r, rep.get(i), plan, inv, sig);
            }
            r.chestsUsed.add(used);
            if (!rest.isEmpty()) r.warnings.add("Bit " + bit + ": " + rest.size() + " items did not fit in the " + (perBit - start[bit] + 1) + " chests available from chest " + start[bit] + " of " + perBit);
        }
        return r;
    }

    private static int coord(BlockPos p, int ax) {
        return ax == 0 ? p.getX() : ax == 1 ? p.getY() : p.getZ();
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
        String pos = p.getX() + " " + p.getY() + " " + p.getZ();
        if (plan.slots == null) {
            r.warnings.add("Chest at " + pos + " left untouched: its existing items plus the new ones do not fit");
            return;
        }
        r.affected++;
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

    /** Items inside the inventories of the cuboid in scan order and slot order (no dedupe: list index = item number). */
    public static List<ItemStack> fromContainers(World w, BlockPos a, BlockPos b) {
        List<ItemStack> r = new ArrayList<>();
        Set<BlockPos> done = new HashSet<>();
        for (BlockPos p : BlockPos.getAllInBox(a, b)) {
            if (!w.isBlockLoaded(p)) continue;
            IInventory inv = inventoryAt(w, p, done);
            if (inv == null) continue;
            for (int i = 0; i < inv.getSizeInventory(); i++) {
                ItemStack s = inv.getStackInSlot(i);
                if (!s.isEmpty()) r.add(withCount(s, 1)); //AqCM
            }
        }
        return r;
    }

    /**
     * Items shown in item frames whose hanging block is in the cuboid a..b, ordered along the longest axis from a
     * towards b (ties: x, y, z), then by the other axes. Empty frames are skipped, rotation and custom names ignored.
     */
    public static List<ItemStack> fromItemFrames(World w, BlockPos a, BlockPos b) { //AqCM
        int[] sgn = {b.getX() >= a.getX() ? 1 : -1, b.getY() >= a.getY() ? 1 : -1, b.getZ() >= a.getZ() ? 1 : -1};
        int[] ext = {Math.abs(b.getX() - a.getX()), Math.abs(b.getY() - a.getY()), Math.abs(b.getZ() - a.getZ())};
        int axis = 0;
        for (int i = 1; i < 3; i++) if (ext[i] > ext[axis]) axis = i;
        final int ax0 = axis;
        AxisAlignedBB box = new AxisAlignedBB(a, b).grow(1);
        List<EntityItemFrame> frames = new ArrayList<>();
        for (EntityItemFrame f : w.getEntitiesWithinAABB(EntityItemFrame.class, box)) {
            BlockPos p = f.getHangingPosition();
            if (!f.getDisplayedItem().isEmpty() && p.getX() >= Math.min(a.getX(), b.getX()) && p.getX() <= Math.max(a.getX(), b.getX())
                    && p.getY() >= Math.min(a.getY(), b.getY()) && p.getY() <= Math.max(a.getY(), b.getY())
                    && p.getZ() >= Math.min(a.getZ(), b.getZ()) && p.getZ() <= Math.max(a.getZ(), b.getZ())) frames.add(f);
        }
        frames.sort((f, g) -> {
            BlockPos p = f.getHangingPosition(), q = g.getHangingPosition();
            int c = Integer.compare(coord(p, ax0) * sgn[ax0], coord(q, ax0) * sgn[ax0]);
            for (int ax = 0; c == 0 && ax < 3; ax++) c = Integer.compare(coord(p, ax) * sgn[ax], coord(q, ax) * sgn[ax]);
            return c != 0 ? c : f.getUniqueID().compareTo(g.getUniqueID()); // same block, two frames: stable
        });
        List<ItemStack> r = new ArrayList<>();
        for (EntityItemFrame f : frames) {
            ItemStack s = withCount(f.getDisplayedItem(), 1);
            s.clearCustomName();
            r.add(s);
        }
        return r;
    }

    /**
     * Blocks (pick-block item, deduped by item+meta keeping the first) and item frame items (never deduped) of the cuboid
     * in ONE sequence ordered from a towards b like item_frames; at the same position the block goes before the frames.
     * Air and empty frames are skipped; blocks without an item are added by name to ignored.
     */
    public static List<ItemStack> fromArea(World w, BlockPos a, BlockPos b, List<String> ignored) { //AqCM
        int[] sgn = {b.getX() >= a.getX() ? 1 : -1, b.getY() >= a.getY() ? 1 : -1, b.getZ() >= a.getZ() ? 1 : -1};
        int[] ext = {Math.abs(b.getX() - a.getX()), Math.abs(b.getY() - a.getY()), Math.abs(b.getZ() - a.getZ())};
        int axis = 0;
        for (int i = 1; i < 3; i++) if (ext[i] > ext[axis]) axis = i;
        final int ax0 = axis;
        // entry: pos, kind (0 block, 1 frame), stack, frame uuid
        List<Object[]> es = new ArrayList<>();
        for (BlockPos p : BlockPos.getAllInBox(a, b)) {
            if (!w.isBlockLoaded(p)) continue;
            IBlockState st = w.getBlockState(p);
            if (st.getMaterial() == Material.AIR) continue;
            ItemStack s = st.getBlock().getItem(w, p, st);
            if (s.isEmpty()) {
                String n = String.valueOf(Block.REGISTRY.getNameForObject(st.getBlock()));
                if (!ignored.contains(n)) ignored.add(n);
                continue;
            }
            es.add(new Object[]{p.toImmutable(), 0, withCount(s, 1), null});
        }
        for (EntityItemFrame f : w.getEntitiesWithinAABB(EntityItemFrame.class, new AxisAlignedBB(a, b).grow(1))) {
            BlockPos p = f.getHangingPosition();
            if (f.getDisplayedItem().isEmpty() || p.getX() < Math.min(a.getX(), b.getX()) || p.getX() > Math.max(a.getX(), b.getX())
                    || p.getY() < Math.min(a.getY(), b.getY()) || p.getY() > Math.max(a.getY(), b.getY())
                    || p.getZ() < Math.min(a.getZ(), b.getZ()) || p.getZ() > Math.max(a.getZ(), b.getZ())) continue;
            ItemStack s = withCount(f.getDisplayedItem(), 1);
            s.clearCustomName();
            es.add(new Object[]{p, 1, s, f.getUniqueID()});
        }
        es.sort((e, g) -> {
            BlockPos p = (BlockPos) e[0], q = (BlockPos) g[0];
            int c = Integer.compare(coord(p, ax0) * sgn[ax0], coord(q, ax0) * sgn[ax0]);
            for (int ax = 0; c == 0 && ax < 3; ax++) c = Integer.compare(coord(p, ax) * sgn[ax], coord(q, ax) * sgn[ax]);
            if (c == 0) c = Integer.compare((Integer) e[1], (Integer) g[1]);
            if (c == 0 && e[3] != null) c = ((java.util.UUID) e[3]).compareTo((java.util.UUID) g[3]);
            return c;
        });
        List<ItemStack> r = new ArrayList<>();
        List<ItemStack> blocks = new ArrayList<>();
        for (Object[] e : es) {
            ItemStack s = (ItemStack) e[2];
            if ((Integer) e[1] == 0) {
                boolean dup = false;
                for (ItemStack t : blocks) dup |= ItemStack.areItemsEqual(t, s);
                if (dup) continue;
                blocks.add(s);
            }
            r.add(s);
        }
        return r;
    }

    /** All registry items (and their variants) matching the same filters as /getallitems. */
    public static List<ItemStack> allItems(String obtainability, String stackability) {
        List<Item> items = new ArrayList<>();
        for (Item i : Item.REGISTRY) if (i != Items.AIR && GetAllItemsHelper.filterItem(i, obtainability, stackability)) items.add(i);
        items.sort(Comparator.comparing(Item::getTranslationKey));
        List<ItemStack> r = new ArrayList<>();
        for (Item i : items) for (ItemStack s : GetAllItemsHelper.getItemVariants(i)) addUnique(r, s);
        return r;
    }
}
