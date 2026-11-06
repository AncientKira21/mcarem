#!/usr/bin/env python3
"""Execute the actual loader renderer-registration bodies against all registered MCA entity IDs."""
from pathlib import Path
import re,tempfile,subprocess,os
root=Path(__file__).resolve().parents[1]
registry=(root/'common/src/main/java/com/ancientkira/mca/registry/EntitiesMCA.java').read_text()
names=set(re.findall(r'EntityType<[^\n]+>\s+([A-Z_]+)\s*=\s*register\(',registry))
assert {'SICKENED_PERSON','SICKENED_VILLAGER'}<=names
bodies={};renderers=set()
for loader,file,call in [('fabric','fabric/MCAFabricClient.java','EntityRenderers.register'),('neoforge','neoforge/ClientNeoForge.java','event.registerEntityRenderer')]:
 text=(root/loader/'src/main/java/com/ancientkira/mca'/file).read_text()
 a=text.index('if (Config.getInstance().useSquidwardModels)')
 b=text.index(';',text.index(call+'(EntitiesMCA.RAIDER_GOLEM',a))+1
 body=text[a:b]
 for name in re.findall(r'([\w.]+)::new',body):
  short=name.rsplit('.',1)[-1];renderers.add(short);body=body.replace(name+'::new',short+'::new')
 bodies[loader]=body
fields=''.join('static String '+n+'="'+n+'";' for n in sorted(names))
stubs=''.join('class '+n+'{'+n+'(Object context){}}\n' for n in sorted(renderers))
expected=','.join('"'+n+'"' for n in sorted(names))
code='''import java.util.*;import java.util.function.Function;
class Config{boolean useSquidwardModels;static Config instance=new Config();static Config getInstance(){return instance;}}
class EntitiesMCA{'''+fields+'''}
class Event{Map<String,Function<Object,Object>> renderers=new HashMap<>();void registerEntityRenderer(String id,Function<Object,Object> factory){if(renderers.put(id,factory)!=null)throw new AssertionError("duplicate binding "+id);}}
class EntityRenderers{static Event current;static void register(String id,Function<Object,Object> factory){current.registerEntityRenderer(id,factory);}}
'''+stubs+'''
public class Checks{
 static int checks;static void check(boolean b,String n){if(!b)throw new AssertionError(n);checks++;}
 static void fabric(){
'''+bodies['fabric']+'''
 }
 static void neoforge(Event event){
'''+bodies['neoforge']+'''
 }
 public static void main(String[]args){
  Set<String> expected=Set.of('''+expected+''');
  for(boolean vanilla:new boolean[]{true,false})for(boolean fabric:new boolean[]{true,false}){
   Config.instance.useSquidwardModels=vanilla;Event event=new Event();EntityRenderers.current=event;
   if(fabric)fabric();else neoforge(event);
   check(event.renderers.keySet().equals(expected),"complete renderer coverage for loader/model setting");
   for(String id:expected)check(event.renderers.get(id).apply(null)!=null,"non-null renderer for "+id);
   String wanted=vanilla?"ZombieVillagerRenderer":"ZombieVillagerEntityMCARenderer";
   check(event.renderers.get("SICKENED_PERSON").apply(null).getClass().getSimpleName().equals(wanted),"sickened person uses selected villager model");
   check(event.renderers.get("SICKENED_VILLAGER").apply(null).getClass().getSimpleName().equals(wanted),"legacy sickened alias has renderer");
  }
  System.out.println("PASS: "+checks+" production renderer registration checks across "+expected.size()+" entity IDs, both loaders and model settings");
 }
}
'''
homes=list((Path.home()/'.gradle/jdks').glob('*/jdk-25*/Contents/Home'));java=Path(os.environ.get('JAVA_HOME',str(homes[0])))/'bin'
with tempfile.TemporaryDirectory(prefix='mca-renderers-') as d:
 p=Path(d)/'Checks.java';p.write_text(code);subprocess.run([str(java/'javac'),str(p)],check=True);subprocess.run([str(java/'java'),'-cp',d,'Checks'],check=True)
