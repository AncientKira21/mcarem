#!/usr/bin/env python3
"""Exercise production faction death conversion without starting Minecraft."""
from pathlib import Path
import subprocess, tempfile
root=Path(__file__).resolve().parents[1]
s=(root/'common/src/main/java/com/ancientkira/mca/compat/SickenedCompat.java').read_text()
a=s.index('    private static boolean undead('); b=s.index('    public static void onHurt(',a)
methods=s[a:b].replace('com.ancientkira.mca.registry.EntitiesMCA.','Types.').replace('net.minecraft.world.entity.monster.skeleton.AbstractSkeleton','AbstractSkeleton').replace('net.minecraft.tags.EntityTypeTags.UNDEAD','"undead"')
for prefix in ('net.minecraft.world.entity.ai.attributes.', 'net.minecraft.world.entity.monster.', 'net.minecraft.world.entity.animal.equine.', 'net.minecraft.world.entity.boss.enderdragon.'):
 methods=methods.replace(prefix,'')
code='''import java.util.*;
class Entity {Set<String> tags=new HashSet<>(); Type getType(){return new Type();} Set<String> entityTags(){return tags;}}
class Type{Type builtInRegistryHolder(){return this;} boolean is(String tag){return false;}}
class LivingEntity extends Entity {ServerLevel world=new ServerLevel();Object level(){return world;}boolean wither;boolean hasEffect(String e){return wither;}}
class Attributes{static String ATTACK_DAMAGE="attack";}
class Attr{boolean hasAttribute(String a){return true;}}
class Mob extends LivingEntity{Attr getAttributes(){return new Attr();}double getAttributeValue(String a){return 3;}}
class Enderman extends Mob{}
class Creeper extends Mob{}
class Slime extends Mob{}
class Ghast extends Mob{}
class Llama extends Mob{}
class EnderDragon extends Mob{}
class Zombie extends Mob{}
class AbstractSkeleton extends Mob{}
class WitherSkeleton extends AbstractSkeleton{}
class WitherBoss extends LivingEntity{}
enum Difficulty{HARD,NORMAL,EASY}
class ServerLevel{Difficulty difficulty=Difficulty.HARD;Difficulty getDifficulty(){return difficulty;}}
class DamageTypes{static String WITHER="wither",WITHER_SKULL="skull";}
class MobEffects{static String WITHER="wither",STRENGTH="strength";}
class DamageSource{Entity attacker;String type="melee";DamageSource(Entity e){attacker=e;}Entity getEntity(){return attacker;}boolean is(String t){return type.equals(t);}}
class ConversionParams{static Object single(Object p,boolean a,boolean b){return null;}}
class Types{static String SICKENED_PERSON="sickened",ZOMBIE_PERSON="zombie";}
class ZombieVillagerEntityMCA extends Zombie {
 ZombieVillagerEntityMCA result;boolean fail;String target;float health;UUID id=UUID.randomUUID();String name="Relative";
 ZombieVillagerEntityMCA convertTo(String t,Object params,java.util.function.Consumer<ZombieVillagerEntityMCA> callback){
  if(fail)return null;target=t;result=new ZombieVillagerEntityMCA();result.id=id;result.name=name;callback.accept(result);return result;}
 float getMaxHealth(){return 20;}void setHealth(float h){health=h;}void removeEffect(String e){}void setTarget(Object o){}void setLastHurtByMob(Object o){}
}
public class Checks{
 static String TAG="sickened_mob";static boolean installed=true;static int checks;
 static boolean enabled(){return installed;}static void mark(ZombieVillagerEntityMCA p){p.tags.add(TAG);}static void clear(ZombieVillagerEntityMCA p){p.tags.clear();}
'''+methods+'''
 static void check(boolean b,String n){checks++;if(!b)throw new AssertionError(n);}
 public static void main(String[] args){
  for(boolean sick:new boolean[]{false,true})for(int attacker=0;attacker<8;attacker++){
   ZombieVillagerEntityMCA p=new ZombieVillagerEntityMCA();if(sick)mark(p);
   Entity e=switch(attacker){case 0->new Zombie();case 1->new AbstractSkeleton();case 2->new WitherSkeleton();case 3->new WitherBoss();case 4->new Zombie();case 5->new Entity();case 7->new Enderman();default->null;};
   if(attacker==4)e.tags.add(TAG);boolean expected=sick?(attacker<2||attacker==7):attacker>=2&&attacker<=4;
   boolean converted=onDeath(p,new DamageSource(e));check(converted==expected,"opposing factions "+sick+"/"+attacker);
   if(converted){check(p.result.health==20,"restored health");check(p.result.tags.contains(TAG)==!sick,"correct faction");check(p.target.equals(sick?"zombie":"sickened"),"correct type");}
  }
  for(Difficulty d:new Difficulty[]{Difficulty.NORMAL,Difficulty.EASY}){var p=new ZombieVillagerEntityMCA();p.world.difficulty=d;var e=new Zombie();e.tags.add(TAG);check(!onDeath(p,new DamageSource(e)),"Hard-only external death rule");}
  var p=new ZombieVillagerEntityMCA();var e=new Zombie();e.tags.add(TAG);installed=false;check(!onDeath(p,new DamageSource(e)),"mod absent");installed=true;
  p.fail=true;check(!onDeath(p,new DamageSource(e)),"failed conversion lets death continue");
  check(!onDeath(new LivingEntity(),new DamageSource(e)),"living infections retain staged MCA rules");
  System.out.println("PASS: "+checks+" production faction conversion checks");
 }
}
'''
java=next((Path.home()/'.gradle/jdks').glob('*/jdk-25*/Contents/Home'))/'bin'
with tempfile.TemporaryDirectory() as d:
 p=Path(d)/'Checks.java';p.write_text(code)
 subprocess.run([str(java/'javac'),str(p)],check=True)
 subprocess.run([str(java/'java'),'-cp',d,'Checks'],check=True)
# These declarations are also compiled by the real six-loader/version builds.
registry=(root/'common/src/main/java/com/ancientkira/mca/registry/EntitiesMCA.java').read_text()
assert 'EntityType<ZombieVillagerEntityMCA> SICKENED_PERSON' in registry
assert 'EntityType<ZombieVillagerEntityMCA> SICKENED_VILLAGER' in registry
assert 'SickenedCompat.onDeath(victim, source)' in (root/'common/src/main/java/com/ancientkira/mca/mixin/compat/MixinSickenedConversion.java').read_text()

zombie=(root/'common/src/main/java/com/ancientkira/mca/entity/ZombieVillagerEntityMCA.java').read_text()
die=zombie[zombie.index('    public void die(DamageSource cause)'):zombie.index('    public void setInventory(')]
assert die.index('SickenedCompat.onDeath(this, cause)') < die.index('super.die(cause)') < die.index('relations.onDeath(cause)')
assert 'if (mob instanceof VillagerEntityMCA) {' in zombie
assert 'zombie.setInventory(inventory);' in zombie
