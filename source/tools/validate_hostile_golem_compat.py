#!/usr/bin/env python3
"""Exercise MCA's production hostile-golem recognition and allegiance methods without launching Minecraft."""
from pathlib import Path
import subprocess,tempfile,os,shutil
ROOT=Path(__file__).resolve().parents[1]/'common/src/main/java/com/ancientkira/mca'
def method(text,signature):
 a=text.index(signature);b=text.index('{',a);depth=1;e=b+1
 while depth:
  depth+=(text[e]=='{')-(text[e]=='}');e+=1
 return text[a:e]
hostile=(ROOT/'compat/HostileGolemCompat.java').read_text();hostile=hostile[hostile.index('    private record Cached'):hostile.rfind('}')]
allies=(ROOT/'server/GolemAllies.java').read_text();enemy=method(allies,'public static boolean enemy(').replace('com.ancientkira.mca.compat.HostileGolemCompat','HostileGolemCompat').replace('net.minecraft.world.Difficulty','Difficulty')
guard=method(allies,'public static boolean enemyOfGuard(').replace('com.ancientkira.mca.compat.HostileGolemCompat','HostileGolemCompat').replace('net.minecraft.world.Difficulty','Difficulty')
hook=method((ROOT/'mixin/compat/MixinZombieGolemConversion.java').read_text(),'private static void mca$damageWithoutInfection(').replace('com.ancientkira.mca.compat.SickenedCompat','SickenedCompat')
code='''import java.util.*;import java.lang.reflect.Method;
class Entity{String type="minecraft:iron_golem";Set<String>tags=new HashSet<>();int tickCount;String getType(){return type;}Set<String>entityTags(){return tags;}}
class LivingEntity extends Entity{ServerLevel world=Checks.WORLD;boolean alive=true,knocked,prisoner;boolean isAlive(){return alive;}ServerLevel level(){return world;}}
class IronGolem extends LivingEntity{boolean raider;}
class VillagerEntityMCA extends LivingEntity{boolean kidnapped,hostile,guard,raider;boolean isKidnapped(){return kidnapped;}boolean isHostile(){return hostile;}boolean isGuard(){return guard;}boolean getProfession(){return raider;}}
enum Difficulty{PEACEFUL,NORMAL,HARD}
class ServerLevel{Difficulty difficulty=Difficulty.HARD;Difficulty getDifficulty(){return difficulty;}}
class Village{int getId(){return 1;}}
class ProfessionsMCA{static boolean isRaider(boolean v){return v;}}
class GolemPolice{static boolean prisoner(LivingEntity e){return e.prisoner;}}
class VillageWarfare{static Optional<Boolean>relation=Optional.empty();static boolean knockedOut(LivingEntity e){return e.knocked;}static Optional<Boolean>combatRelation(LivingEntity a,LivingEntity b){return relation;}static boolean isEnemy(int i,LivingEntity e){return false;}static boolean areEnemies(ServerLevel w,int a,int b){return false;}}
class Registry{String getKey(String type){return type;}}
class BuiltInRegistries{static Registry ENTITY_TYPE=new Registry();}
class Platform{Set<String>mods=new HashSet<>();boolean isModLoaded(String id){return mods.contains(id);}}
class MCA{static Platform platformHelper=new Platform();}
class SickenedCompat{static String TAG="sickened_mob";static boolean enabled(){return MCA.platformHelper.isModLoaded("sickened_mobs");}}
class HostileGolemCompat{
'''+hostile+'''
}
class GolemAllies{static boolean raider(IronGolem g){return g.raider;}static Optional<Village>home(IronGolem g){return Optional.empty();}
'''+enemy+'\n'+guard+'''
}
class CallbackInfoReturnable<T>{boolean cancelled;T value;void setReturnValue(T v){value=v;cancelled=true;}}
class ConversionHook{
'''+hook+'''
static CallbackInfoReturnable<Boolean>run(){var c=new CallbackInfoReturnable<Boolean>();mca$damageWithoutInfection(c);return c;}
}
public class Checks{static final ServerLevel WORLD=new ServerLevel();static int count;static void check(boolean ok,String n){if(!ok)throw new AssertionError(n);count++;}
 public static void main(String[]args){
  MCA.platformHelper.mods.add("sickened_mobs");IronGolem regular=new IronGolem(),sick=new IronGolem(),zombie=new IronGolem();sick.type="sickmob:sickened_iron_golem";zombie.type="minecraft:zombie_golem";VillagerEntityMCA person=new VillagerEntityMCA(),guard=new VillagerEntityMCA();guard.guard=true;
  check(HostileGolemCompat.hostile(sick),"custom sickened golem recognized");check(HostileGolemCompat.hostile(zombie),"minecraft zombie golem recognized");check(!HostileGolemCompat.hostile(regular),"ordinary golem remains friendly");
  check(HostileGolemCompat.hostileType("minecraft:zombie_goelm"),"reported golem ID spelling accepted");check(HostileGolemCompat.hostileType("multigolem:zombie_golem"),"alternate MultiGolem namespace accepted");check(!HostileGolemCompat.hostileType("minecraft:iron_golem"),"normal type not hostile");
  regular.tags.add("sickened_mob");check(HostileGolemCompat.hostile(regular),"tagged legacy sickened golem recognized");MCA.platformHelper.mods.clear();check(!HostileGolemCompat.hostile(regular),"removed sickness mod does not keep ordinary golem hostile");regular.tags.clear();MCA.platformHelper.mods.add("sickened_mobs");
  check(GolemAllies.enemy(sick,person),"sickened golem attacks MCA person");check(GolemAllies.enemy(zombie,person),"zombie golem attacks MCA person");check(!GolemAllies.enemy(regular,person),"friendly golem still protects MCA person");check(GolemAllies.enemy(regular,sick) && GolemAllies.enemy(sick,regular),"friendly and sickened golems recognize each other as enemies");check(GolemAllies.enemy(regular,zombie),"friendly golem defends against zombie golem");
  check(GolemAllies.enemyOfGuard(sick,guard) && GolemAllies.enemyOfGuard(zombie,guard),"guards treat both corrupt variants as enemies");
  VillageWarfare.relation=Optional.of(false);check(GolemAllies.enemy(sick,person) && GolemAllies.enemy(zombie,person),"ordinary village allegiance cannot neutralize hostile golems");VillageWarfare.relation=Optional.empty();
  person.kidnapped=true;check(!GolemAllies.enemy(sick,person),"kidnapped villager protection retained");person.kidnapped=false;person.prisoner=true;check(!GolemAllies.enemy(zombie,person),"prisoner protection retained");person.prisoner=false;person.alive=false;check(!GolemAllies.enemy(sick,person),"dead targets excluded");
  MCA.platformHelper.mods.clear();check(!HostileGolemCompat.hostile(sick),"sickened type hook inactive without Sickened Mobs");check(!HostileGolemCompat.hostile(zombie),"MultiGolem hostility hook inactive without Sickened Mobs");check(!ConversionHook.run().cancelled,"MultiGolem conversion untouched without Sickened Mobs");MCA.platformHelper.mods.add("sickened_mobs");var result=ConversionHook.run();check(result.cancelled && Boolean.FALSE.equals(result.value),"installed compatibility resumes damage instead of conversion");
  System.out.println("PASS: "+count+" production hostile-golem and allegiance checks");
 }
}
'''
home=os.environ.get('JAVA_HOME')
if not home:
 choices=list((Path.home()/'.gradle/jdks').glob('*/jdk-25*/Contents/Home'))
 if choices:home=str(choices[0])
binpath=Path(home)/'bin' if home else None
with tempfile.TemporaryDirectory(prefix='mca-hostile-golems-') as temp:
 p=Path(temp)/'Checks.java';p.write_text(code)
 subprocess.run([str(binpath/'javac') if binpath else shutil.which('javac'),str(p)],check=True)
 subprocess.run([str(binpath/'java') if binpath else shutil.which('java'),'-cp',temp,'Checks'],check=True)
