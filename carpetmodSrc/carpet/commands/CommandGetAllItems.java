package carpet.commands;

import carpet.utils.Messenger;
import net.minecraft.command.*;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraft.util.NonNullList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import javax.annotation.Nullable;
import java.util.*;
import java.util.stream.Collectors;

public class CommandGetAllItems extends CommandCarpetBase {

    private static final List<String> OBTAINABILITY = Arrays.asList("everything", "main_storage", "survival_obtainables");
    private static final List<String> STACKABILITY = Arrays.asList("stackables", "64_stackables", "16_stackables", "unstackables");

    private static final Set<String> SURVIVAL_UNOBTAINABLE = new HashSet<>(Arrays.asList(
            "bedrock", "mob_spawner", "spawner", "command_block", "barrier",
            "structure_block", "structure_void", "end_portal_frame", "repeating_command_block",
            "chain_command_block", "jigsaw", "knowledge_book", "piston_head", "spawn_egg"
    ));

    private static final Set<String> JUNK_ITEMS = new HashSet<>(Arrays.asList(
            "filled_map", "written_book", "tipped_arrow", "firework_star", "firework_rocket"
    ));

    @Override
    public String getName() {
        return "getallitems";
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "/getallitems [everything | main_storage | survival_obtainables] [stackables | 64_stackables | 16_stackables | unstackables]";
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        if (!(sender instanceof EntityPlayerMP)) throw new CommandException("Only players can use this command");
        EntityPlayerMP player = (EntityPlayerMP) sender;
        World world = player.getEntityWorld();

        String obtainability = args.length >= 1 ? args[0].toLowerCase() : "main_storage";
        String stackability = args.length >= 2 ? args[1].toLowerCase() : "stackables";

        if (!OBTAINABILITY.contains(obtainability))
            throw new CommandException("Invalid obtainability: " + obtainability);
        if (!STACKABILITY.contains(stackability))
            throw new CommandException("Invalid stackability: " + stackability);

        List<Item> allItems = Item.REGISTRY.iterator()
                .hasNext() ? new ArrayList<>() : Collections.emptyList();
        for (Object itemObj : Item.REGISTRY) {
            allItems.add((Item) itemObj);
        }

        List<Item> filteredItems = allItems.stream()
                .filter(item -> filterItem(item, obtainability, stackability))
                .collect(Collectors.toList());

        Collections.sort(filteredItems, Comparator.comparing(item -> item.getTranslationKey()));

        List<ItemStack> stacks = filteredItems.stream()
                .flatMap(item -> getItemVariants(item).stream())
                .collect(Collectors.toList());

        // group all
        List<ItemStack> shulkers = new ArrayList<>();
        for (int i = 0; i < stacks.size(); i += 27) {
            List<ItemStack> page = stacks.subList(i, Math.min(i + 27, stacks.size()));
            NBTTagList itemsTag = new NBTTagList();
            for (int j = 0; j < page.size(); j++) {
                ItemStack stack = page.get(j);
                NBTTagCompound tag = new NBTTagCompound();
                tag.setByte("Slot", (byte) j);
                tag.setString("id", Item.REGISTRY.getNameForObject(stack.getItem()).toString());
                int maxStackSize = stack.getItem().getItemStackLimit();
                stack.setCount(maxStackSize);
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

        Messenger.m(player, "g Spawned " + stacks.size() + " items inside " + shulkers.size() + " shulker boxes.");
    }

    private List<ItemStack> getItemVariants(Item item) {
        NonNullList<ItemStack> variants = NonNullList.create();

        try {
            item.getSubItems(CreativeTabs.SEARCH, variants);
        } catch (Exception e) {
            // pass
        }

        Set<String> seen = new HashSet<>();
        List<ItemStack> unique = new ArrayList<>();

        for (ItemStack stack : variants) {
            try {
                String name = stack.getDisplayName();
                if (name != null && !name.toLowerCase().contains("missing") && seen.add(name)) {
                    unique.add(stack);
                }
            } catch (Exception e) {
                // pass
            }
        }

        if (unique.isEmpty()) {
            unique.add(new ItemStack(item));
        }

        return unique;
    }

    private boolean filterItem(Item item, String obtainability, String stackability) {
        String id = Item.REGISTRY.getNameForObject(item).toString();
        int maxStack = item.getItemStackLimit();

        switch (obtainability) {
            case "everything":
                return true;
            case "main_storage":
                if (SURVIVAL_UNOBTAINABLE.contains(id) || JUNK_ITEMS.contains(id) || id.contains("shulker_box"))
                    return false;
                break;
            case "survival_obtainables":
                if (SURVIVAL_UNOBTAINABLE.contains(id))
                    return false;
                break;
        }

        switch (stackability) {
            case "stackables": return maxStack == 16 || maxStack == 64;
            case "64_stackables": return maxStack == 64;
            case "16_stackables": return maxStack == 16;
            case "unstackables": return maxStack == 1;
        }
        return true;
    }

    public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args, @Nullable BlockPos targetPos)
    {
        if (args.length == 1){
            return getListOfStringsMatchingLastWord(args, OBTAINABILITY);
        } else if (args.length == 2) {
            return getListOfStringsMatchingLastWord(args, STACKABILITY);
        }else {
            return Collections.emptyList();
        }
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 2;
    }
}
