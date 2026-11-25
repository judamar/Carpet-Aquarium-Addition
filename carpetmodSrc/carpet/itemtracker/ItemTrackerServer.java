package carpet.itemtracker;

import carpet.CarpetSettings;
import carpet.network.PluginChannelHandler;
import io.netty.buffer.Unpooled;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.item.EntityItemFrame;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemShulkerBox;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.network.PacketBuffer;
import net.minecraft.network.play.client.CPacketCustomPayload;
import net.minecraft.network.play.server.SPacketCustomPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

/**
 * Server side of the ItemTracker client mod. C->S: the ItemStack to track (empty = off).
 * S->C every tick: dimension, count, then (x, y, z, kind, entityId or -1) per match; kind 0=dropped, 1=frame, 2=entity/player/minecart, 3=block container.
 * Only ops may track: it reveals item locations the player has no other way to see.
 */
public class ItemTrackerServer implements PluginChannelHandler {
    public static final String CHANNEL = "itemtracker:main";
    private static final int MAX_TARGETS = 200; // keeps the packet far below the 32 KiB custom payload limit

    private final MinecraftServer server;
    private final Map<EntityPlayerMP, ItemStack> tracked = new HashMap<>();

    public ItemTrackerServer(MinecraftServer server) { this.server = server; }

    @Override public String[] getChannels() { return new String[]{CHANNEL}; }

    @Override
    public void onCustomPayload(CPacketCustomPayload packet, EntityPlayerMP player) {
        if (!CarpetSettings.modItemTracker || !server.getPlayerList().canSendCommands(player.getGameProfile())) {
            PacketBuffer no = new PacketBuffer(Unpooled.buffer()); // n = -1: tell the client why nothing will come
            no.writeInt(0); no.writeInt(-1);
            player.connection.sendPacket(new SPacketCustomPayload(CHANNEL, no));
            return;
        }
        try {
            ItemStack s = packet.getBufferData().readItemStack();
            if (s.isEmpty()) tracked.remove(player); else tracked.put(player, s);
        } catch (IOException e) { tracked.remove(player); }
    }

    @Override public void unregister(String channel, EntityPlayerMP player) { tracked.remove(player); }

    /** Call once per server tick. */
    public void tick() {
        if (!CarpetSettings.modItemTracker) { tracked.clear(); return; }
        if (tracked.isEmpty()) return;
        tracked.keySet().removeIf(p -> p.hasDisconnected());
        for (Map.Entry<EntityPlayerMP, ItemStack> en : tracked.entrySet()) send(en.getKey(), en.getValue());
    }

    private static boolean matches(ItemStack want, ItemStack s) { return matches(want, s, 0); }

    private static boolean matches(ItemStack want, ItemStack s, int depth) {
        if (s == null || s.isEmpty()) return false;
        if (s.getItem() == want.getItem() && (s.getItem().isDamageable() // same rule as the client mod: tools match by type only
                || (ItemStack.areItemsEqual(s, want) && ItemStack.areItemStackTagsEqual(s, want)))) return true;
        if (depth < 3 && s.getItem() instanceof ItemShulkerBox && s.hasTagCompound() && s.getTagCompound().hasKey("BlockEntityTag", 10)) { //AqCM: look inside shulker box items
            NBTTagList items = s.getTagCompound().getCompoundTag("BlockEntityTag").getTagList("Items", 10);
            for (int i = 0; i < items.tagCount(); i++) if (matches(want, new ItemStack(items.getCompoundTagAt(i)), depth + 1)) return true;
        }
        return false;
    }

    private static boolean holds(ItemStack want, IInventory inv) {
        for (int i = 0; i < inv.getSizeInventory(); i++) if (matches(want, inv.getStackInSlot(i))) return true;
        return false;
    }

    private void send(EntityPlayerMP me, ItemStack want) {
        World w = me.world;
        List<double[]> out = new ArrayList<>();
        for (Entity e : new ArrayList<>(w.loadedEntityList)) {
            if (e == me || out.size() >= MAX_TARGETS) continue; // own inventory: nothing to point at
            int kind = 2; boolean hit = false;
            if (e instanceof EntityItem) { hit = matches(want, ((EntityItem) e).getItem()); kind = 0; }
            else if (e instanceof EntityItemFrame) { hit = matches(want, ((EntityItemFrame) e).getDisplayedItem()); kind = 1; }
            else if (e instanceof IInventory) hit = holds(want, (IInventory) e);
            else if (e instanceof EntityPlayer) hit = holds(want, ((EntityPlayer) e).inventory);
            else if (e instanceof EntityLivingBase) { for (ItemStack s : ((EntityLivingBase) e).getEquipmentAndArmor()) if (matches(want, s)) hit = true; }
            if (hit) out.add(new double[]{e.posX, e.posY + e.height / 2, e.posZ, kind, e.getEntityId()});
        }
        for (TileEntity te : new ArrayList<>(w.loadedTileEntityList))
            if (out.size() < MAX_TARGETS && te instanceof IInventory && holds(want, (IInventory) te))
                out.add(new double[]{te.getPos().getX() + .5, te.getPos().getY() + .5, te.getPos().getZ() + .5, 3, -1});

        PacketBuffer b = new PacketBuffer(Unpooled.buffer());
        b.writeInt(w.provider.getDimensionType().getId());
        b.writeInt(out.size());
        for (double[] t : out) { b.writeDouble(t[0]); b.writeDouble(t[1]); b.writeDouble(t[2]); b.writeByte((int) t[3]); b.writeInt((int) t[4]); }
        me.connection.sendPacket(new SPacketCustomPayload(CHANNEL, b));
    }
}
