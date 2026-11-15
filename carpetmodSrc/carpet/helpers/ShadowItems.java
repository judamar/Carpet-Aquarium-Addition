package carpet.helpers;

import carpet.CarpetSettings;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.ClickType;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import java.security.SecureRandom;

// AqCM - shadow items: one ItemStack instance referenced from several slots, ported from mattymatty97/carpet-shadow
public class ShadowItems {
    private static final String ALPHABET = "abcdefghijklmnopqrstuvwxyz0123456789";
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Cache<String, ItemStack> registry = CacheBuilder.newBuilder().weakValues().build();
    private static int idLength = 5;

    public static String newId() {
        String id;
        do {
            char[] chars = new char[idLength];
            for (int i = 0; i < chars.length; i++) chars[i] = ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length()));
            id = new String(chars);
        } while (registry.getIfPresent(id) != null);
        return id;
    }

    public static void setIdLength(int length) {
        if (length < 1) throw new IllegalArgumentException("Shadow id length must be at least 1");
        idLength = length;
    }

    public static ItemStack getByIdOrNull(String id) {
        return id == null ? null : registry.getIfPresent(id);
    }

    public static ItemStack getByIdOrAdd(String id, ItemStack stack) {
        ItemStack reference = registry.getIfPresent(id);
        if (reference != null) return reference;
        stack.setShadowId(id);
        registry.put(id, stack);
        return stack;
    }

    public static void clear() {
        registry.invalidateAll();
    }

    public static void cleanUp() {
        registry.cleanUp();
    }

    /**
     * Called when Container.slotClick threw. Registers the stack left in several slots as a shadow item.
     * Returns true if the exception must be swallowed, false if the caller has to rethrow it.
     */
    public static boolean onSlotClickFailure(Container container, int slotId, int dragType, ClickType type, EntityPlayerMP player, Throwable error) {
        if (type != ClickType.SWAP && type != ClickType.PICKUP && type != ClickType.QUICK_CRAFT) return false;
        ItemStack slotStack = slotId >= 0 && slotId < container.inventorySlots.size() ? container.getSlot(slotId).getStack() : ItemStack.EMPTY;
        ItemStack hotbarStack = type == ClickType.SWAP && dragType >= 0 && dragType < 9 ? player.inventory.getStackInSlot(dragType) : ItemStack.EMPTY;
        ItemStack cursorStack = player.inventory.getItemStack();

        ItemStack shadow = null;
        if (shared(slotStack, hotbarStack) || shared(slotStack, cursorStack)) shadow = slotStack;
        else if (shared(hotbarStack, cursorStack)) shadow = hotbarStack;
        if (shadow == null) return false;

        CarpetSettings.LOG.warn("New Shadow Item Created");
        String id = shadow.getShadowId();
        getByIdOrAdd(id == null ? newId() : id, shadow);
        // the id was assigned after the stack was already in slots: record its membership now (shadowItemUpdateFix)
        for (Slot slot : container.inventorySlots) for (int i = 0; i < slot.inventory.getSizeInventory(); i++) if (slot.inventory.getStackInSlot(i) == shadow) shadow.addSlot(slot.inventory, i);
        for (int i = 0; i < player.inventory.getSizeInventory(); i++) if (player.inventory.getStackInSlot(i) == shadow) shadow.addSlot(player.inventory, i);
        return CarpetSettings.shadowItemMode != CarpetSettings.ShadowItemMode.UNLINK;
    }

    /** Called from ItemStack.writeToNBT: writes the shadow tag if this stack is the registered instance. */
    public static void write(ItemStack stack, NBTTagCompound nbt) {
        String id = stack.getShadowId();
        if (id == null || CarpetSettings.shadowItemMode == CarpetSettings.ShadowItemMode.UNLINK) return;
        if (stack.isEmpty()) {
            // ItemStack.EMPTY is never given an id; an emptied shadow stack leaves the registry
            if (registry.getIfPresent(id) == stack) registry.invalidate(id);
            stack.setShadowId(null);
            return;
        }
        if (registry.getIfPresent(id) == stack) nbt.setString("shadow", id);
        else stack.setShadowId(null); // stale id
    }

    /** Same as new ItemStack(nbt), but honours the shadow tag according to shadowItemMode. */
    public static ItemStack load(NBTTagCompound nbt) {
        if (CarpetSettings.shadowItemMode == CarpetSettings.ShadowItemMode.UNLINK || !nbt.hasKey("shadow", 8)) return new ItemStack(nbt);
        String id = nbt.getString("shadow");
        if (CarpetSettings.shadowItemMode == CarpetSettings.ShadowItemMode.VANISH) {
            ItemStack stack = new ItemStack(nbt);
            stack.setCount(0);
            return stack;
        }
        ItemStack existing = getByIdOrNull(id);
        if (existing != null) return existing;
        ItemStack stack = new ItemStack(nbt);
        return stack.isEmpty() ? stack : getByIdOrAdd(id, stack);
    }

    /** Inventories to markDirty after the world tick (shadowItemUpdateFix). */
    public static final java.util.Set<net.minecraft.inventory.IInventory> toUpdate = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());

    public static void updateShadowInventories() {
        if (toUpdate.isEmpty()) return;
        try {
            for (net.minecraft.inventory.IInventory inv : new java.util.ArrayList<>(toUpdate)) {
                try { inv.markDirty(); } catch (Throwable t) { CarpetSettings.LOG.error("Failed to update shadow inventory", t); }
            }
        } finally { toUpdate.clear(); }
    }

    /** TileEntity.readFromNBT that re-tracks shadow stack slots (upstream loaders/*). */
    public static void readTileEntity(net.minecraft.tileentity.TileEntity te, NBTTagCompound nbt) {
        if (!CarpetSettings.shadowItemUpdateFix || !(te instanceof net.minecraft.inventory.IInventory)) { te.readFromNBT(nbt); return; }
        net.minecraft.inventory.IInventory inv = (net.minecraft.inventory.IInventory) te;
        try { for (int i = 0; i < inv.getSizeInventory(); i++) inv.getStackInSlot(i).removeSlot(inv, i); } catch (Exception ignored) {}
        te.readFromNBT(nbt);
        try { for (int i = 0; i < inv.getSizeInventory(); i++) inv.getStackInSlot(i).addSlot(inv, i); } catch (Exception ignored) {}
    }

    /** Hoppers/droppers move shadow stacks by instance instead of by copy. */
    public static boolean transferFix(ItemStack stack) {
        return CarpetSettings.shadowItemTransferFragilityFix && !stack.isEmpty() && stack.getShadowId() != null;
    }

    private static int mergeDepth = 0;
    /** Set by Container.mergeItemStack when a shadow stack was moved whole into another slot. */
    public static ItemStack movedWhole = null;

    public static void mergeStart() { mergeDepth++; }

    public static void mergeEnd() { if (mergeDepth > 0) mergeDepth--; }

    /** The stack is the registered instance of its shadow id. */
    public static boolean isShadow(ItemStack stack) {
        String id = stack.getShadowId();
        return id != null && !stack.isEmpty() && registry.getIfPresent(id) == stack;
    }

    public static boolean sameShadowId(ItemStack a, ItemStack b) {
        return a.getShadowId() != null && a.getShadowId().equals(b.getShadowId());
    }

    /** Vetoes a merge while inside slotClick/combineItems/pickup when both stacks share an id (or both are shadow with PreventCombine). */
    public static boolean mergeCheck(ItemStack a, ItemStack b, boolean original) {
        if (!original || mergeDepth <= 0 || !CarpetSettings.shadowItemInventoryFragilityFix) return original;
        String x = a.getShadowId(), y = b.getShadowId();
        if (x == null || y == null) return true;
        return !(CarpetSettings.shadowItemPreventCombine || x.equals(y));
    }

    /** After a transferStackInSlot: if the stack moved whole, empty the source slot (the instance lives on in the target). */
    public static ItemStack afterTransfer(Slot source, ItemStack result) {
        if (movedWhole != null) {
            if (source.getStack() == movedWhole) source.putStack(ItemStack.EMPTY);
            movedWhole = null;
        }
        return result;
    }

    // ItemStack.EMPTY is one shared instance, so two empty slots are always identical
    private static boolean shared(ItemStack a, ItemStack b) {
        return a == b && !a.isEmpty();
    }
}
