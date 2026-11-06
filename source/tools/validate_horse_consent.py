from pathlib import Path
import subprocess,tempfile
s=(Path(__file__).resolve().parents[1]/'common/src/main/java/com/ancientkira/mca/server/HorseHelp.java').read_text()
def extract(sig):
 a=s.index(sig);b=s.index('{',a);d=1;e=b+1
 while d:d+=(s[e]=='{')-(s[e]=='}');e+=1
 return s[a:e]
methods=extract('public static int respond(')+'\n'+extract('public static boolean permissionMatches(')
code='''import java.util.*;
class Checks {
 static class MinecraftServer {}
 static class Level {MinecraftServer server=new MinecraftServer();Map<UUID,Object> entities=new HashMap<>();MinecraftServer getServer(){return server;}Object getEntity(UUID id){return entities.get(id);}String dimension(){return "overworld";}}
 static class ServerPlayer {UUID id;Level w;ServerPlayer(UUID id,Level w){this.id=id;this.w=w;}UUID getUUID(){return id;}Level level(){return w;}double distanceToSqr(Object p){return 0;}}
 enum MoveState {MOVE,STAY}
 static class Brain {MoveState getMoveState(){return MoveState.MOVE;}}
 static class VillagerEntityMCA {boolean passenger,kidnapped,baby;UUID id=UUID.randomUUID();boolean isPassenger(){return passenger;}boolean isKidnapped(){return kidnapped;}boolean isBaby(){return baby;}Brain getVillagerBrain(){return new Brain();}double distanceToSqr(Object p){return 0;}}
 static class Horse {Level w;UUID owner,id=UUID.randomUUID();boolean occupied;Horse(UUID owner,Level w){this.owner=owner;this.w=w;}boolean isTamed(){return true;}UUID getUUID(){return id;}Level level(){return w;}int blockPosition(){return 0;}}
 record GlobalPos(String dimension,int pos){static GlobalPos of(String d,int p){return new GlobalPos(d,p);}}
 record Request(UUID owner,UUID horse,long expires,java.lang.ref.WeakReference<MinecraftServer> server){}
 static class Loan {UUID owner,horse;GlobalPos origin,last;long issued,lastThreat;}
 static final Map<UUID,Request> REQUESTS=new HashMap<>();final Map<UUID,Loan> loans=new HashMap<>();
 static Checks DATA=new Checks();static long now;static boolean fast=true,saddleAvailable=true;static int saddles,mounts;
 static long time(Level w){return now;}static boolean available(Horse h){return !h.occupied;}
 static boolean ownedBy(Horse h,UUID id){return h.owner.equals(id);}static Object threat(VillagerEntityMCA p){return null;}
 static boolean tooFast(VillagerEntityMCA p,Object enemy){return fast;}
 static Checks get(MinecraftServer s){return DATA;}static boolean saddle(VillagerEntityMCA p,Horse h){if(!saddleAvailable)return false;saddles++;return true;}
 void setDirty(){}static void approach(VillagerEntityMCA p,Horse h){mounts++;}
'''+methods+'''
 static int checks;static void check(boolean b,String label){if(!b)throw new AssertionError(label);checks++;}
 public static void main(String[] args){
  Level w=new Level();UUID owner=UUID.randomUUID();ServerPlayer p=new ServerPlayer(owner,w),stranger=new ServerPlayer(UUID.randomUUID(),w);
  VillagerEntityMCA v=new VillagerEntityMCA();Horse h=new Horse(owner,w);w.entities.put(v.id,v);w.entities.put(h.id,h);
  Request req=new Request(owner,h.id,1200,new java.lang.ref.WeakReference<>(w.server));
  REQUESTS.put(v.id,req);check(respond(stranger,v.id,true)==0 && REQUESTS.containsKey(v.id) && saddles==0,"stranger cannot approve or consume request");
  check(respond(p,v.id,false)==1 && DATA.loans.isEmpty() && saddles==0 && mounts==0,"denial does not borrow or saddle");
  check(respond(p,v.id,true)==0,"denied link cannot be replayed");
  REQUESTS.put(v.id,req);now=1201;check(respond(p,v.id,true)==0 && saddles==0,"expired approval cannot consume saddle");now=0;
  REQUESTS.put(v.id,new Request(owner,h.id,1200,new java.lang.ref.WeakReference<>(new MinecraftServer())));check(respond(p,v.id,true)==0,"different server cannot approve");
  REQUESTS.put(v.id,req);h.owner=stranger.id;check(respond(p,v.id,true)==0 && saddles==0,"ownership rechecked at response");h.owner=owner;
  REQUESTS.put(v.id,req);h.occupied=true;check(respond(p,v.id,true)==0 && saddles==0,"occupied horse rejected");h.occupied=false;
  REQUESTS.put(v.id,req);fast=false;check(respond(p,v.id,true)==0 && saddles==0,"finished combat cannot borrow");fast=true;
  REQUESTS.put(v.id,req);saddleAvailable=false;check(respond(p,v.id,true)==0 && DATA.loans.isEmpty(),"missing saddle prevents loan");saddleAvailable=true;
  REQUESTS.put(v.id,req);check(respond(p,v.id,true)==1 && DATA.loans.containsKey(v.id) && saddles==1 && mounts==1 && h.owner.equals(owner),"owner grant produces one loan without changing owner");
  check(respond(p,v.id,true)==0 && saddles==1 && mounts==1,"approval replay cannot duplicate loan or saddle consumption");
  System.out.println("PASS: "+checks+" production horse-consent flow checks");
 }
}
'''
j=Path.home()/'.gradle/jdks/eclipse_adoptium-25-aarch64-os_x.2/jdk-25.0.4.1+1/Contents/Home/bin'
with tempfile.TemporaryDirectory() as t:
 p=Path(t)/'Checks.java';p.write_text(code);subprocess.run([str(j/'javac'),str(p)],check=True);subprocess.run([str(j/'java'),'-cp',t,'Checks'],check=True)
