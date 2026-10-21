package com.ancientkira.mca.client;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import java.util.*;
public final class DiscCordPlaybackMonitor {
    private static final Map<BlockPos,Integer> silence=new HashMap<>();
    private static final Set<BlockPos> heard=new HashSet<>();
    private static java.lang.reflect.Field playing;
    private static boolean checked;
    public static void tick(Minecraft client){
        if (client.level==null || client.player==null){silence.clear();heard.clear();return;}
        if (!checked){checked=true;try{playing=Class.forName("com.mrbysco.disccord.client.ClientHandler").getField("playingSounds");}catch(ReflectiveOperationException|LinkageError ignored){}}
        if (playing==null)return;
        try{
            Object value=playing.get(null);
            if (!(value instanceof Map<?,?> sounds))return;
            Set<BlockPos> seen=new HashSet<>();
            for(var entry:new ArrayList<>(sounds.entrySet())){
                if (!(entry.getKey() instanceof Vec3 location) || !(entry.getValue() instanceof SoundInstance sound))continue;
                BlockPos pos=BlockPos.containing(location);seen.add(pos);
                if (client.getSoundManager().isActive(sound)){heard.add(pos);silence.remove(pos);}
            }
            for(BlockPos pos:new HashSet<>(heard)){
                Object sound=sounds.entrySet().stream().filter(e->e.getKey() instanceof Vec3 v && BlockPos.containing(v).equals(pos)).map(Map.Entry::getValue).findFirst().orElse(null);
                if (sound instanceof SoundInstance instance && client.getSoundManager().isActive(instance))continue;
                if (silence.merge(pos,1,Integer::sum)>=60){
                    com.ancientkira.mca.network.Network.sendToServer(new com.ancientkira.mca.network.c2s.DiscCordFinishedMessage(pos));
                    heard.remove(pos);silence.remove(pos);
                }
            }
        }catch(IllegalAccessException ignored){}
    }
}
