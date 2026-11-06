#!/usr/bin/env python3
"""Exercise production texture classification, category-tag precedence, and popup extraction."""
from pathlib import Path
import subprocess,tempfile
r=Path(__file__).resolve().parents[1]/'common/src/main/java/com/ancientkira/mca/client/gui'
def method(text,signature):
 a=text.index(signature);b=text.index('{',a);c=b+1;n=1
 while n:n+=(text[c]=='{')-(text[c]=='}');c+=1
 return text[a:c]
s=(r/'SkinLibraryScreen.java').read_text();enum=method(s,'    public enum SkinType ')
coverage=(r/'immersive_library/AccessoryTextureCoverage.java').read_text();coverage=coverage[coverage.index('public final class'):].replace('public final class','final class',1)
classic=(r/'immersive_library/ClassicSkinCoverage.java').read_text();classic=classic[classic.index('public final class'):].replace('public final class','final class',1)
overlay=method((r/'widget/DropdownWidget.java').read_text(),'    public static void extractOverlay(').replace('net.minecraft.client.gui.GuiGraphicsExtractor','Gui')
code='''import java.util.*;import java.util.function.IntBinaryOperator;
class Component{static Component translatable(String s){return new Component();}}
record LiteContent(Set<String> tags,int[][] pixels){boolean hasTag(String t){return tags.contains(t);}}
class SkinCache{
 static boolean isCompleteSkin(LiteContent c){return ClassicSkinCoverage.qualifies(c.pixels().length,c.pixels().length,(x,y)->c.pixels()[y][x]);}
 static boolean isEyeTexture(LiteContent c){return false;}
 static boolean coversOnly(LiteContent c,int r){return (AccessoryTextureCoverage.classify(c.pixels().length,c.pixels().length,(x,y)->c.pixels()[y][x])&r)!=0;}
}
'''+coverage+classic+'''
class Screen{}
class Gui{List<String> draws=new ArrayList<>();void nextStratum(){draws.add("stratum");}void fill(int a,int b,int c,int d,int color){draws.add("background");}}
class AbstractWidget{int getX(){return 0;}int getY(){return 0;}int getWidth(){return 10;}int getHeight(){return 20;}void extractRenderState(Gui g,int x,int y,float dt){g.draws.add("option");}}
class DropdownWidget<T>{static Map<Screen,DropdownWidget<?>> OPEN=new HashMap<>();List<AbstractWidget> options=new ArrayList<>();
'''+overlay+'''
}
public class Checks{
'''+enum+'''
 static int checks;static void check(boolean b,String s){checks++;if(!b)throw new AssertionError(s);}
 static LiteContent skin(int[][] p,String...tags){return new LiteContent(Set.of(tags),p);}
 public static void main(String[]args){
  for(int scale:new int[]{1,2,4}){
   int size=64*scale;int[][] p=new int[size][size];p[8*scale][8*scale]=255;
   check(SkinType.HAT.matches(skin(p)),"head-only untagged hat accepted");check(SkinType.GLASSES.matches(skin(p)),"upper-head untagged glasses accepted");
   check(SkinType.FACE.matches(skin(p)),"front face untagged accessory accepted");
   p[12*scale][8*scale]=255;check(SkinType.GLASSES.matches(skin(p)),"normal eye-line pixels accepted as upper face");
   p[12*scale][8*scale]=0;
   p[20*scale][20*scale]=255;
   for(SkinType type:new SkinType[]{SkinType.HAT,SkinType.GLASSES,SkinType.FACE})check(!type.matches(skin(p,type.tag())),"legacy tag cannot admit body pixels");
   p[20*scale][20*scale]=0;p[8*scale][8*scale]=0;p[0][8*scale]=255;
   check(SkinType.HAT.matches(skin(p)),"hat crown accepted");check(!SkinType.GLASSES.matches(skin(p,"glasses")),"hat crown rejected as glasses");check(!SkinType.FACE.matches(skin(p,"face")),"hat crown rejected as face");
   p[0][8*scale]=0;p[14*scale][8*scale]=255;
   check(SkinType.FACE.matches(skin(p)),"lower face accessory accepted");check(!SkinType.GLASSES.matches(skin(p,"glasses")),"lower face rejected as glasses");
   for(int[] row:p)Arrays.fill(row,255);
   check(SkinType.CLASSIC.matches(skin(p,"hat","hair")),"full-body legacy asset uses coverage despite tags");
   check(!SkinType.HAT.matches(skin(p,"hat")),"full-body asset rejected as hat");
   check(!SkinType.GLASSES.matches(skin(p,"glasses")),"full-body asset rejected as glasses");
   check(!SkinType.FACE.matches(skin(p,"face")),"full-body asset rejected as face");
   check(!SkinType.CLASSIC.matches(skin(p,"mcarem","hat")),"marker plus wrong category rejected despite coverage");
   check(SkinType.HAT.matches(skin(p,"mcarem","hat")),"marker and selected category are authoritative");
   check(!SkinType.HAT.matches(skin(p,"mcarem")),"marker alone insufficient");
   for(int[] row:p)Arrays.fill(row,0);p[8*scale][8*scale]=255;
   check(!SkinType.CLASSIC.matches(skin(p,"classic")),"unmarked classic tag alone insufficient");
   check(SkinType.CLASSIC.matches(skin(p,"mcarem","classic")),"marked classic uses both tags");
   for(int[] row:p)Arrays.fill(row,0);check(!SkinType.HAT.matches(skin(p)),"empty asset rejected");
  }
  Screen screen=new Screen();Gui graphics=new Gui();DropdownWidget<Object> menu=new DropdownWidget<>();menu.options.add(new AbstractWidget());menu.options.add(new AbstractWidget());DropdownWidget.OPEN.put(screen,menu);
  graphics.draws.add("preview");DropdownWidget.extractOverlay(screen,graphics,0,0,0);
  check(graphics.draws.equals(List.of("preview","stratum","background","option","option")),"popup is above preview in a new stratum");
  System.out.println("PASS: "+checks+" production category/coverage/dropdown checks");
 }
}
'''
java=next((Path.home()/'.gradle/jdks').glob('*/jdk-25*/Contents/Home'))/'bin/java'
with tempfile.TemporaryDirectory() as d:
 p=Path(d)/'Checks.java';p.write_text(code);subprocess.run([str(java),str(p)],check=True)
