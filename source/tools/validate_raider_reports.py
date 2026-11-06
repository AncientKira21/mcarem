#!/usr/bin/env python3
"""Check production redemption cleanup and physical melee limits without a game launch."""
from pathlib import Path
import subprocess,tempfile,os
root=Path(__file__).resolve().parents[1]/'common/src/main/java/com/ancientkira/mca'
def method(path,signature):
 s=(root/path).read_text();i=s.index(signature);a=s.index('{',i);n=1;b=a+1
 while n:n+=(s[b]=='{')-(s[b]=='}');b+=1
 return s[i:b]
melee=method('entity/ai/MeleeAttack.java','public static boolean canHit(')
redeem=method('server/RaiderSocial.java','public static void redeem(')
for full,short in [('com.ancientkira.mca.entity.VillagerEntityMCA','VillagerEntityMCA'),('com.ancientkira.mca.registry.ProfessionsMCA','ProfessionsMCA'),('net.minecraft.util.Mth','Mth'),('net.minecraft.core.registries.BuiltInRegistries','BuiltInRegistries'),('net.minecraft.world.entity.npc.villager.VillagerProfession','VillagerProfession'),('com.ancientkira.mca.entity.ai.MoveState','MoveState')]:
 melee=melee.replace(full,short);redeem=redeem.replace(full,short)
code='''import java.util.*;
class Vec3 {double x,y,z;Vec3(double x,double y,double z){this.x=x;this.y=y;this.z=z;}double distanceToSqr(Vec3 b){return (x-b.x)*(x-b.x)+(y-b.y)*(y-b.y)+(z-b.z)*(z-b.z);}}
class Box {double minX,maxX,minY=0,maxY=1.8,minZ,maxZ;Box(double x,double z,double width){minX=x-width/2;maxX=x+width/2;minZ=z-width/2;maxZ=z+width/2;}Vec3 getCenter(){return new Vec3((minX+maxX)/2,.9,(minZ+maxZ)/2);}}
class Mth {static double clamp(double x,double a,double b){return Math.max(a,Math.min(b,x));}}
class ClipContext {enum Block{COLLIDER}enum Fluid{NONE}ClipContext(Vec3 a,Vec3 b,Block c,Fluid d,Mob e){}}
class HitResult {enum Type{MISS,BLOCK}Type type;HitResult(Type t){type=t;}Type getType(){return type;}}
class Level{boolean blocked;HitResult clip(ClipContext c){return new HitResult(blocked?HitResult.Type.BLOCK:HitResult.Type.MISS);}}
class LivingEntity{double x,z;float width=.6F;boolean alive=true;double getX(){return x;}double getZ(){return z;}float getBbWidth(){return width;}Vec3 getEyePosition(){return new Vec3(x,1.6,z);}Box getBoundingBox(){return new Box(x,z,width);}}
class Mob extends LivingEntity{boolean inRange=true;Level world=new Level();boolean isWithinMeleeAttackRange(LivingEntity t){return inRange;}Level level(){return world;}}
class RangedWeaponHelper {static boolean isValidAttackTarget(Mob a,LivingEntity b){return b!=null&&b.alive;}}
class ProfessionsMCA{static Object WITCH=new Object(),VINDICATOR=new Object(),GUARD=new Object();static boolean isRaider(Object p){return p==WITCH||p==VINDICATOR;}}
class VillagerProfession{static Object NONE=new Object();}
class Registry{Object getValueOrThrow(Object key){return key;}}
class BuiltInRegistries{static Registry VILLAGER_PROFESSION=new Registry();}
class MemoryModuleType<T>{static MemoryModuleType<Object> JOB_SITE=new MemoryModuleType<>(),POTENTIAL_JOB_SITE=new MemoryModuleType<>(),ATTACK_TARGET=new MemoryModuleType<>(),HURT_BY_ENTITY=new MemoryModuleType<>(),WALK_TARGET=new MemoryModuleType<>(),PATH=new MemoryModuleType<>();}
class Brain{Set<MemoryModuleType<?>> memories=new HashSet<>();void eraseMemory(MemoryModuleType<?> m){memories.remove(m);}}
enum MoveState{MOVE,FOLLOW,STAY}
class MoodBrain{MoveState state=MoveState.STAY;void setMoveState(MoveState s,Object leader){state=s;}}
class Navigation{boolean stopped;void stop(){stopped=true;}}
class Residency{boolean sought;void seekHome(){sought=true;}}
class RaiderCombat{static void clear(VillagerEntityMCA p){p.casting=false;}}
class VillagerEntityMCA extends Mob{
 Object profession=ProfessionsMCA.WITCH,target=new Object();int delay=100;boolean casting=true,released;Brain brain=new Brain();MoodBrain mood=new MoodBrain();Navigation nav=new Navigation();Residency residency=new Residency();
 Object getProfession(){return profession;}void setProfession(Object p){profession=p;}void releasePoi(Object m){released=true;}Brain getBrain(){return brain;}void setDespawnDelay(int d){delay=d;}void setTarget(Object t){target=t;}Navigation getNavigation(){return nav;}MoodBrain getVillagerBrain(){return mood;}Residency getResidency(){return residency;}
}
class MeleeAttack {
'''+melee+'''
}
class RaiderSocial {
'''+redeem+'''
}
public class Checks{
 static int checks;static void check(boolean ok,String name){if(!ok)throw new AssertionError(name);checks++;}
 public static void main(String[]args){
  VillagerEntityMCA witch=new VillagerEntityMCA();witch.brain.memories.addAll(List.of(MemoryModuleType.JOB_SITE,MemoryModuleType.POTENTIAL_JOB_SITE,MemoryModuleType.ATTACK_TARGET,MemoryModuleType.HURT_BY_ENTITY,MemoryModuleType.WALK_TARGET,MemoryModuleType.PATH));
  RaiderSocial.redeem(witch);check(witch.profession==VillagerProfession.NONE,"witch becomes ordinary unemployed person");check(witch.target==null&&witch.brain.memories.isEmpty(),"old combat and workplace memories cleared");check(!witch.casting&&witch.nav.stopped,"stop witch casting and navigation");check(witch.mood.state==MoveState.MOVE,"remove old stay/follow order");check(witch.delay==0&&witch.residency.sought,"redeemed person settles instead of despawning");
  witch.mood.setMoveState(MoveState.FOLLOW,new Object());check(!ProfessionsMCA.isRaider(witch.getProfession())&&witch.mood.state==MoveState.FOLLOW,"follow eligible after redemption");
  VillagerEntityMCA guard=new VillagerEntityMCA();guard.profession=ProfessionsMCA.GUARD;RaiderSocial.redeem(guard);check(guard.profession==ProfessionsMCA.GUARD,"non-raider profession unchanged");
  LivingEntity target=new LivingEntity();VillagerEntityMCA vindicator=new VillagerEntityMCA();vindicator.profession=ProfessionsMCA.VINDICATOR;target.x=2;
  check(!MeleeAttack.canHit(vindicator,target),"vindicator cannot hit a human two blocks away");target.x=1.25;check(MeleeAttack.canHit(vindicator,target),"vindicator can strike close target");vindicator.world.blocked=true;check(!MeleeAttack.canHit(vindicator,target),"solid wall blocks strike");vindicator.world.blocked=false;vindicator.inRange=false;check(!MeleeAttack.canHit(vindicator,target),"vanilla range is still required");vindicator.inRange=true;target.x=1.9;target.width=2;check(MeleeAttack.canHit(vindicator,target),"large target hitbox respected");target.width=.6F;target.x=2;Mob ordinary=new Mob();check(MeleeAttack.canHit(ordinary,target),"other mobs retain existing reach");target.alive=false;check(!MeleeAttack.canHit(vindicator,target),"invalid target rejected");check(!MeleeAttack.canHit(vindicator,null),"missing target rejected");
  System.out.println("PASS: "+checks+" production redemption and melee checks");
 }
}
'''
homes=list((Path.home()/'.gradle/jdks').glob('*/jdk-25*/Contents/Home'));java=Path(os.environ.get('JAVA_HOME',str(homes[0])))/'bin'
with tempfile.TemporaryDirectory(prefix='mca-raider-') as d:
 p=Path(d)/'Checks.java';p.write_text(code);subprocess.run([str(java/'javac'),str(p)],check=True);subprocess.run([str(java/'java'),'-cp',d,'Checks'],check=True)
