#!/usr/bin/env python3
"""Compile and exercise production height, errand-completion and permission methods without launching Minecraft."""
from pathlib import Path
import subprocess, tempfile, shutil, os
ROOT=Path(__file__).resolve().parents[1]/'common/src/main/java/com/ancientkira/mca'
def method(file, signature):
    text=(ROOT/file).read_text();start=text.index(signature);brace=text.index('{',start);depth=1;end=brace+1
    while depth:
        depth+=(text[end]=='{')-(text[end]=='}');end+=1
    return text[start:end]
methods='\n'.join([
    method('server/world/VillageStreetGravityProcessor.java','public static int surface('),
    method('server/PortalPursuit.java','public static boolean taskEnded('),
    method('server/HorseHelp.java','public static boolean permissionMatches('),
])
java='''import java.util.*;
public class Checks {
 static class Block {} static class SlabBlock extends Block {}
 static class Blocks { static Block DIRT_PATH=new Block(), WATER=new Block(); }
 static class BlockTags { static String LEAVES="leaves"; }
 static class Heightmap { enum Types { WORLD_SURFACE_WG } }
 static class BlockPos {
  int y; BlockPos(int x,int y,int z){this.y=y;} int getY(){return y;}
  static class MutableBlockPos extends BlockPos { MutableBlockPos(int x,int y,int z){super(x,y,z);} void move(int x,int y,int z){this.y+=y;} }
 }
 static class State {
  Block block;boolean full,leaves;State(Block b,boolean f){block=b;full=f;} State leaf(){leaves=true;return this;}
  boolean is(Block b){return block==b;} boolean is(String tag){return leaves;}
  Block getBlock(){return block;}boolean isCollisionShapeFullBlock(LevelReader w,BlockPos p){return full;}
 }
 static class LevelReader {
  State[] column;LevelReader(State... c){column=c;}int getHeight(Heightmap.Types t,int x,int z){return column.length;}
  int getMinY(){return 0;}State getBlockState(BlockPos p){return column[p.y];}
 }
 static int checks;static void check(boolean ok,String name){if(!ok)throw new AssertionError(name);checks++;}
 static State full(){return new State(new Block(),true);}static State partial(){return new State(new Block(),false);}
'''+methods+'''
 public static void main(String[] args) {
  check(surface(new LevelReader(full()),0,0)==1,"bare grass block");
  check(surface(new LevelReader(full(),partial()),0,0)==1,"short grass");
  check(surface(new LevelReader(full(),partial(),partial()),0,0)==1,"tall grass");
  check(surface(new LevelReader(full(),new State(new SlabBlock(),false)),0,0)==1,"single slab");
  check(surface(new LevelReader(full(),new State(new SlabBlock(),true)),0,0)==1,"double slab also excluded");
  check(surface(new LevelReader(full(),partial()),0,0)==1,"stairs/flowers excluded");
  check(surface(new LevelReader(full(),full()),0,0)==2,"real terrain elevation retained");
  check(surface(new LevelReader(full(),full().leaf()),0,0)==1,"leaves excluded");
  check(surface(new LevelReader(full(),new State(Blocks.DIRT_PATH,false)),0,0)==2,"existing road retained");
  check(surface(new LevelReader(full(),new State(Blocks.WATER,false)),0,0)==2,"bridge waterline retained");
  check(!taskEnded(false,false,0,100),"active combat");
  check(taskEnded(true,false,0,100),"dead target returns");
  check(!taskEnded(false,true,100,100),"target loading grace");
  check(taskEnded(false,true,101,100),"lost target returns");
  check(!taskEnded(false,false,0,6000),"task duration boundary");
  check(taskEnded(false,false,0,6001),"bounded task returns");
  UUID owner=UUID.randomUUID(), stranger=UUID.randomUUID();
  check(permissionMatches(owner,owner,1200,0),"owner approval");
  check(permissionMatches(owner,owner,1200,1200),"expiry boundary");
  check(!permissionMatches(owner,owner,1200,1201),"expired approval");
  check(!permissionMatches(owner,stranger,1200,0),"another player cannot approve");
  check(!permissionMatches(null,owner,1200,0),"missing owner");
  check(!permissionMatches(owner,null,1200,0),"missing responder");
  System.out.println("PASS: "+checks+" production road-height, portal-task and horse-permission checks");
 }
}
'''
home=os.environ.get('JAVA_HOME')
if not home:
    choices=list((Path.home()/'.gradle/jdks').glob('*/jdk-25*/Contents/Home'))
    if choices:home=str(choices[0])
javac=str(Path(home)/'bin/javac') if home else shutil.which('javac')
java_bin=str(Path(home)/'bin/java') if home else shutil.which('java')
with tempfile.TemporaryDirectory(prefix='mca-road-portal-horse-') as temp:
    file=Path(temp)/'Checks.java';file.write_text(java)
    subprocess.run([javac,str(file)],check=True)
    subprocess.run([java_bin,'-cp',temp,'Checks'],check=True)
