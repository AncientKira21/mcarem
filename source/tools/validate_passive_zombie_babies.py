#!/usr/bin/env python3
"""Run production baby attack/target rules in isolation, without launching Minecraft."""
from pathlib import Path
import subprocess,tempfile
root=Path(__file__).resolve().parents[1]
s=(root/'common/src/main/java/com/ancientkira/mca/entity/ZombieVillagerEntityMCA.java').read_text()
def extract(signature):
 a=s.index(signature);b=s.index('{',a);c=b+1;depth=1
 while depth:
  depth+=(s[c]=='{')-(s[c]=='}');c+=1
 return s[a:c].replace('@Nullable ','').replace('com.ancientkira.mca.server.InfirmaryCare.','InfirmaryCare.')
methods='\n'.join(extract(m) for m in ['public boolean isPassiveBaby(', 'public void setTarget(', 'public void setAggressive(', 'public boolean doHurtTarget(', 'public boolean isHostile(', 'public boolean canAttack('])
mixin=(root/'common/src/main/java/com/ancientkira/mca/mixin/MixinLivingEntity.java').read_text()
a=mixin.index('        if (source.getEntity() instanceof com.ancientkira.mca.entity.ZombieVillagerEntityMCA zombie')
b=mixin.index('        if (com.ancientkira.mca.server.UnderwaterVillageSupport.shouldCancelDrowning',a)
guard=mixin[a:b].replace('com.ancientkira.mca.entity.ZombieVillagerEntityMCA','Baby')
code='''class Entity{}
class LivingEntity extends Entity{}
class ServerLevel{}
enum AgeState{UNASSIGNED,BABY,TODDLER,CHILD,TEEN,ADULT}
class InfirmaryCare{static boolean isZombiePatient(Baby p){return p.patient;}}
class Parent extends LivingEntity{
 boolean vanillaBaby,sleeping,patient,aggressive;AgeState age=AgeState.ADULT;LivingEntity target;int hits;
 boolean isBaby(){return vanillaBaby;}AgeState getAgeState(){return age;}boolean isSleeping(){return sleeping;}
 public void setTarget(LivingEntity t){target=t;}public void setAggressive(boolean a){aggressive=a;}
 public boolean doHurtTarget(ServerLevel w,Entity t){hits++;return true;}public boolean canAttack(LivingEntity t){return true;}
}
class Baby extends Parent{
'''+methods+'''
}
class DamageSource{Entity owner;DamageSource(Entity e){owner=e;}Entity getEntity(){return owner;}}
class Result{boolean cancelled;void setReturnValue(boolean v){cancelled=!v;}}
public class Checks{
 static int checks;static void check(boolean b,String s){checks++;if(!b)throw new AssertionError(s);}
 static void damage(DamageSource source,Result result){
'''+guard+'''
 }
 public static void main(String[]args){
  for(AgeState age:AgeState.values())for(boolean vanillaBaby:new boolean[]{false,true}){
   Baby baby=new Baby();baby.age=age;baby.vanillaBaby=vanillaBaby;LivingEntity target=new LivingEntity();boolean passive=vanillaBaby||age==AgeState.BABY;
   check(baby.isPassiveBaby()==passive,"both baby representations recognized");baby.setTarget(target);check((baby.target==null)==passive,"no hunting or retaliation target");
   baby.setAggressive(true);check(baby.aggressive==!passive,"no aggressive pose");check(baby.canAttack(target)==!passive,"attack eligibility");check(baby.isHostile()==!passive,"hostility classification");
   check(baby.doHurtTarget(new ServerLevel(),target)==!passive,"direct attack blocked");check(baby.hits==(passive?0:1),"no damage side effects");
   Result result=new Result();damage(new DamageSource(baby),result);check(result.cancelled==passive,"indirect and reflected retaliation blocked");
   if(!passive){baby.sleeping=true;check(!baby.canAttack(target)&&!baby.isHostile(),"sleep protection retained");baby.sleeping=false;baby.patient=true;check(!baby.canAttack(target)&&!baby.isHostile(),"infirmary protection retained");}
  }
  Baby changed=new Baby();changed.setTarget(new LivingEntity());changed.setAggressive(true);changed.age=AgeState.BABY;changed.setTarget(new LivingEntity());changed.setAggressive(true);check(changed.target==null&&!changed.aggressive,"existing target cleared after age edit");
  Result normal=new Result();damage(new DamageSource(new LivingEntity()),normal);check(!normal.cancelled,"other mobs unchanged");
  System.out.println("PASS: "+checks+" production passive-baby checks");
 }
}
'''
java=next((Path.home()/'.gradle/jdks').glob('*/jdk-25*/Contents/Home'))/'bin'
with tempfile.TemporaryDirectory() as d:
 p=Path(d)/'Checks.java';p.write_text(code)
 subprocess.run([str(java/'javac'),str(p)],check=True)
 subprocess.run([str(java/'java'),'-cp',d,'Checks'],check=True)
