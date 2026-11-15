package carpet.helpers;

import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.ai.EntityAITasks;
import java.util.Set;

public class EntityBrainMemoryUnfreedFixHelper
{
    public static void clearAIReferences(EntityLivingBase entity)
    {
        if (entity instanceof EntityLiving)
        {
            EntityLiving living = (EntityLiving) entity;
            clearTaskList(living.tasks);
            clearTaskList(living.targetTasks);
        }
    }

    private static void clearTaskList(EntityAITasks taskList)
    {
        if (taskList == null) return;

        Set<EntityAITasks.EntityAITaskEntry> entries = taskList.taskEntries;

        for (EntityAITasks.EntityAITaskEntry entry : entries)
        {
            if (entry.action != null)
            {
                entry.action.resetTask();
            }
        }
    }
}