#!/usr/bin/env python3
"""Exercise production family loss/reunion methods without launching Minecraft."""
from pathlib import Path
import subprocess,tempfile,os
root=Path(__file__).resolve().parents[1]
s=(root/'common/src/main/java/com/ancientkira/mca/server/FamilyEmotions.java').read_text()
body=s[s.index('    public static final long FIVE_DAYS'):s.rfind('}')].replace('net.minecraft.world.entity.ai.memory.MemoryModuleType.','MemoryModuleType.').replace('net.minecraft.world.entity.ai.behavior.EntityTracker','EntityTracker')
def extract(text,signature):
 i=text.index(signature);a=text.index('{',i);n=1;b=a+1
 while n:n+=(text[b]=='{')-(text[b]=='}');b+=1
 return text[i:b]
node_source=(root/'common/src/main/java/com/ancientkira/mca/server/world/data/FamilyTreeNode.java').read_text()
mourning=extract(node_source,'public boolean mourningSpouse(').replace('com.ancientkira.mca.server.FamilyEmotions','FamilyEmotions')
update=extract(node_source,'public void updatePartner(@Nullable Entity').replace('@Nullable ','')
import json
messenger=(root/'common/src/main/java/com/ancientkira/mca/entity/ai/Messenger.java').read_text()
arguments=messenger[messenger.index('        Object[] newParams ='):messenger.index('        //also pass profession')]
phrase=json.loads((root/'common/src/main/resources/assets/mca_dialogue/lang/en_us.json').read_text())['family.reunited/1']
code='''import java.util.*;
class CompoundTag {
 Map<String,Object> m=new HashMap<>();Optional<CompoundTag> getCompound(String k){return Optional.ofNullable((CompoundTag)m.get(k));}
 long getLongOr(String k,long d){return m.get(k) instanceof Long v?v:d;}boolean getBooleanOr(String k,boolean d){return m.get(k) instanceof Boolean v?v:d;}
 String getStringOr(String k,String d){return m.get(k) instanceof String v?v:d;}void putString(String k,String v){m.put(k,v);}void putLong(String k,long v){m.put(k,v);}void putBoolean(String k,boolean v){m.put(k,v);}void put(String k,CompoundTag v){m.put(k,v);}void remove(String k){m.remove(k);}boolean contains(String k){return m.containsKey(k);}Set<String> keySet(){return m.keySet();}
}
class MemoryModuleType{static final Object ATTACK_TARGET=new Object(),WALK_TARGET=new Object(),PATH=new Object(),LOOK_TARGET=new Object();}
class MCA {static Server server=new Server();static Optional<Server> getServer(){return Optional.of(server);}}
class Server {ServerLevel world=new ServerLevel();ServerLevel overworld(){return world;}}
class ServerLevel {long time;Map<UUID,Entity> entities=new HashMap<>();long getGameTime(){return time;}Entity getEntity(UUID id){return entities.get(id);}}
class FamilyTree {static FamilyTree tree=new FamilyTree();Map<UUID,FamilyTreeNode> nodes=new LinkedHashMap<>();boolean dirty;static FamilyTree get(ServerLevel w){return tree;}Optional<FamilyTreeNode> getOrEmpty(UUID id){return Optional.ofNullable(nodes.get(id));}java.util.stream.Stream<FamilyTreeNode> getAllEntries(){return nodes.values().stream();}void setDirty(){dirty=true;}void getOrCreate(Entity e){}}
enum RelationshipState {SINGLE,WIDOW,MARRIED_TO_VILLAGER,ENGAGED;boolean isMarried(){return this==MARRIED_TO_VILLAGER;}}
class Util {static UUID NIL_UUID=new UUID(0,0);}
class FamilyTreeNode {FamilyTree rootNode=FamilyTree.tree;UUID partner=Util.NIL_UUID;RelationshipState relationshipState=RelationshipState.WIDOW;UUID id=UUID.randomUUID();boolean deceased;Set<UUID> family=new HashSet<>();UUID widowedPartner=new UUID(0,0);CompoundTag emotions=new CompoundTag();CompoundTag familyEmotions=emotions;FamilyTreeNode(){FamilyTree.tree.nodes.put(id,this);}UUID id(){return id;}boolean isDeceased(){return deceased;}FamilyTree getRoot(){return FamilyTree.tree;}boolean isFamilyMember(UUID id){return family.contains(id);}UUID getWidowedPartner(){return widowedPartner;}CompoundTag familyEmotions(){return emotions;}void markDirty(){rootNode.setDirty();}void rememberWidowedMarriage(){}
'''+mourning+'\n'+update+'''
}
class Entity {UUID id;ServerLevel world=MCA.server.world;boolean alive=true,removed;String name="relative";double x,z;double getX(){return x;}double getZ(){return z;}Entity(UUID id){this.id=id;world.entities.put(id,this);}ServerLevel level(){return world;}UUID getUUID(){return id;}boolean isAlive(){return alive;}boolean isRemoved(){return removed;}String getName(){return name;}}
interface VillagerLike<T>{Object FAMILY_MOOD=new Object(),FAMILY_HUG=new Object();<V>V getTrackedValue(Object key);<V>void setTrackedValue(Object key,V value);Entity asEntity();MoodBrain getVillagerBrain();}
class Relationship{FamilyTreeNode node;Relationship(FamilyTreeNode n){node=n;}FamilyTreeNode getFamilyEntry(){return node;}}
class Navigation{int moves,stops;void stop(){stops++;}void moveTo(Entity e,double speed){moves++;}}
class Look{Entity target;void setLookAt(Entity e,int x,int y){target=e;}}
record EntityTracker(Entity entity,boolean eyes){}
class Interactions{Optional<Entity> getInteractingPlayer(){return Optional.empty();}}
class Brain{boolean combat;Map<Object,Object> memories=new HashMap<>();void eraseMemory(Object key){memories.remove(key);}void setMemory(Object key,Object value){memories.put(key,value);}boolean hasMemoryValue(Object key){return key==MemoryModuleType.ATTACK_TARGET?combat:memories.containsKey(key);}}
class MoodBrain{VillagerEntityMCA person;int mood;MoodBrain(VillagerEntityMCA p){person=p;}void setMoodValue(int value){mood=FamilyEmotions.lockedMood(person,value);}}
class VillagerEntityMCA extends Entity implements VillagerLike<VillagerEntityMCA>{
 float yaw,bodyYaw,headYaw;void setYRot(float v){yaw=v;}void setYBodyRot(float v){bodyYaw=v;}void setYHeadRot(float v){headYaw=v;}Interactions getInteractions(){return new Interactions();}FamilyTreeNode node;Map<Object,Object> tracked=new HashMap<>();Navigation nav=new Navigation();Look look=new Look();Brain brain=new Brain();MoodBrain mood=new MoodBrain(this);Object lastName;int messages;boolean sleeping,passenger,sight=true;
 VillagerEntityMCA(FamilyTreeNode n){super(n.id);node=n;tracked.put(FAMILY_MOOD,0);tracked.put(FAMILY_HUG,false);}
 @SuppressWarnings("unchecked")public <V>V getTrackedValue(Object key){return (V)tracked.get(key);}public <V>void setTrackedValue(Object k,V v){tracked.put(k,v);}public Entity asEntity(){return this;}
 Relationship getRelationships(){return new Relationship(node);}public MoodBrain getVillagerBrain(){return mood;}Navigation getNavigation(){return nav;}Look getLookControl(){return look;}Brain getBrain(){return brain;}
 double distanceToSqr(Entity e){return (x-e.x)*(x-e.x);}boolean isSleeping(){return sleeping;}boolean isPassenger(){return passenger;}boolean hasLineOfSight(Entity e){return sight;}void sendChatToAllAround(String key,Object name){messages++;lastName=name;}
}
class FamilyEmotions {
'''+body+'''
}
public class Checks{
 /*SPOKEN*/
 static int checks;static void check(boolean ok,String name){if(!ok)throw new AssertionError(name);checks++;}
 public static void main(String[]args){
  FamilyTreeNode dead=new FamilyTreeNode(),spouse=new FamilyTreeNode(),child=new FamilyTreeNode(),stranger=new FamilyTreeNode();
  spouse.family.add(dead.id);spouse.widowedPartner=dead.id;child.family.add(dead.id);dead.deceased=true;
  MCA.server.world.time=100;FamilyEmotions.death(dead);
  check(FamilyEmotions.state(spouse.emotions,100)==1,"spouse mourns");check(FamilyEmotions.state(child.emotions,100)==1,"child mourns");check(FamilyEmotions.state(stranger.emotions,100)==0,"stranger unchanged");
  check(FamilyEmotions.state(spouse.emotions,120099)==1,"mourning lasts all 120000 ticks");check(FamilyEmotions.state(spouse.emotions,120100)==0,"mourning ends at five days");
  check(spouse.mourningSpouse(),"widow cannot remarry while mourning");check(!child.mourningSpouse(),"mourning another relative does not block marriage");
  FamilyTreeNode candidate=new FamilyTreeNode();candidate.relationshipState=RelationshipState.SINGLE;Entity candidateEntity=new Entity(candidate.id);
  spouse.updatePartner(candidateEntity,RelationshipState.ENGAGED);check(spouse.partner.equals(Util.NIL_UUID),"engagement blocked during mourning");
  MCA.server.world.time=120100;spouse.updatePartner(candidateEntity,RelationshipState.ENGAGED);check(spouse.partner.equals(candidate.id),"new relationship allowed after five days");
  spouse.partner=Util.NIL_UUID;spouse.relationshipState=RelationshipState.WIDOW;spouse.widowedPartner=dead.id;MCA.server.world.time=100;
  candidate.updatePartner(new Entity(spouse.id),RelationshipState.MARRIED_TO_VILLAGER);check(candidate.partner.equals(Util.NIL_UUID),"cannot bypass lock by marrying from other side");
  VillagerEntityMCA survivor=new VillagerEntityMCA(spouse);FamilyEmotions.tick(survivor);check(survivor.mood.mood==-15,"loaded spouse depressed");check(FamilyEmotions.lockedMood(survivor,15)==-15,"gifts cannot improve locked grief");
  Entity returned=new Entity(dead.id);dead.deceased=false;MCA.server.world.time=200;FamilyEmotions.revive(returned);
  check(FamilyEmotions.state(spouse.emotions,200)==2,"revival ends spouse grief early");check(!spouse.mourningSpouse(),"revival clears widow marriage lock early");check(FamilyEmotions.state(child.emotions,200)==2,"unloaded child celebrates too");check(FamilyEmotions.state(dead.emotions,200)==2,"returned person celebrates");check(FamilyEmotions.state(stranger.emotions,200)==0,"strangers get no reunion lock");
  check(FamilyEmotions.state(spouse.emotions,120199)==2,"upbeat lasts five days");check(FamilyEmotions.state(spouse.emotions,120200)==0,"upbeat expires at boundary");
  check(FamilyEmotions.lockedMood(survivor,-15)==15,"upbeat resists negative mood changes");
  MCA.server.world.time=300;FamilyEmotions.revive(returned);check(spouse.emotions.getLongOr("upbeatUntil",0)==120200,"duplicate revival does not extend lock");
  VillagerEntityMCA revived=new VillagerEntityMCA(dead);survivor.x=10;FamilyEmotions.tick(survivor);check(survivor.nav.moves==1 && survivor.messages==0,"approach before hugging");
  survivor.x=.8;survivor.sight=false;FamilyEmotions.tick(survivor);check(survivor.messages==0,"no hugging through walls");
  survivor.sight=true;survivor.brain.combat=true;FamilyEmotions.tick(survivor);check(survivor.messages==0,"combat takes priority");survivor.brain.combat=false;
  revived.name="Aurora";survivor.name="Kenna";CompoundTag reciprocal=new CompoundTag();reciprocal.putBoolean(spouse.id.toString(),true);dead.emotions.put("reunions",reciprocal);
  FamilyEmotions.tick(survivor);check(survivor.lastName.equals("Aurora")&&revived.lastName.equals("Kenna"),"both reunion messages address the other relative");check(spoken("Ivan",survivor.lastName).equals("I missed you so much, Aurora!"),"listener name is not used as relative name");check(spoken("Another player",survivor.lastName).equals(spoken("Ivan",survivor.lastName)),"all listeners hear the same relative name");check(!reciprocal.contains(spouse.id.toString()),"reciprocal pending hug removed to prevent repeated dialogue");check(survivor.messages==1 && revived.messages==1,"both relatives say missed each other");check((Boolean)survivor.getTrackedValue(VillagerLike.FAMILY_HUG) && (Boolean)revived.getTrackedValue(VillagerLike.FAMILY_HUG),"both use hug pose");
  survivor.look.target=new Entity(UUID.randomUUID());FamilyEmotions.tick(survivor);check(survivor.messages==1,"reunion only once");check(survivor.look.target==revived,"hug gaze stays on relative instead of player");check(survivor.yaw==90 && revived.yaw==-90,"both bodies face each other");
  VillagerEntityMCA waiting=new VillagerEntityMCA(child);waiting.x=.4;FamilyEmotions.tick(waiting);check(waiting.messages==0,"third relative waits while pair is hugging");
  MCA.server.world.time=360;FamilyEmotions.tick(survivor);check(!(Boolean)survivor.getTrackedValue(VillagerLike.FAMILY_HUG),"hug pose ends");
  MCA.server.world.time=120200;FamilyEmotions.tick(survivor);check(FamilyEmotions.lockedMood(survivor,2)==2,"normal mood changes resume");
  dead.deceased=true;FamilyEmotions.death(dead);check(FamilyEmotions.state(spouse.emotions,120200)==1,"second death starts a fresh mourning period");dead.deceased=false;MCA.server.world.time++;FamilyEmotions.revive(revived);check(FamilyEmotions.state(spouse.emotions,120201)==2,"second revival celebrates again");
  FamilyEmotions.overrideMood(survivor,-4);FamilyEmotions.tick(survivor);
  check(FamilyEmotions.state(spouse.emotions,120201)==0 && survivor.mood.mood==-4,"editor ends joy lock and applies new mood");
  dead.deceased=true;FamilyEmotions.death(dead);FamilyEmotions.tick(survivor);
  check(spouse.mourningSpouse(),"new grief still locks after earlier editor change");
  FamilyEmotions.overrideMood(survivor,0);FamilyEmotions.tick(survivor);
  check(!spouse.mourningSpouse() && survivor.mood.mood==0,"editor ends grief timer and restores neutral mood");
  System.out.println("PASS: "+checks+" production family emotion/reunion checks");
 }
}
'''
code=code.replace('/*SPOKEN*/','static String spoken(String targetName,Object...params){'+arguments+'return String.format('+json.dumps(phrase)+',newParams);}')
homes=list((Path.home()/'.gradle/jdks').glob('*/jdk-25*/Contents/Home'))
java=Path(os.environ.get('JAVA_HOME',str(homes[0])))/'bin'
with tempfile.TemporaryDirectory(prefix='mca-family-') as d:
 p=Path(d)/'Checks.java';p.write_text(code)
 subprocess.run([str(java/'javac'),str(p)],check=True)
 subprocess.run([str(java/'java'),'-cp',d,'Checks'],check=True)
