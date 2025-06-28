package carpet.helpers;

import carpet.CarpetSettings;
import net.minecraft.entity.player.EntityPlayer;

public class CreativeOpenContainerForciblyHelper {
    public static final ThreadLocal<Boolean> ignoreChestBlockedCheck = ThreadLocal.withInitial(() -> false);
    public static boolean canOpenForcibly(EntityPlayer player)
    {
        return CarpetSettings.creativeOpenContainerForcibly && player.capabilities.isCreativeMode;
    }
}
