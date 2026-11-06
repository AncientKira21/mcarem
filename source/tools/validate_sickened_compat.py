#!/usr/bin/env python3
"""Run production sickness lifecycle and cure logic against lightweight Minecraft fixtures; never launch the game."""
from pathlib import Path
import subprocess,tempfile,os,shutil
ROOT=Path(__file__).resolve().parents[1]
s=(ROOT/'common/src/main/java/com/ancientkira/mca/compat/SickenedCompat.java').read_text()
body=s[s.index('    public static final String MOD'):s.rfind('}')]
def extract(text, signature):
    start=text.index(signature);brace=text.index('{',start);depth=1;end=brace+1
    while depth:
        depth+=(text[end]=='{')-(text[end]=='}');end+=1
    return text[start:end]
purple=extract((ROOT/'common/src/main/java/com/ancientkira/mca/client/resources/ColorPalette.java').read_text(),'public static int applyPurpleShift(')
visual=extract((ROOT/'common/src/main/java/com/ancientkira/mca/client/render/VillagerVisuals.java').read_text(),'public float skinInfectionProgress(')

items_source=(ROOT/'common/src/main/java/com/ancientkira/mca/registry/ItemsMCA.java').read_text()
register_items=extract(items_source,'static void registerItems(').replace('com.ancientkira.mca.compat.SickenedCompat','SickenedCompat').replace('com.ancientkira.mca.item.SickenedPersonSpawnEggItem','SickenedPersonSpawnEggItem')
infirmary_sick=extract((ROOT/'common/src/main/java/com/ancientkira/mca/server/InfirmaryCare.java').read_text(),'private static boolean sick(').replace('com.ancientkira.mca.compat.SickenedCompat','SickenedCompat')

speech=extract((ROOT/'common/src/main/java/com/ancientkira/mca/entity/VillagerLike.java').read_text(),'default boolean isSpeechImpaired(').replace('com.ancientkira.mca.compat.SickenedCompat','SickenedCompat').replace('BABBLING_THRESHOLD','.6F')
rules=extract((ROOT/'common/src/main/java/com/ancientkira/mca/entity/InfectionRules.java').read_text(),'public static boolean roll(')
code='''import java.util.*;import java.util.function.Consumer;
class Param<T>{T initial;Param(T v){initial=v;}}
interface VillagerLike<T>{Param<Float> SICKNESS_PROGRESS=new Param<>(0F),SICKNESS_ZOMBIE_PROGRESS=new Param<>(0F);Param<Boolean>SICKENED=new Param<>(false);<V>V getTrackedValue(Param<V> p);<V>void setTrackedValue(Param<V> p,V v);Entity asEntity();default float getInfectionProgress(){return 0;}
'''+speech+'''
default Data getVillagerData(){return ((LivingEntity)asEntity()).villagerData;}default void sendChatToAllAround(String key){((LivingEntity)asEntity()).messages++;}}
class ProfessionsMCA{static Object GUARD=new Object();}
class Profession{Object profession;Object value(){return profession;}}
class Data{Profession p=new Profession();int rank=1;Profession profession(){return p;}int level(){return rank;}}
class Tracker{Map<Param<?>,Object> values=new HashMap<>();@SuppressWarnings("unchecked")<V>V get(Param<V> p){return (V)values.getOrDefault(p,p.initial);}<V>void set(Param<V> p,V v){values.put(p,v);}}
class Platform{boolean installed;boolean isModLoaded(String s){return installed;}}
class MCA{static Platform platformHelper=new Platform();static Identifier locate(String s){return Identifier.fromNamespaceAndPath("mca",s);}interface RegisterHelper<T>{void register(Identifier id,T item);}}
class Config{static Config c=new Config();boolean enableInfection=true;int infectionTime=10;float zombieBiteInfectionChance=.05F,infectionChanceDecreasePerLevel=.25F;static Config getInstance(){return c;}}
record Identifier(String namespace,String path){static Identifier fromNamespaceAndPath(String n,String p){return new Identifier(n,p);}}
class Item{Identifier id;Item(String path){id=Identifier.fromNamespaceAndPath("sickened_mobs",path);}Item(String namespace,String path){id=Identifier.fromNamespaceAndPath(namespace,path);}}
class ItemStack{Item item;int count;ItemStack(Item i){this(i,1);}ItemStack(Item i,int n){item=i;count=n;}Item getItem(){return item;}void shrink(int n){count=Math.max(0,count-n);}}
class Items{static Item BOWL=new Item("minecraft","bowl");}
record Holder(Item value){}
class Registry{Map<Identifier,Item> items=new HashMap<>();Identifier getKey(Item i){return i.id;}Optional<Holder> get(Identifier id){return Optional.ofNullable(items.get(id)).map(Holder::new);}}
class BuiltInRegistries{static Registry ITEM=new Registry();}
enum Difficulty{PEACEFUL,EASY,NORMAL,HARD}
class FixedRandom extends Random{boolean coin=true;float roll=.01F;@Override public float nextFloat(){return roll;}@Override public boolean nextBoolean(){return coin;}@Override public int nextInt(int bound){return bound-1;}}
class ServerLevel{Difficulty difficulty=Difficulty.HARD;FixedRandom random=new FixedRandom();boolean isClientSide(){return false;}Difficulty getDifficulty(){return difficulty;}FixedRandom getRandom(){return random;}void sendParticles(Object p,double x,double y,double z,int n,double a,double b,double c,double d){}}
class Entity{ServerLevel world=new ServerLevel();Set<String> tags=new HashSet<>();UUID id=UUID.randomUUID();ServerLevel level(){return world;}Set<String>entityTags(){return tags;}void addTag(String s){tags.add(s);}void removeTag(String s){tags.remove(s);}double getX(){return 0;}double getY(){return 0;}double getZ(){return 0;}}
class LivingEntity extends Entity{boolean alive=true,wither,strength=true;int fleshDrops,messages;Data villagerData=new Data();FixedRandom random=new FixedRandom();boolean isAlive(){return alive;}boolean hasEffect(Object e){return wither;}void removeEffect(Object e){strength=false;}FixedRandom getRandom(){return random;}void spawnAtLocation(ServerLevel w,ItemStack s){fleshDrops+=s.count;}String getName(){return "Alex";}}
class Mob extends LivingEntity{int tickCount=20;boolean persistent;void setPersistenceRequired(){persistent=true;}void setTarget(Object t){}}
class Zombie extends Mob{}
class WitherSkeleton extends Mob{}
class WitherBoss extends Mob{}
class VillagerEntityMCA extends Mob implements VillagerLike<VillagerEntityMCA>{Tracker data=new Tracker();float zombieInfection;ZombieVillagerEntityMCA converted;int meals;public <V>V getTrackedValue(Param<V>p){return data.get(p);}public <V>void setTrackedValue(Param<V>p,V v){data.set(p,v);}public Entity asEntity(){return this;}public float getInfectionProgress(){return zombieInfection;}boolean isInfected(){return zombieInfection>0;}void ate(){meals++;}Mob convertTo(EntityType t,Object params,Consumer<Mob> callback){converted=new ZombieVillagerEntityMCA();converted.id=id;callback.accept(converted);converted.data.values.putAll(data.values);return converted;}}
class ZombieVillagerEntityMCA extends Zombie implements VillagerLike<ZombieVillagerEntityMCA>{Tracker data=new Tracker();public <V>V getTrackedValue(Param<V>p){return data.get(p);}public <V>void setTrackedValue(Param<V>p,V v){data.set(p,v);}public Entity asEntity(){return this;}}
class EntityType{static EntityType ZOMBIE_VILLAGER=new EntityType();}
class ConversionParams{static Object single(Object m,boolean a,boolean b){return null;}}
class MobEffects{static String STRENGTH="strength",WITHER="wither";}
class DamageTypes{static String WITHER="wither";}
class DamageSource{Entity attacker;boolean wither;DamageSource(Entity a){attacker=a;}Entity getEntity(){return attacker;}boolean is(Object t){return wither;}}
class ParticleTypes{static String HAPPY_VILLAGER="happy";}
class Component{static Object translatable(String key,Object...a){return key;}}
class Abilities{boolean instabuild;}
class Inventory{int bowls;boolean add(ItemStack s){bowls+=s.count;return true;}}
class ServerPlayer extends LivingEntity{Abilities abilities=new Abilities();Inventory inventory=new Inventory();int messages;Abilities getAbilities(){return abilities;}Inventory getInventory(){return inventory;}void sendSystemMessage(Object m){messages++;}}
class InfectionRules{
'''+rules+'''
}
class SickenedCompat{
'''+body+'''
}
class ARGB{static int red(int c){return c>>>16&255;}static int green(int c){return c>>>8&255;}static int blue(int c){return c&255;}static int colorFromFloat(float a,float r,float g,float b){return Math.round(a*255)<<24|Math.round(r*255)<<16|Math.round(g*255)<<8|Math.round(b*255);}}
class Mth{static float clamp(float v,float a,float b){return Math.max(a,Math.min(b,v));}static float lerp(float a,float b,float c){return b+a*(c-b);}}
class SickenedPersonSpawnEggItem extends Item{SickenedPersonSpawnEggItem(String name){super("mca",name);}}
class ItemsMCA{static Map<Identifier,Item>ITEMS=new LinkedHashMap<>();static String baseProps(String name){return name;}
'''+register_items+'''
}
class Infirmary{
'''+infirmary_sick+'''
static boolean needsCare(VillagerEntityMCA p){return sick(p);}
}
class Palette{
'''+purple+'''
}
class Visual{float sicknessProgress,infectionProgress;
'''+visual+'''
}
public class Checks{
 static int tests;static void check(boolean ok,String n){if(!ok)throw new AssertionError(n);tests++;}
 static float p(VillagerLike<?> v){return v.getTrackedValue(VillagerLike.SICKNESS_PROGRESS);}
 static float z(VillagerLike<?> v){return v.getTrackedValue(VillagerLike.SICKNESS_ZOMBIE_PROGRESS);}
 static void enabled(boolean v){MCA.platformHelper.installed=v;}
 public static void main(String[]args){
  enabled(true);check(SickenedCompat.sanitize(Float.NaN)==0,"NaN rejected");check(SickenedCompat.sanitize(Float.POSITIVE_INFINITY)==0,"infinity rejected");check(SickenedCompat.sanitize(-1)==0,"negative clamped");check(SickenedCompat.sanitize(2)==1,"overrange clamped");
  check(SickenedCompat.advance(.9F,10)==1,"infection threshold");check(SickenedCompat.advance(.1F,0)==1,"zero-duration bounded");
  VillagerEntityMCA v=new VillagerEntityMCA();v.zombieInfection=.4F;SickenedCompat.setProgress(v,.3F);check(p(v)==.3F && v.zombieInfection==.4F,"dual infections independent");check(SickenedCompat.sick(v),"pending sickness colors name");
  enabled(false);check(SickenedCompat.progress(v)==0 && !SickenedCompat.sick(v),"absent mod hides disease");SickenedCompat.tick(v);check(p(v)==0 && v.zombieInfection==.4F,"uninstall clears only pending sickness");
  enabled(true);SickenedCompat.setProgress(v,.5F);v.tickCount=1;SickenedCompat.tick(v);check(p(v)==.5F,"progress only on infection tick");v.tickCount=20;SickenedCompat.tick(v);check(Math.abs(p(v)-.6F)<.0001,"progress gradual");check(v.converted==null,"pending sickness does not immediately convert");
  SickenedCompat.setProgress(v,.95F);SickenedCompat.tick(v);check(v.converted!=null,"full sickness uses MCA conversion");check(v.converted.tags.contains(SickenedCompat.TAG) && p(v.converted)==1,"converted person marked sickened");check(v.converted.id.equals(v.id),"conversion retains identity");check(z(v.converted)==.4F,"existing zombie infection survives sickness turn");
  ZombieVillagerEntityMCA sick=new ZombieVillagerEntityMCA();SickenedCompat.mark(sick);Zombie attacker=new Zombie();SickenedCompat.onHurt(sick,new DamageSource(attacker));check(z(sick)==.001F && sick.tags.contains(SickenedCompat.TAG),"ordinary zombie starts staged reverse infection");sick.setTrackedValue(VillagerLike.SICKNESS_ZOMBIE_PROGRESS,.99F);SickenedCompat.tick(sick);check(!sick.tags.contains(SickenedCompat.TAG) && p(sick)==0,"reverse infection ends as ordinary zombie");
  Zombie ordinary=new Zombie();WitherSkeleton wither=new WitherSkeleton();SickenedCompat.onHurt(ordinary,new DamageSource(wither));check(ordinary.tags.stream().anyMatch(t->t.startsWith("mca_sickness:")) && !ordinary.tags.contains(SickenedCompat.TAG),"ordinary zombies get pending sickness");Config.c.infectionTime=1;SickenedCompat.tick(ordinary);check(ordinary.tags.contains(SickenedCompat.TAG),"ordinary zombie completes sickness");Config.c.infectionTime=10;
  VillagerEntityMCA normal=new VillagerEntityMCA();normal.world.difficulty=Difficulty.NORMAL;normal.random.roll=.9F;SickenedCompat.onHurt(normal,new DamageSource(wither));check(p(normal)==0,"Normal unsuccessful infection roll");normal.random.roll=.01F;SickenedCompat.onHurt(normal,new DamageSource(wither));check(p(normal)>0,"Normal successful infection roll");
  VillagerEntityMCA easy=new VillagerEntityMCA();easy.world.difficulty=Difficulty.EASY;SickenedCompat.onHurt(easy,new DamageSource(wither));check(p(easy)>0,"Easy follows same infection rules as zombie bites");check(easy.messages==1,"new sickness announces infection");SickenedCompat.onHurt(easy,new DamageSource(wither));check(easy.messages==1,"repeat exposure does not repeat announcement");check(normal.messages==1,"failed roll is silent and successful roll announces");
  VillagerEntityMCA immune=new VillagerEntityMCA();immune.villagerData.p.profession=ProfessionsMCA.GUARD;SickenedCompat.onHurt(immune,new DamageSource(wither));check(p(immune)==0 && immune.messages==0,"guards resist sickness using shared zombie rules");immune.villagerData.p.profession=null;immune.villagerData.rank=5;SickenedCompat.onHurt(immune,new DamageSource(wither));check(p(immune)==0,"level resistance shared with zombie infection");immune.villagerData.rank=1;Config.c.enableInfection=false;SickenedCompat.onHurt(immune,new DamageSource(wither));check(p(immune)==0 && immune.messages==0,"disabled infections remain silent");Config.c.enableInfection=true;
  VillagerEntityMCA cured=new VillagerEntityMCA();cured.zombieInfection=.7F;SickenedCompat.setProgress(cured,.5F);ServerPlayer player=new ServerPlayer();Item stew=new Item("golden_apple_stew");ItemStack stack=new ItemStack(stew,3);check(SickenedCompat.cureGift(player,cured,stack),"existing external stew accepted as gift");check(p(cured)==0 && cured.zombieInfection==.7F,"stew cures sickness without curing zombie infection");check(stack.count==2 && player.inventory.bowls==1,"one stew consumed and one bowl returned");
  SickenedCompat.setProgress(cured,.2F);ItemStack wrong=new ItemStack(new Item("minecraft","golden_apple"));check(!SickenedCompat.cureGift(player,cured,wrong) && p(cured)==.2F,"golden apple does not cure sickness");player.abilities.instabuild=true;ItemStack creative=new ItemStack(stew);check(SickenedCompat.cureGift(player,cured,creative) && creative.count==1 && player.inventory.bowls==1,"Creative cure does not consume or duplicate bowl");
  SickenedCompat.mark(sick);sick.tags.add("sickened_cure:100");enabled(false);SickenedCompat.tick(sick);check(!sick.tags.contains(SickenedCompat.TAG) && sick.tags.stream().noneMatch(t->t.startsWith("sickened_cure:")) && p(sick)==0 && !sick.strength,"uninstall clears full disease and cure state");check(sick instanceof Zombie,"uninstalled sickened person remains zombie");
  enabled(true);VillagerEntityMCA fullLiving=new VillagerEntityMCA();SickenedCompat.mark(fullLiving);enabled(false);SickenedCompat.tick(fullLiving);check(fullLiving.converted!=null && p(fullLiving.converted)==0 && !fullLiving.converted.tags.contains(SickenedCompat.TAG),"uninstall converts full sickened living person to clean zombie");
  enabled(true);ZombieVillagerEntityMCA from=new ZombieVillagerEntityMCA();SickenedCompat.mark(from);from.tags.add("sickened_cure:100");from.setTrackedValue(VillagerLike.SICKNESS_ZOMBIE_PROGRESS,.25F);ZombieVillagerEntityMCA to=new ZombieVillagerEntityMCA();SickenedCompat.transfer(from,to);check(to.tags.contains(SickenedCompat.TAG) && to.tags.contains("sickened_cure:100") && z(to)==.25F,"conversion preserves sickness cure timer and opposing infection");
  from.tags.remove(SickenedCompat.TAG);VillagerEntityMCA healthy=new VillagerEntityMCA();healthy.setTrackedValue(VillagerLike.SICKENED,true);healthy.setTrackedValue(VillagerLike.SICKNESS_PROGRESS,1F);SickenedCompat.afterZombieCure(from,healthy);check(p(healthy)==0 && !healthy.getTrackedValue(VillagerLike.SICKENED),"external timed cure does not re-infect restored person");
  Item flesh=new Item("withered_flesh");BuiltInRegistries.ITEM.items.put(flesh.id,flesh);SickenedCompat.mark(to);SickenedCompat.dropFlesh(to,to.level());check(to.fleshDrops>=1 && to.fleshDrops<=2,"sickened person drops existing withered flesh");enabled(false);int dropped=to.fleshDrops;SickenedCompat.dropFlesh(to,to.level());check(to.fleshDrops==dropped,"absent mod adds no drops");
  Item vanillaEgg=new Item("sickened_villager_spawn_egg");BuiltInRegistries.ITEM.items.put(vanillaEgg.id,vanillaEgg);Map<Identifier,Item>registered=new HashMap<>();enabled(false);ItemsMCA.registerItems(registered::put);check(registered.isEmpty(),"absent mod registers no MCA sickened egg");enabled(true);ItemsMCA.registerItems(registered::put);Identifier personEggId=MCA.locate("sickened_person_spawn_egg");check(registered.size()==1 && registered.containsKey(personEggId),"present mod registers distinct MCA person egg");check(registered.get(personEggId)!=vanillaEgg && !personEggId.equals(vanillaEgg.id),"Person and vanilla Villager eggs remain different items");check(BuiltInRegistries.ITEM.items.get(vanillaEgg.id)==vanillaEgg,"external villager egg remains untouched");int size=ItemsMCA.ITEMS.size();ItemsMCA.registerItems(registered::put);check(ItemsMCA.ITEMS.size()==size,"repeated registration creates no duplicate person egg");ItemsMCA.ITEMS.clear();registered.clear();enabled(false);ItemsMCA.registerItems(registered::put);check(registered.isEmpty(),"restart without optional mod exposes no give or Creative egg ID");
  int healthyColor=0xFFCCAA88;check(Palette.applyPurpleShift(healthyColor,0)==healthyColor,"zero sickness keeps skin color");int purpleColor=Palette.applyPurpleShift(healthyColor,1);check(ARGB.blue(purpleColor)>ARGB.red(purpleColor) && ARGB.red(purpleColor)>ARGB.green(purpleColor),"full sickness is purple rather than green");check(Palette.applyPurpleShift(healthyColor,.2F)!=healthyColor && Palette.applyPurpleShift(healthyColor,.2F)!=purpleColor,"purple appearance progresses gradually");Visual visual=new Visual();visual.infectionProgress=1;check(visual.skinInfectionProgress()==1,"ordinary zombies retain green palette");visual.sicknessProgress=.5F;check(visual.skinInfectionProgress()==-.5F,"sickness selects separate purple palette with co-infection");visual.sicknessProgress=0;check(visual.skinInfectionProgress()==1,"curing sickness restores ordinary zombie appearance");
  enabled(true);VillagerEntityMCA pending=new VillagerEntityMCA();SickenedCompat.setProgress(pending,.3F);check(Infirmary.needsCare(pending),"pending sickness qualifies for infirmary care");enabled(false);check(!Infirmary.needsCare(pending),"absent mod adds no sickness-only infirmary admission");SickenedCompat.tick(pending);check(p(pending)==0 && pending.converted==null,"uninstall cures unconverted patient without zombifying them");pending.zombieInfection=.4F;check(Infirmary.needsCare(pending),"ordinary zombie infection care remains available");VillagerEntityMCA taggedLiving=new VillagerEntityMCA();taggedLiving.tags.add(SickenedCompat.TAG);SickenedCompat.tick(taggedLiving);check(taggedLiving.converted!=null && !taggedLiving.converted.tags.contains(SickenedCompat.TAG),"full legacy sickness tag becomes clean zombie after uninstall");
  enabled(true);VillagerEntityMCA speaker=new VillagerEntityMCA();check(!speaker.isSpeechImpaired(),"healthy speech unchanged");SickenedCompat.setProgress(speaker,.5F);check(!speaker.isSpeechImpaired(),"early sickness retains clear speech");SickenedCompat.setProgress(speaker,.7F);check(speaker.isSpeechImpaired(),"advanced sickness uses zombie gibberish");SickenedCompat.mark(speaker);speaker.setTrackedValue(VillagerLike.SICKNESS_PROGRESS,0F);check(speaker.isSpeechImpaired(),"full sickened flag retains gibberish without zombie infection");enabled(false);check(!speaker.isSpeechImpaired(),"absent optional mod adds no sickness gibberish");speaker.zombieInfection=1;check(speaker.isSpeechImpaired(),"ordinary zombie infection still uses gibberish");
  System.out.println("PASS: "+tests+" production sickness, cure, cross-infection, uninstall drop, item-reuse and purple-palette checks");
 }
}
'''
java_home=os.environ.get('JAVA_HOME')
if not java_home:
 choices=list((Path.home()/'.gradle/jdks').glob('*/jdk-25*/Contents/Home'))
 if choices:java_home=str(choices[0])
binpath=Path(java_home)/'bin' if java_home else None
with tempfile.TemporaryDirectory(prefix='mca-sickened-') as temp:
 file=Path(temp)/'Checks.java';file.write_text(code)
 subprocess.run([str(binpath/'javac') if binpath else shutil.which('javac'),str(file)],check=True)
 subprocess.run([str(binpath/'java') if binpath else shutil.which('java'),'-cp',temp,'Checks'],check=True)
