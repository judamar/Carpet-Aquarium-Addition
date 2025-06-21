/*
 * This file is part of the Carpet TIS Addition project, licensed under the
 * GNU Lesser General Public License v3.0
 *
 * Copyright (C) 2023  Fallen_Breath and contributors
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

import net.minecraft.entity.item.*;
import net.minecraft.item.Item;
import net.minecraft.init.Items;

import java.util.Optional;

public class MinecartFullDropBackportHelper
{
    public static Optional<Item> getFullDropItem(EntityMinecart cart)
    {
        Item item = null;
        if (cart instanceof EntityMinecartChest)
        {
            item = Items.CHEST_MINECART;
        }
        else if (cart instanceof EntityMinecartFurnace)
        {
            item = Items.FURNACE_MINECART;
        }
        else if (cart instanceof EntityMinecartHopper)
        {
            item = Items.HOPPER_MINECART;
        }
        else if (cart instanceof EntityMinecartTNT)
        {
            item = Items.TNT_MINECART;
        }
        return Optional.ofNullable(item);
    }
}

