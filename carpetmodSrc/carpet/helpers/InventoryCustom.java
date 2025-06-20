package carpet.helpers;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.InventoryBasic;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagList;

import javax.annotation.Nullable;

public class InventoryCustom extends InventoryBasic {

    private InventoryLoadedListener onLoadInventory;
    private InventoryChangedListener onChangeInventory;
    private InventoryClosedListener onCloseInventory;
    public EntityPlayerMP entityPlayerOwner;
    public EntityPlayerMP entityPlayer;
    private boolean loaded;

    public InventoryCustom(String title, boolean customName, int slotCount, InventoryLoadedListener onLoadInventory, InventoryChangedListener onChangeInventory, @Nullable InventoryClosedListener onCloseInventory, EntityPlayerMP entityPlayerOwner, EntityPlayerMP entityPlayer) {
        super(title, customName, slotCount);

        this.onLoadInventory = onLoadInventory;
        this.onChangeInventory = onChangeInventory;
        this.onCloseInventory = onCloseInventory;
        this.entityPlayerOwner = entityPlayerOwner;
        this.entityPlayer = entityPlayer;
    }

    public void loadInventory(NBTTagList nbttaglist) {
        this.onLoadInventory.load(this, nbttaglist);
        this.loaded = true;
    }

    public void closeInventory(EntityPlayer player) {
        super.closeInventory(player);
        if(this.onCloseInventory != null) this.onCloseInventory.close(this);
    }

    public void setInventorySlotContents(int index, ItemStack stack) {
        super.setInventorySlotContents(index, stack);
        if(this.loaded) this.onChangeInventory.change(this, index, stack);
    }

    public interface InventoryLoadedListener {
        void load(IInventory inventory, NBTTagList nbttaglist);
    }

    public interface InventoryChangedListener {
        void change(IInventory inventory, int index, ItemStack itemStack);
    }

    public interface InventoryClosedListener {
        void close(IInventory inventory);
    }

}
