/*
 * This file is part of the Carpet TIS Addition project, licensed under the
 * GNU Lesser General Public License v3.0
 *
 * Copyright (C) 2024  Fallen_Breath and contributors
 *
 * Carpet TIS Addition is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Carpet TIS Addition is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Carpet TIS Addition.  If not, see <https://www.gnu.org/licenses/>.
 */

package carpet.helpers;

import net.minecraft.block.Block;
import net.minecraft.block.BlockShulkerBox;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTUtil;
import net.minecraft.util.NonNullList;
import net.minecraft.world.World;

public class ShulkerBoxFullDropBackportHelper
{
    public static void onItemEntityDamagedToDie(EntityItem entityItem)
    {
        ItemStack shulkerStack = entityItem.getItem();
        Item item = shulkerStack.getItem();

        if (!(item instanceof ItemBlock))
        {
            return;
        }

        Block block = ((ItemBlock) item).getBlock();
        if (!(block instanceof BlockShulkerBox))
        {
            return;
        }

        NBTTagCompound compoundTag = shulkerStack.getTagCompound();
        World world = entityItem.world;

        if (compoundTag != null && !world.isRemote)
        {
            NBTTagCompound blockEntityTag = compoundTag.getCompoundTag("BlockEntityTag");
            if (blockEntityTag.hasKey("Items", 9)) // 9 = TAG_List
            {
                NBTTagList itemsTag = blockEntityTag.getTagList("Items", 10); // 10 = TAG_Compound
                for (int i = 0; i < itemsTag.tagCount(); i++)
                {
                    NBTTagCompound itemTag = itemsTag.getCompoundTagAt(i);
                    ItemStack stack = new ItemStack(itemTag);
                    if (!stack.isEmpty())
                    {
                        EntityItem dropped = new EntityItem(
                                world,
                                entityItem.posX, entityItem.posY, entityItem.posZ,
                                stack
                        );
                        world.spawnEntity(dropped);
                    }
                }
            }
        }
    }
}