package carpet.commands;

import carpet.helpers.InventoryCustom;
import carpet.helpers.InventoryCustom.InventoryChangedListener;
import carpet.helpers.InventoryCustom.InventoryClosedListener;
import carpet.helpers.InventoryCustom.InventoryLoadedListener;
import com.mojang.authlib.GameProfile;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.management.PlayerInteractionManager;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

public class CommandInventory extends CommandCarpetBase {

    @Override
    public String getName() {
        return "view";
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "/view (inv|echest) <playername>";
    }

    /**
     * Return the required permiss on level for this command.
     */
    public int getRequiredPermissionLevel() {
        return 2;
    }

    public boolean checkPermission(MinecraftServer server, ICommandSender sender) {
        return sender.canUseCommand(this.getRequiredPermissionLevel(), this.getName());
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        if (command_enabled("commandInventory", sender)) {
            if (checkPermission(server, sender)) {
                EntityPlayerMP entityPlayer = getCommandSenderAsPlayer(sender);

                if (args.length == 2) {

                    if (!entityPlayer.getName().equalsIgnoreCase(args[1])) {

                        if (args[0].equalsIgnoreCase("inv")) {

                            InventoryLoadedListener onLoadInventory = (inventory, nbttaglist) -> {
                                InventoryCustom inventoryCustom = (InventoryCustom) inventory;
                                for (int k = 0; k < nbttaglist.tagCount(); ++k) {
                                    NBTTagCompound nbttagcompound = nbttaglist.getCompoundTagAt(k);
                                    int j = nbttagcompound.getByte("Slot") & 255;
                                    ItemStack itemstack = new ItemStack(nbttagcompound);

                                    if (!itemstack.isEmpty()) {
                                        if (j >= 0 && j < inventoryCustom.entityPlayerOwner.inventory.mainInventory.size()) {
                                            inventoryCustom.setInventorySlotContents((j >= 0 && j <= 8) ? j + 36 : j, itemstack);
                                        }
                                        else if (j >= 100 && j < inventoryCustom.entityPlayerOwner.inventory.armorInventory.size() + 100) {
                                            inventoryCustom.setInventorySlotContents(j - 100, itemstack);
                                        }
                                        else if (j >= 150 && j < inventoryCustom.entityPlayerOwner.inventory.offHandInventory.size() + 150) {
                                            inventoryCustom.setInventorySlotContents(j - 146, itemstack);
                                        }
                                    }
                                }
                            };

                            InventoryChangedListener onChangeInventory = (inventory, index, itemStack) -> {
                                InventoryCustom inventoryCustom = (InventoryCustom) inventory;
                                if (index >= 9 && index <= 44) {
                                    inventoryCustom.entityPlayerOwner.inventory.mainInventory.set((index >= 36 && index <= 44) ? index - 36 : index, itemStack);
                                }
                                else if (index >= 0 && index <= 3) {
                                    inventoryCustom.entityPlayerOwner.inventory.armorInventory.set(index, itemStack);
                                }
                                else if (index == 4) {
                                    inventoryCustom.entityPlayerOwner.inventory.offHandInventory.set(index - 4, itemStack);
                                }
                                else {
                                    inventoryCustom.entityPlayer.closeScreen();
                                }
                            };

                            if (this.isPlayerOnline(server, args[1])) {

                                EntityPlayerMP otherPlayer = getPlayer(server, sender, args[1]);
                                InventoryCustom inventoryCustom = new InventoryCustom(otherPlayer.getName(), true, 45, onLoadInventory, onChangeInventory, null, otherPlayer, entityPlayer);
                                inventoryCustom.loadInventory(otherPlayer.inventory.writeToNBT(new NBTTagList()));
                                entityPlayer.displayGUIChest(inventoryCustom);

                            }
                            else {

                                GameProfile gameProfile = server.getPlayerProfileCache().getGameProfileForUsername(args[1]);
                                NBTTagCompound nbttagcompound = this.getOfflinePlayerData(server, args[1]);

                                if (nbttagcompound != null) {
                                    EntityPlayerMP otherPlayer = new EntityPlayerMP(server, server.getWorld(0), gameProfile, new PlayerInteractionManager(server.getWorld(0)));
                                    otherPlayer.readFromNBT(nbttagcompound);

                                    InventoryClosedListener onCloseInventory = (inventory) -> {
                                        InventoryCustom inventoryCustom = (InventoryCustom) inventory;
                                        server.getWorld(0).getSaveHandler().getPlayerNBTManager().writePlayerData(inventoryCustom.entityPlayerOwner);
                                    };

                                    InventoryCustom inventoryCustom = new InventoryCustom(gameProfile.getName(), true, 45, onLoadInventory, onChangeInventory, onCloseInventory, otherPlayer, entityPlayer);
                                    inventoryCustom.loadInventory(otherPlayer.inventory.writeToNBT(new NBTTagList()));
                                    entityPlayer.displayGUIChest(inventoryCustom);
                                }
                                else {
                                    entityPlayer.sendMessage(new TextComponentString(TextFormatting.RED + "Player doesn't exist"));
                                }

                            }

                        }

                        if (args[0].equalsIgnoreCase("echest")) {

                            InventoryLoadedListener onLoadInventory = (inventory, nbttaglist) -> {
                                InventoryCustom inventoryCustom = (InventoryCustom) inventory;
                                for (int k = 0; k < nbttaglist.tagCount(); ++k) {
                                    NBTTagCompound nbttagcompound = nbttaglist.getCompoundTagAt(k);
                                    int j = nbttagcompound.getByte("Slot") & 255;
                                    ItemStack itemstack = new ItemStack(nbttagcompound);

                                    if (!itemstack.isEmpty()) {
                                        if (j >= 0 && j < inventoryCustom.getSizeInventory()) {
                                            inventoryCustom.setInventorySlotContents(j, itemstack);
                                        }
                                    }
                                }
                            };

                            InventoryChangedListener onChangeInventory = (inventory, index, itemStack) -> {
                                InventoryCustom inventoryCustom = (InventoryCustom) inventory;
                                inventoryCustom.entityPlayerOwner.getInventoryEnderChest().setInventorySlotContents(index, itemStack);
                            };

                            if (this.isPlayerOnline(server, args[1])) {

                                EntityPlayerMP otherPlayer = getPlayer(server, sender, args[1]);
                                InventoryCustom inventoryCustom = new InventoryCustom(otherPlayer.getName(), true, 27, onLoadInventory, onChangeInventory, null, otherPlayer, entityPlayer);
                                inventoryCustom.loadInventory(otherPlayer.getInventoryEnderChest().saveInventoryToNBT());
                                entityPlayer.displayGUIChest(inventoryCustom);

                            }
                            else {

                                GameProfile gameProfile = server.getPlayerProfileCache().getGameProfileForUsername(args[1]);
                                NBTTagCompound nbttagcompound = this.getOfflinePlayerData(server, args[1]);

                                if (nbttagcompound != null) {
                                    EntityPlayerMP otherPlayer = new EntityPlayerMP(server, server.getWorld(0), gameProfile, new PlayerInteractionManager(server.getWorld(0)));
                                    otherPlayer.readFromNBT(nbttagcompound);
                                    System.out.println(otherPlayer.getPosition());

                                    InventoryClosedListener onCloseInventory = (inventory) -> {
                                        InventoryCustom inventoryCustom = (InventoryCustom) inventory;
                                        server.getWorld(0).getSaveHandler().getPlayerNBTManager().writePlayerData(inventoryCustom.entityPlayerOwner);
                                        System.out.println(inventoryCustom.entityPlayerOwner.getName());
                                    };

                                    InventoryCustom inventoryCustom = new InventoryCustom(gameProfile.getName(), true, 27, onLoadInventory, onChangeInventory, onCloseInventory, otherPlayer, entityPlayer);
                                    inventoryCustom.loadInventory(otherPlayer.getInventoryEnderChest().saveInventoryToNBT());
                                    entityPlayer.displayGUIChest(inventoryCustom);
                                }
                                else {
                                    entityPlayer.sendMessage(new TextComponentString(TextFormatting.RED + "Player doesn't exist"));
                                }

                            }

                        }

                    }
                    else {

                        entityPlayer.sendMessage(new TextComponentString(TextFormatting.RED + "You can't see your own chest"));

                    }

                }
                else if (args.length == 1) {

                    entityPlayer.sendMessage(new TextComponentString(TextFormatting.RED + "Playername is missing"));

                }
                else if (args.length > 2 || args.length == 0) {

                    throw new WrongUsageException(getUsage(sender), new Object[0]);
                }
            }
        }
    }

    public NBTTagCompound getOfflinePlayerData(MinecraftServer server, String offlinePlayerName) {
        GameProfile gameProfile = server.getPlayerProfileCache().getGameProfileForUsername(offlinePlayerName);
        return (gameProfile != null) ? server.getPlayerList().readPlayerDataFromFile(new EntityPlayerMP(server, server.getWorld(0), gameProfile, new PlayerInteractionManager(server.getWorld(0)))) : null;
    }

    public boolean isPlayerOnline(MinecraftServer server, String playerName) {
        return Arrays.stream(server.getOnlinePlayerNames()).anyMatch((name) -> name.equalsIgnoreCase(playerName));
    }

    public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args, @Nullable BlockPos targetPos) {
        if (args.length == 1) {

            return getListOfStringsMatchingLastWord(args, "inv", "echest");

        }
        else if (args.length == 2) {
            List<String> userNames = getListOfStringsMatchingLastWord(args, server.getPlayerProfileCache().getUsernames());
            Iterator<String> validUsernameIterator = userNames.iterator();
            Scoreboard scoreboard = server.getWorld(0).getScoreboard();

            while (validUsernameIterator.hasNext()) {
                String currentName = validUsernameIterator.next();
                if (getOfflinePlayerData(server, currentName) == null) {
                    validUsernameIterator.remove();
                }
                if (scoreboard.getPlayersTeam(currentName)!=null && scoreboard.getPlayersTeam(currentName).getName().equals("Bots")) {
                    validUsernameIterator.remove();
                }
            }
            return userNames;
        }

        else {
            return Collections.emptyList();
        }
    }

}
