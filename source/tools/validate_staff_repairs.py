#!/usr/bin/env python3
"""Exercise production Staff of Life recipes and actual grindstone slot wrappers without Minecraft."""
from pathlib import Path
import os,subprocess,tempfile
root=Path(__file__).resolve().parents[1]
base=root/'common/src/main/java/com/ancientkira/mca'
s=(base/'item/StaffRepairs.java').read_text();repair=s[s.index('    private StaffRepairs()'):s.rfind('}')]
def method(text,signature):
 i=text.index(signature);a=text.index('{',i);n=1;b=a+1
 while n:n+=(text[b]=='{')-(text[b]=='}');b+=1
 return text[i:b]
s=(base/'mixin/MixinStaffGrindstone.java').read_text()
slots=method(s,'private void mca$repairSlots(')
result=method(s,'private void mca$repairWithStar(')
slot_source=(base/'item/StaffRepairSlot.java').read_text()
slot_body=slot_source[slot_source.index('    private final Slot original;'):slot_source.rfind('}')]
code='''import java.util.*;
class Item{}
class StaffOfLifeItem extends Item{}
class Items{static Item NETHER_STAR=new Item(),DIAMOND=new Item();}
class ItemStack{
 static final ItemStack EMPTY=new ItemStack(new Item(),0,0,0);Item item;int count,damage,max;boolean enchanted,curse;String name="Custom staff";int repairCost=7;
 ItemStack(Item i,int c,int d,int m){item=i;count=c;damage=d;max=m;}boolean is(Item i){return item==i;}boolean isEmpty(){return count<=0;}Item getItem(){return item;}int getCount(){return count;}boolean isDamaged(){return damage>0&&max>0;}int getDamageValue(){return damage;}int getMaxDamage(){return max;}void setDamageValue(int d){damage=d;}ItemStack copy(){return copyWithCount(count);}ItemStack copyWithCount(int n){ItemStack s=new ItemStack(item,n,damage,max);s.enchanted=enchanted;s.curse=curse;s.name=name;s.repairCost=repairCost;return s;}
}
class StaffRepairs{
'''+repair+'''
}
class Container{ItemStack[]items={ItemStack.EMPTY,ItemStack.EMPTY};ItemStack getItem(int i){return items[i];}void setItem(int i,ItemStack s){items[i]=s;}}
class Player{}
class Inventory{}
class ContainerLevelAccess{}
class CallbackInfo{}
class CallbackInfoReturnable<T>{T result;void setReturnValue(T t){result=t;}}
class Slot{Container container;int index,x,y,slot;Slot(Container c,int i,int x,int y){container=c;slot=i;this.x=x;this.y=y;}int getContainerSlot(){return slot;}boolean mayPlace(ItemStack s){return s.max>0||s.enchanted;}boolean mayPickup(Player p){return true;}void onTake(Player p,ItemStack s){}}
class GrindstoneMenu{
 Container repairSlots=new Container(),results=new Container();List<Slot> slots=new ArrayList<>();int takes,xp;boolean cancel;
 GrindstoneMenu(){slots.add(new Slot(repairSlots,0,49,19));slots.add(new Slot(repairSlots,1,49,40));slots.add(new Slot(results,0,129,34){@Override boolean mayPlace(ItemStack s){return false;}@Override void onTake(Player p,ItemStack out){takes++;if(cancel)return;if(repairSlots.getItem(0).enchanted||repairSlots.getItem(1).enchanted)xp++;repairSlots.setItem(0,ItemStack.EMPTY);repairSlots.setItem(1,ItemStack.EMPTY);}});for(int i=0;i<3;i++)slots.get(i).index=i;}
}
class StaffRepairSlot extends Slot {
'''+slot_body+'''
}
class Mix extends GrindstoneMenu{
'''+slots+'\n'+result+'''
 private ItemStack removeNonCursesFrom(ItemStack s){s.enchanted=false;if(!s.curse)s.repairCost=0;return s;}
 Mix(){mca$repairSlots(0,new Inventory(),new ContainerLevelAccess(),new CallbackInfo());}
 ItemStack compute(){CallbackInfoReturnable<ItemStack> cir=new CallbackInfoReturnable<>();mca$repairWithStar(repairSlots.getItem(0),repairSlots.getItem(1),cir);return cir.result;}
}
public class Checks{
 static int checks;static void check(boolean b,String name){if(!b)throw new AssertionError(name);checks++;}
 static ItemStack staff(int damage){return new ItemStack(new StaffOfLifeItem(),1,damage,10);}static ItemStack stars(int n){return new ItemStack(Items.NETHER_STAR,n,0,0);}
 public static void main(String[]args){
  var s=staff(9);var stars=stars(64);check(StaffRepairs.starSlot(s,stars)==1,"staff and star recognized");check(StaffRepairs.starSlot(stars,s)==0,"either slot order supported");var repaired=StaffRepairs.repair(s,stars);check(repaired.damage==0,"one star fully restores the staff");check(s.damage==9&&stars.count==64,"preview does not consume inputs");check(repaired.name.equals(s.name)&&repaired.repairCost==s.repairCost,"copy preserves staff metadata");
  check(StaffRepairs.starSlot(staff(0),stars)<0,"full staff rejects material waste");check(StaffRepairs.starSlot(s,new ItemStack(Items.DIAMOND,1,0,0))<0,"wrong material rejected");check(StaffRepairs.starSlot(s,stars(0))<0,"empty material rejected");check(StaffRepairs.starSlot(new ItemStack(Items.DIAMOND,1,5,10),stars)<0,"other tools unchanged");var stacked=staff(5);stacked.count=2;check(StaffRepairs.starSlot(stacked,stars)<0,"stacked damaged staffs rejected");check(StaffRepairs.repair(staff(1),stars).damage==0,"partial final repair clamps to full durability");
  for(int starSlot=0;starSlot<2;starSlot++)for(int count:new int[]{1,2,64}){
   Mix menu=new Mix();ItemStack tool=staff(8);tool.enchanted=true;tool.curse=true;menu.repairSlots.setItem(starSlot,stars(count));menu.repairSlots.setItem(1-starSlot,tool);
   check(menu.slots.get(starSlot).mayPlace(stars(count)),"material accepted in native slots");check(!menu.slots.get(2).mayPlace(stars(count)),"result slot still rejects insertion");check(menu.slots.get(0).index==0&&menu.slots.get(2).index==2,"menu slot indices retained");
   ItemStack output=menu.compute();check(output.damage==0&&!output.enchanted&&output.curse,"repair applies native disenchanting while preserving curse");check(menu.repairSlots.getItem(starSlot).count==count&&tool.enchanted,"preview leaves input enchantments and materials unchanged");
   menu.slots.get(2).onTake(new Player(),ItemStack.EMPTY);check(menu.repairSlots.getItem(starSlot).count==count-1,"take consumes exactly one star including shift-click");check(menu.repairSlots.getItem(1-starSlot).isEmpty(),"take consumes original staff");check(menu.takes==1&&menu.xp==1,"native take/XP invoked exactly once");
  }
  check(StaffRepairs.anvilDivisor(4,s,stars)==1,"anvil repairs entire durability with one star");check(StaffRepairs.anvilDivisor(4,stars,s)==4,"anvil keeps normal slot ordering");check(StaffRepairs.anvilDivisor(4,s,new ItemStack(Items.DIAMOND,1,0,0))==4,"wrong anvil material unaffected");check(StaffRepairs.anvilDivisor(4,new ItemStack(Items.DIAMOND,1,5,10),stars)==4,"other anvil tools retain vanilla repair amount");
  int damage=9,used=0;while(damage>0&&used<64){damage-=Math.min(damage,10/StaffRepairs.anvilDivisor(4,s,stars));used++;}check(damage==0&&used==1,"native anvil loop consumes only one star from full stack");
  Mix cancelled=new Mix();cancelled.repairSlots.setItem(0,staff(5));cancelled.repairSlots.setItem(1,stars(4));cancelled.cancel=true;cancelled.slots.get(2).onTake(new Player(),ItemStack.EMPTY);check(cancelled.repairSlots.getItem(1).count==4&&!cancelled.repairSlots.getItem(0).isEmpty(),"loader-handled cancellation leaves inputs unchanged");
  Mix normal=new Mix();normal.repairSlots.setItem(0,staff(5));normal.repairSlots.setItem(1,staff(5));check(normal.compute()==null,"normal two-tool recipe is not intercepted");normal.slots.get(2).onTake(new Player(),ItemStack.EMPTY);check(normal.repairSlots.getItem(0).isEmpty()&&normal.repairSlots.getItem(1).isEmpty(),"normal grindstone consumption unchanged");
  System.out.println("PASS: "+checks+" production staff repair/menu checks");
 }
}
'''
homes=list((Path.home()/'.gradle/jdks').glob('*/jdk-25*/Contents/Home'));java=Path(os.environ.get('JAVA_HOME',str(homes[0])))/'bin'
with tempfile.TemporaryDirectory(prefix='mca-staff-repair-') as d:
 p=Path(d)/'Checks.java';p.write_text(code);subprocess.run([str(java/'javac'),str(p)],check=True);subprocess.run([str(java/'java'),'-cp',d,'Checks'],check=True)
