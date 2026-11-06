package com.ancientkira.mca.compat;
import com.ancientkira.mca.MCA;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.golem.IronGolem;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.WeakHashMap;
/** Recognizes hostile golem types without treating every IronGolem subclass as a village ally. */
public final class HostileGolemCompat {
    private record Cached(int until,boolean zombie) {}
    private static final Map<Entity,Cached> VARIANTS=new WeakHashMap<>();
    private static Method variantGetter;
    private static boolean unavailable;
    private HostileGolemCompat() {}
    public static boolean hostileType(String id) {
        return id.equals("sickmob:sickened_iron_golem") || id.equals("minecraft:zombie_golem")
                || id.equals("minecraft:zombie_goelm") || id.equals("multigolem:zombie_golem");
    }
    public static boolean hostile(Entity entity) {
        if(!SickenedCompat.enabled() || !(entity instanceof IronGolem))return false;
        if(hostileType(BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString()))return true;
        if(SickenedCompat.enabled() && entity.entityTags().contains(SickenedCompat.TAG))return true;
        if(!MCA.platformHelper.isModLoaded("multigolem") || unavailable)return false;
        Cached cached=VARIANTS.get(entity);if(cached!=null && entity.tickCount<cached.until())return cached.zombie();
        boolean zombie=false;
        try {
            if(variantGetter==null)variantGetter=Class.forName("dev.charles.multigolem.attachment.GolemVariantAttachment").getMethod("get",Entity.class);
            Object variant=variantGetter.invoke(null,entity);zombie=variant instanceof Enum<?> value && value.name().equals("ZOMBIE");
        } catch(ReflectiveOperationException | LinkageError error) { unavailable=true; }
        VARIANTS.put(entity,new Cached(entity.tickCount+20,zombie));return zombie;
    }
}
