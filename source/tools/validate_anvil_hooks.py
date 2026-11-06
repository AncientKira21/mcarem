#!/usr/bin/env python3
"""Check anvil injection points in real class files, without loading or launching Minecraft."""
from pathlib import Path
import argparse,os,re,subprocess
root=Path(__file__).resolve().parents[1]
parser=argparse.ArgumentParser();parser.add_argument('runtime_jars',nargs='*',type=Path);args=parser.parse_args()
source=(root/'common/src/main/java/com/ancientkira/mca/mixin/MixinStaffAnvil.java').read_text()
hooks=re.findall(r'@ModifyConstant\(method="([^"]+)",constant=@Constant\(intValue=4\),require=0(?:,remap=false)?\)',source)
assert set(hooks)=={'createResult()V','createResultInternal()V'},'Both loader layouts need optional individual hooks'
names={h.split('(')[0] for h in hooks}
homes=list((Path.home()/'.gradle/jdks').glob('*/jdk-25*/Contents/Home'));javap=Path(os.environ.get('JAVA_HOME',str(homes[0])))/'bin/javap'
jars=[]
for version in ('26.1.2','26.2','26.3'):
 choices=list((root/'.gradle/loom-cache/minecraftMaven/net/minecraft').glob('minecraft-merged-*/'+version+'/*.jar'))
 assert choices,version
 jars.append(max(choices,key=lambda p:p.stat().st_mtime))
jars+=args.runtime_jars
for jar in jars:
 output=subprocess.run([str(javap),'-p','-c','-classpath',str(jar),'net.minecraft.world.inventory.AnvilMenu'],check=True,text=True,capture_output=True).stdout
 counts={};method=None
 for line in output.splitlines():
  header=re.match(r'^  (?:public|protected|private).*\s([\w$]+)\([^)]*\);$',line)
  if header:method=header.group(1)
  if method in names and re.search(r'\biconst_4\b',line):counts[method]=counts.get(method,0)+1
 assert sum(counts.values())==2,(jar,counts,'expected both material-repair divisions in supported recipe body')
 print('PASS:',jar.name,counts)
print('PASS: both optional anvil hooks cover all',len(jars),'real Minecraft class layouts; no game launched')
