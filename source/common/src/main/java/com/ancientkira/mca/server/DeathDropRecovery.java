package com.ancientkira.mca.server;
import java.util.*;
import com.ancientkira.mca.entity.VillagerEntityMCA;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
/** Ownership belongs to the dropped entity, so player pickups never carry a reclaim marker. */
public final class DeathDropRecovery {
    private static final String PREFIX="mca_death_drop:";
    private DeathDropRecovery() {}
    public static Optional<UUID> owner(ItemEntity item) {
        for(String tag:item.entityTags()) if(tag.startsWith(PREFIX)) {
            try{return Optional.of(UUID.fromString(tag.substring(PREFIX.length())));}catch(IllegalArgumentException ignored){}
        }
        return Optional.empty();
    }
    public static void mark(ItemEntity item,VillagerEntityMCA owner){item.addTag(PREFIX+owner.getUUID());}
    public static boolean mayMerge(ItemEntity first,ItemEntity second){return owner(first).equals(owner(second));}
    public static void recover(ItemEntity item,VillagerEntityMCA person) {
        if(item.isRemoved() || !person.isAlive() || !owner(item).filter(person.getUUID()::equals).isPresent())return;
        var remaining=person.getInventory().addItem(item.getItem().copy());
        if(remaining.isEmpty())item.discard();else item.setItem(remaining);
    }
    public static void tick(ItemEntity item) {
        if(!(item.level() instanceof ServerLevel world) || Math.floorMod(item.tickCount+item.getId(),40)!=0)return;
        var id=owner(item);if(id.isEmpty())return;
        for(ServerLevel level:world.getServer().getAllLevels()) if(level.getEntity(id.get()) instanceof VillagerEntityMCA person && person.isAlive()) {
            recover(item,person);return;
        }
    }
}
