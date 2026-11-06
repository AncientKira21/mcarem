#!/usr/bin/env python3
"""Queue production collision-refresh packets against a reused mutable position iterator."""
from pathlib import Path
import subprocess,tempfile
root=Path(__file__).resolve().parents[1]
s=(root/'common/src/main/java/com/ancientkira/mca/server/CollisionVisibilityRepair.java').read_text()
a=s.index('    public static void tick(');s=s[a:s.rfind('}')]
code='''import java.util.*;
class BlockPos {int x,y,z;BlockPos(int a,int b,int c){x=a;y=b;z=c;}BlockPos offset(int a,int b,int c){return new BlockPos(x+a,y+b,z+c);}BlockPos immutable(){return new BlockPos(x,y,z);}String key(){return x+","+y+","+z;}
 static Iterable<BlockPos> betweenClosed(BlockPos lo,BlockPos hi){return ()->new Iterator<>(){int index;BlockPos reused=new BlockPos(0,0,0);int width=hi.x-lo.x+1,depth=hi.z-lo.z+1,total=width*depth*(hi.y-lo.y+1);public boolean hasNext(){return index<total;}public BlockPos next(){int i=index++;reused.x=lo.x+i%width;reused.z=lo.z+(i/width)%depth;reused.y=lo.y+i/(width*depth);return reused;}};}}
class World {long getGameTime(){return 10;}boolean hasChunkAt(BlockPos p){return true;}String state(BlockPos p){return p.y<0?"grass":"air";}}
class ClientboundBlockUpdatePacket{BlockPos pos;String state;ClientboundBlockUpdatePacket(World w,BlockPos p){pos=p;state=w.state(p);}}
class Connection{List<ClientboundBlockUpdatePacket> queued=new ArrayList<>();void send(ClientboundBlockUpdatePacket p){queued.add(p);}}
class Abilities{boolean flying;}
class ServerPlayer{World world=new World();Connection connection=new Connection();boolean isPassenger(){return false;}boolean isSpectator(){return false;}Abilities getAbilities(){return new Abilities();}boolean isInWater(){return false;}boolean isInLava(){return false;}boolean onClimbable(){return false;}boolean onGround(){return true;}boolean horizontalCollision,verticalCollision;World level(){return world;}double getX(){return 0;}double getY(){return 0;}double getZ(){return 0;}BlockPos blockPosition(){return new BlockPos(0,0,0);}}
class Tracker{boolean shouldRefresh(Object p,long t,double x,double y,double z,boolean collision){return true;}}
public class Checks{static Tracker TRACKER=new Tracker();
'''+s+'''
 public static void main(String[]args){var player=new ServerPlayer();tick(player);Set<String> positions=new HashSet<>();int grass=0;
 for(var packet:player.connection.queued){if(!packet.state.equals(player.world.state(packet.pos)))throw new AssertionError("queued block state moved to a different position");positions.add(packet.pos.key());if(packet.state.equals("grass")){grass++;if(packet.pos.y>=0)throw new AssertionError("floating grass created");}}
 if(positions.size()!=36||grass!=9)throw new AssertionError("incorrect refresh volume");
 System.out.println("PASS: 36 delayed packets retain distinct positions and correct ground/air states");
 }
}
'''
java=next((Path.home()/'.gradle/jdks').glob('*/jdk-25*/Contents/Home'))/'bin/java'
with tempfile.TemporaryDirectory() as d:
 p=Path(d)/'Checks.java';p.write_text(code);subprocess.run([str(java),str(p)],check=True)
