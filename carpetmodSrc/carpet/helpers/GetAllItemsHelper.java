package carpet.helpers;

import carpet.utils.Messenger;
import net.minecraft.command.ICommandSender;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraft.util.NonNullList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.*;
import java.util.stream.Collectors;

public class GetAllItemsHelper {

    private static final Set<String> SURVIVAL_UNOBTAINABLE = new HashSet<>(Arrays.asList(
            "minecraft:grass_path",
            "minecraft:farmland",
            "minecraft:bedrock",
            "minecraft:mob_spawner",
            "minecraft:command_block",
            "minecraft:command_block_minecart",
            "minecraft:barrier",
            "minecraft:structure_block",
            "minecraft:structure_void",
            "minecraft:end_portal_frame",
            "minecraft:repeating_command_block",
            "minecraft:chain_command_block",
            "minecraft:knowledge_book",
            "minecraft:spawn_egg",
            "minecraft:monster_egg"
    ));

    private static final Set<String> SHULKERS = new HashSet<>(Arrays.asList(
            "minecraft:white_shulker_box",
            "minecraft:orange_shulker_box",
            "minecraft:magenta_shulker_box",
            "minecraft:light_blue_shulker_box",
            "minecraft:yellow_shulker_box",
            "minecraft:lime_shulker_box",
            "minecraft:pink_shulker_box",
            "minecraft:gray_shulker_box",
            "minecraft:silver_shulker_box",
            "minecraft:cyan_shulker_box",
            "minecraft:purple_shulker_box",
            "minecraft:blue_shulker_box",
            "minecraft:brown_shulker_box",
            "minecraft:green_shulker_box",
            "minecraft:red_shulker_box",
            "minecraft:black_shulker_box"
    ));

    private static final Set<String> JUNK_ITEMS = new HashSet<>(Arrays.asList(
            "minecraft:filled_map",
            "minecraft:written_book",
            "minecraft:tipped_arrow",
            "minecraft:firework_star",
            "minecraft:firework_rocket"
    ));

    public static List<String> getObtainabilityOptions() {
        return Arrays.asList(
                "use",
                "everything",
                "main_storage",
                "survival_obtainables",
                "unobtainables");
    }

    public static List<String> getStackabilityOptions() {
        return Arrays.asList(
                "stackables",
                "any",
                "64_stackables",
                "16_stackables",
                "unstackables");
    }

    public static int generateItemDump(World world, EntityPlayerMP player, String obtainability, String stackability) {
        List<Item> filteredItems = new ArrayList<>();
        for (Object obj : Item.REGISTRY) {
            Item item = (Item) obj;
            if (filterItem(item, obtainability, stackability)) {
                filteredItems.add(item);
            }
        }

        filteredItems.sort(Comparator.comparing(Item::getTranslationKey));

        List<ItemStack> stacks = filteredItems.stream()
                .flatMap(item -> getItemVariants(item).stream())
                .collect(Collectors.toList());

        List<ItemStack> shulkers = new ArrayList<>();
        for (int i = 0; i < stacks.size(); i += 27) {
            List<ItemStack> page = stacks.subList(i, Math.min(i + 27, stacks.size()));
            NBTTagList itemsTag = new NBTTagList();
            for (int j = 0; j < page.size(); j++) {
                ItemStack stack = page.get(j);
                int maxStackSize = stack.getItem().getItemStackLimit();
                stack.setCount(maxStackSize);

                NBTTagCompound tag = new NBTTagCompound();
                tag.setByte("Slot", (byte) j);
                tag.setString("id", Item.REGISTRY.getNameForObject(stack.getItem()).toString());
                tag.setByte("Count", (byte) maxStackSize);
                tag.setShort("Damage", (short) stack.getMetadata());

                itemsTag.appendTag(tag);
            }

            NBTTagCompound shulkerTag = new NBTTagCompound();
            NBTTagCompound beTag = new NBTTagCompound();
            beTag.setTag("Items", itemsTag);
            shulkerTag.setTag("BlockEntityTag", beTag);
            ItemStack shulker = new ItemStack(Item.getItemFromBlock(Blocks.WHITE_SHULKER_BOX));
            shulker.setTagCompound(shulkerTag);
            shulkers.add(shulker);
        }

        for (int i = 0; i < shulkers.size(); i += 27) {
            BlockPos pos = player.getPosition().add(0, 0, i / 27);
            world.setBlockState(pos, Blocks.CHEST.getDefaultState());
            TileEntityChest chest = (TileEntityChest) world.getTileEntity(pos);
            List<ItemStack> contents = shulkers.subList(i, Math.min(i + 27, shulkers.size()));
            for (int j = 0; j < contents.size(); j++) {
                chest.setInventorySlotContents(j, contents.get(j));
            }
        }

        return stacks.size();
    }

    static List<ItemStack> getItemVariants(Item item) {
        NonNullList<ItemStack> variants = NonNullList.create();
        try {
            item.getSubItems(CreativeTabs.SEARCH, variants);
        } catch (Exception ignored) {}

        Set<String> seen = new HashSet<>();
        List<ItemStack> unique = new ArrayList<>();

        for (ItemStack stack : variants) {
            try {
                String name = stack.getDisplayName();
                if (name != null && !name.toLowerCase().contains("missing") && seen.add(name)) {
                    unique.add(stack);
                }
            } catch (Exception ignored) {}
        }

        if (unique.isEmpty()) unique.add(new ItemStack(item));
        return unique;
    }

    static boolean filterItem(Item item, String obtainability, String stackability) {
        String id = Item.REGISTRY.getNameForObject(item).toString();
        int maxStack = item.getItemStackLimit();

        switch (obtainability) {
            case "everything":
                if (SHULKERS.contains(id))
                    return false;
                break;
            case "main_storage":
                if (SURVIVAL_UNOBTAINABLE.contains(id) || JUNK_ITEMS.contains(id) || SHULKERS.contains(id))
                    return false;
                break;
            case "survival_obtainables":
                if (SURVIVAL_UNOBTAINABLE.contains(id))
                    return false;
                break;
            case "unobtainables":
                return SURVIVAL_UNOBTAINABLE.contains(id) && !id.startsWith("minecraft:spawn_egg");
        }

        switch (stackability) {
            case "stackables": return maxStack == 16 || maxStack == 64;
            case "any": return maxStack == 16 || maxStack == 64 || maxStack == 1;
            case "64_stackables": return maxStack == 64;
            case "16_stackables": return maxStack == 16;
            case "unstackables": return maxStack == 1;
        }
        return true;
    }

    public static void selectObtainability(ICommandSender sender) {
        Messenger.m(sender, "wb Select an obtainability mode:");

        Messenger.m(sender, "l  [Everything]", "^g All items (obtainable and unobtainable)", "!/getallitems use everything");
        Messenger.m(sender, "l  [Main Storage]", "^g Items relevant for storage systems", "!/getallitems use main_storage");
        Messenger.m(sender, "l  [Survival]", "^g Only items obtainable in survival", "!/getallitems use survival_obtainables");
        Messenger.m(sender, "l  [Unobtainables]", "^g Items unobtainable in survival", "!/getallitems use unobtainables");
    }

    public static void selectStackability(ICommandSender sender, String obtainability) {
        Messenger.m(sender, "wb Select a stackability mode:");

        if (!GetAllItemsHelper.getObtainabilityOptions().contains(obtainability)) {
            Messenger.m(sender, "r Unknown obtainability mode: ", "w " + obtainability);
            return;
        }

        Messenger.m(sender, "wb Mode: ", "gb " + obtainability);

        printStackabilityOption(sender, obtainability, "All Stackables", "stackables");
        printStackabilityOption(sender, obtainability, "64 Stackables", "64_stackables");
        printStackabilityOption(sender, obtainability, "16 Stackables", "16_stackables");
        printStackabilityOption(sender, obtainability, "Unstackables", "unstackables");
    }

    private static void printStackabilityOption(ICommandSender sender, String obtainability, String label, String commandArg) {
        Messenger.m(sender,
                "l  [" + label + "]",
                "^g " + capitalize(obtainability.replace('_', ' ')) + " - " + label,
                "!/getallitems " + obtainability + " " + commandArg
        );
    }

    private static String capitalize(String text) {
        return Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }
}

