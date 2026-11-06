#!/usr/bin/env python3
"""Generate MCA-owned underwater structures from vanilla connection and furnishing metadata.
Usage: python3 tools/generate_underwater_village.py --minecraft-jar /path/to/26.1.2.jar
Uses only Python's standard library. No air-to-water conversion is performed.
"""
import argparse,copy,json,re,zipfile
from pathlib import Path
from structure_nbt import Reader,encode,value,pos
ROOT=Path(__file__).resolve().parents[1]/'common/src/main/resources/data/mca'
S=lambda s:(8,s)
L=lambda values:(9,(3,list(values)))
def state(name,**props):
 result={'Name':S(name if ':' in name else 'minecraft:'+name)}
 if props:result['Properties']=(10,{k:S(str(v)) for k,v in props.items()})
 return result
VOID=state('structure_void')
PLANTS={'short_grass','tall_grass','wheat','dandelion','poppy','oxeye_daisy','potted_dandelion','oak_leaves'}
FURNITURE={'barrel','blast_furnace','bookshelf','brewing_stand','cartography_table','chest','composter','fletching_table','furnace','grindstone','lectern','loom','smithing_table','smoker','stonecutter','water_cauldron'}
def convert(source):
 source=copy.deepcopy(source);name=value(source,'Name').split(':')[-1]
 props={k:v[1] for k,v in source.get('Properties',(10,{}))[1].items()}
 if 'waterlogged' in props:props['waterlogged']='false'
 if props:source['Properties']=(10,{k:S(v) for k,v in props.items()})
 if name in {'air','water','lava'}|PLANTS:return copy.deepcopy(VOID)
 if name=='torch':return state('mca:underwater_torch',waterlogged='false')
 if name=='wall_torch':return state('mca:underwater_wall_torch',facing=props.get('facing','north'),waterlogged='false')
 if name=='oak_door':return state('mca:underwater_prismarine_door',**props,waterlogged='false')
 if name in {'oak_log','stripped_oak_log','stripped_oak_wood','terracotta','hay_block'}:return state('dark_prismarine')
 if name in {'cobblestone','mossy_cobblestone','bricks','oak_planks','white_terracotta'}:return state('prismarine_bricks')
 if name in {'grass_block','dirt','dirt_path','farmland'}:return state('prismarine')
 if name in {'oak_stairs','cobblestone_stairs'}:return state('prismarine_brick_stairs',**props)
 if name in {'oak_slab','cobblestone_slab','smooth_stone_slab'}:return state('prismarine_brick_slab',**props)
 if name=='cobblestone_wall':return state('prismarine_wall',**props)
 if name=='oak_fence':return state('prismarine_wall',**{k:('low' if v=='true' else 'none') for k,v in props.items() if k in ['north','south','east','west']},up='true',waterlogged='false')
 if name in {'glass_pane','white_stained_glass_pane','yellow_stained_glass_pane'}:return state('cyan_stained_glass_pane',**props)
 if name in {'white_wool','yellow_wool'}:return state('cyan_terracotta')
 if name.endswith('_carpet'):return state('prismarine_brick_slab',type='bottom',waterlogged='false')
 return source

def rewrite_text(text):
 text=text.replace('minecraft:village/plains/','mca:village/underwater/')
 if text.startswith('minecraft:') and not text.startswith('minecraft:village/'):
  match=re.fullmatch(r'([\w:]+)(?:\[(.*)\])?',text)
  if match:
   props=dict(p.split('=',1) for p in (match[2] or '').split(',') if p)
   changed=convert(state(match[1],**props))
   text=value(changed,'Name');p=changed.get('Properties',(10,{}))[1]
   if p:text+='['+','.join(k+'='+v[1] for k,v in sorted(p.items()))+']'
 return text

def jigsaw(block):
 block=copy.deepcopy(block)
 if 'nbt' in block:
  nbt=value(block,'nbt')
  for key in ['pool','final_state']:
   if key in nbt:nbt[key]=S(rewrite_text(value(nbt,key)))
 return block

def assemble(original,grid,size):
 palette=[];indices={};blocks=[]
 for p,(st,nbt) in sorted(grid.items(),key=lambda item:(item[0][1],item[0][0],item[0][2])):
  key=json.dumps(st,sort_keys=True)
  if key not in indices:indices[key]=len(palette);palette.append(st)
  block={'pos':L(p),'state':(3,indices[key])}
  if nbt is not None:block['nbt']=(10,nbt)
  blocks.append(block)
 result=copy.deepcopy(original);result.pop('palettes',None)
 result['size']=L(size);result['palette']=(9,(10,palette));result['blocks']=(9,(10,blocks))
 return result

def redesign(original,kind,seed):
 palette=value(original,'palette')[1];blocks=value(original,'blocks')[1];sx,sy,sz=value(original,'size')[1]
 grid={};nodes=[];doors=[];furniture=[];beds=0
 for block in blocks:
  st=palette[value(block,'state')];name=value(st,'Name').split(':')[-1];p=pos(block)
  if name=='jigsaw':nodes.append(jigsaw(block))
  elif name=='oak_door':doors.append((p,convert(st)))
  elif name.endswith('_bed'):
   if st.get('Properties',(10,{}))[1].get('part')==S('head'):beds+=1
  elif name in FURNITURE:furniture.append((convert(st),copy.deepcopy(block.get('nbt',(10,None))[1])))
 def put(x,y,z,st,nbt=None):
  if 0<=x<sx and 0<=z<sz and y>=0:grid[(x,y,z)]=(st,nbt)
 def floor(x,y,z):put(x,y,z,state('dark_prismarine' if (x+z)%5==0 else 'prismarine_bricks'))
 floor_y=min((p[1]-1 for p,_ in doors if p[1]>0),default=0)
 if kind=='center':
  horizontal=[pos(n)[1] for n in nodes if value(palette[value(n,'state')],'Name')=='minecraft:jigsaw' and value(value(n,'nbt'),'target')=='minecraft:street']
  floor_y=max(0,min(horizontal,default=1)-1)
 height=max(sy,floor_y+7)
 # Empty cells are structure void: retain ocean water, but never create water on dry land.
 for x in range(sx):
  for z in range(sz):
   for y in range(height):put(x,y,z,copy.deepcopy(VOID))
 if kind=='center':
  cx,cz=sx//2,sz//2
  for x in range(sx):
   for z in range(sz):floor(x,floor_y,z)
  for dx,dz in [(-2,-2),(-2,2),(2,-2),(2,2)]:
   for y in range(1,4):put(cx+dx,floor_y+y,cz+dz,state('prismarine_bricks'))
   put(cx+dx,floor_y+4,cz+dz,state('sea_lantern'))
  for y in range(1,6):put(cx,floor_y+y,cz,state('dark_prismarine' if y%2 else 'sea_lantern'))
  put(cx,floor_y+6,cz,state('sea_lantern'))
  for dx,dz in [(-2,0),(2,0),(0,-2),(0,2)]:
   put(cx+dx,floor_y+1,cz+dz,state('dark_prismarine'))
   put(cx+dx,floor_y+2,cz+dz,state('mca:underwater_torch',waterlogged='false'))
  put(cx+1,floor_y+1,cz,state('prismarine_bricks'))
  put(cx+1,floor_y+2,cz,state('bell',attachment='floor',facing='north',powered='false'))
 elif kind=='house':
  # Bevelled stone pods with stepped glass skylights, rather than plains gabled roofs.
  x0,z0=1,1;x1,z1=sx-2,sz-2
  for x in range(sx):
   for z in range(sz):floor(x,floor_y,z)
  for x in range(x0,x1+1):
   for z in range(z0,z1+1):
    boundary=x in (x0,x1) or z in (z0,z1)
    corner=x in (x0,x1) and z in (z0,z1)
    if boundary:
     for y in range(1,4):
      material='dark_prismarine' if corner else 'cyan_stained_glass' if y==2 else 'prismarine_bricks'
      put(x,floor_y+y,z,state(material))
    # Two-height terraced roof, with an illuminated central skylight.
    put(x,floor_y+4,z,state('prismarine_bricks' if boundary else 'cyan_stained_glass'))
    if not boundary:put(x,floor_y+5,z,state('prismarine_brick_slab',type='bottom',waterlogged='false'))
  put(sx//2,floor_y+5,sz//2,state('sea_lantern'))
  # Restore real entrances and carve a two-block-high passage to the pod interior.
  for p,st in doors:
   if p[1]>floor_y+2:continue
   x,y,z=p;put(x,y,z,st)
   facing=value(st,'Properties')['facing'][1]
   dx,dz={'north':(0,-1),'south':(0,1),'east':(1,0),'west':(-1,0)}[facing]
   put(x+dx,y,z+dz,copy.deepcopy(VOID))
   for step in range(1,max(sx,sz)):put(x-dx*step,y,z-dz*step,copy.deepcopy(VOID))
  cells=[(x,z) for z in range(2,sz-2) for x in range(2,sx-2) if x!=sx//2 and z!=sz//2]
  cells.sort(key=lambda p: (min(p[0]-2,sx-3-p[0],p[1]-2,sz-3-p[1]),p[1],p[0]))
  occupied=set();cursor=0
  for (x,y,z),door in doors:
   if y>floor_y+2:continue
   occupied.add((x,z))
   facing=value(door,'Properties')['facing'][1]
   dx,dz={'north':(0,-1),'south':(0,1),'east':(1,0),'west':(-1,0)}[facing]
   occupied.add((x+dx,z+dz))
   occupied.update((x-dx*step,z-dz*step) for step in range(1,max(sx,sz)))
  for st,nbt in furniture:
   if not cells:break
   while cursor<len(cells) and cells[cursor] in occupied:cursor+=1
   if cursor>=len(cells):break
   x,z=cells[cursor];cursor+=1;occupied.add((x,z));put(x,floor_y+1,z,st,nbt)
  for _ in range(beds):
   for x,z in reversed(cells):
    if z+1<sz-2 and (x,z) not in occupied and (x,z+1) not in occupied:
     for part,dz in [('head',0),('foot',1)]:put(x,floor_y+1,z+dz,state('cyan_bed',part=part,facing='north',occupied='false'))
     occupied.update([(x,z),(x,z+1)]);break
  for x,z in [(2,2),(sx-3,sz-3)]:
   if 0<x<sx-1 and 0<z<sz-1 and (x,z) not in occupied:
    put(x,floor_y+1,z,state('mca:underwater_torch',waterlogged='false'));occupied.add((x,z))
 else:
  # Courtyards and reef planters retain open swimming space instead of crop fields.
  for x in range(1,sx-1):
   for z in range(1,sz-1):floor(x,floor_y,z)
  colors=['tube_coral_block','brain_coral_block','bubble_coral_block','fire_coral_block','horn_coral_block']
  for x in range(2,sx-1,3):
   for z in range(2,sz-1,3):
    put(x,floor_y+1,z,state('dark_prismarine'))
    put(x,floor_y+2,z,state(colors[(x+z+seed)%len(colors)]))
  put(sx//2,floor_y+1,sz//2,state('sea_lantern'))
  if sx>4 and sz>4:put(1,floor_y+1,1,state('mca:underwater_torch',waterlogged='false'))
  for st,nbt in furniture[:1]:put(2,floor_y+1,1,st,nbt)
 # Preserve every original connector and spawn marker, including its orientation.
 for node in nodes:
  p=pos(node);put(*p,palette[value(node,'state')],copy.deepcopy(value(node,'nbt')))
 return assemble(original,grid,(sx,height,sz))

def greenhouse(original):
 palette=value(original,'palette')[1];nodes=[jigsaw(block) for block in value(original,'blocks')[1] if value(palette[value(block,'state')],'Name')=='minecraft:jigsaw']
 sx,sy,sz=value(original,'size')[1];sy=max(sy,6);grid={}
 def put(x,y,z,block,nbt=None):
  if 0<=x<sx and 0<=y<sy and 0<=z<sz:grid[(x,y,z)]=(block,nbt)
 for x in range(sx):
  for z in range(sz):
   put(x,0,z,state('prismarine_bricks'))
   for y in range(1,5):put(x,y,z,state('glass') if x in (0,sx-1) or z in (0,sz-1) else state('cave_air'))
   put(x,5,z,state('glowstone') if x%3==1 and z%3==1 else state('glass'))
 cx,cz=sx//2,sz//2
 for x in range(1,sx-1):
  for z in range(1,sz-1):
   if x==cx or z==cz:continue
   put(x,0,z,state('farmland',moisture='7'))
   crop=['wheat','carrots','potatoes'][(x+z)%3]
   put(x,1,z,state(crop,age='7'))
 put(cx,0,cz,state('water',level='0'));put(cx,1,cz,state('glass'))
 put(1,1,1,state('composter',level='0'))
 # Closed pressure doors, rather than waterlogged doors, maintain dry growing space.
 for node in nodes:
  nbt=value(node,'nbt')
  if value(nbt,'target')=='minecraft:building_entrance':
   x,y,z=pos(node);orientation=value(value(palette[value(node,'state')],'Properties'),'orientation').split('_')[0]
   dx,dz={'east':(-1,0),'west':(1,0),'north':(0,1),'south':(0,-1)}.get(orientation,(0,0))
   facing={'east':'west','west':'east','north':'south','south':'north'}.get(orientation,'north')
   door_x,door_z=x,z
   for part,dy in [('lower',0),('upper',1)]:put(door_x,1+dy,door_z,state('mca:prismarine_greenhouse_door',facing=facing,half=part,hinge='left',open='false',powered='false'))
   for step in range(1,max(sx,sz)):
    px,pz=x+dx*step,z+dz*step
    if not 0<px<sx-1 or not 0<pz<sz-1:break
    put(px,0,pz,state('prismarine_bricks'));put(px,1,pz,state('cave_air'));put(px,2,pz,state('cave_air'))
  if value(nbt,'target')=='minecraft:building_entrance':nbt['final_state']=S('minecraft:prismarine_bricks')
  put(*pos(node),palette[value(node,'state')],nbt)
 return assemble(original,grid,(sx,sy,sz))

def transform(original):
 palette=value(original,'palette')[1];grid={}
 for block in value(original,'blocks')[1]:
  st=convert(palette[value(block,'state')]);node=jigsaw(block)
  grid[pos(node)]=(st,copy.deepcopy(node.get('nbt',(10,None))[1]))
 return assemble(original,grid,value(original,'size')[1])

def village_outpost(tower, biome):
 """Keep vanilla plains/taiga architecture; give other villages their local palette."""
 palette=value(tower,'palette')[1];grid={}
 for block in value(tower,'blocks')[1]:
  grid[pos(block)]=(copy.deepcopy(palette[value(block,'state')]),copy.deepcopy(block.get('nbt',(10,None))[1]))
 # Glaze ground-floor arrow slits and the broad middle-floor openings.
 windows=[(x,2,z) for x,z in [(6,2),(8,2),(2,6),(2,8),(12,6),(12,8)]]
 windows += [(x,y,z) for y in (6,7) for x,z in
             [(a,b) for a in (6,7,8) for b in (3,11)]+[(b,a) for a in (6,7,8) for b in (3,11)]]
 windows += [(x,y,z) for y in (10,11) for x,z in
             [(a,b) for a in (5,9) for b in (3,11)]+[(b,a) for a in (5,9) for b in (3,11)]]
 pane_positions=set(windows)
 for x,y,z in windows:
  connections={}
  for direction,dx,dz in [('north',0,-1),('south',0,1),('east',1,0),('west',-1,0)]:
   neighbor=grid.get((x+dx,y,z+dz),(state('air'),None))[0]
   connected=(x+dx,y,z+dz) in pane_positions or value(neighbor,'Name') not in ('minecraft:air','minecraft:structure_void')
   connections[direction]=str(connected).lower()
  grid[(x,y,z)]=(state('glass_pane',**connections,waterlogged='false'),None)
 # Close the only entrance; do not place glass across this walking route.
 door={'desert':'birch_door','savanna':'acacia_door','snowy':'spruce_door'}.get(biome,'dark_oak_door')
 for y,half in [(1,'lower'),(2,'upper')]:
  grid[(7,y,12)]=(state(door,facing='south',half=half,hinge='left',open='false',powered='false'),None)
 for x,y,z,facing in [(3,3,7,'east'),(11,3,7,'west'),(7,3,3,'south'),(7,3,11,'north'),(5,7,4,'south'),(9,7,10,'north'),(7,11,4,'south'),(7,11,10,'north'),(4,15,5,'south'),(10,15,9,'north')]:
  grid[(x,y,z)]=(state('wall_torch',facing=facing),None)
 def material(st):
  name=value(st,'Name').removeprefix('minecraft:');props={k:v[1] for k,v in st.get('Properties',(10,{}))[1].items()}
  if biome=='desert':
   mapping={'dark_oak_planks':'smooth_sandstone','birch_planks':'cut_sandstone','dark_oak_log':'chiseled_sandstone','cobblestone':'sandstone',
            'dark_oak_stairs':'smooth_sandstone_stairs','cobblestone_stairs':'sandstone_stairs','dark_oak_slab':'smooth_sandstone_slab','cobblestone_slab':'sandstone_slab','cobblestone_wall':'sandstone_wall'}
  elif biome=='savanna':
   mapping={'dark_oak_planks':'acacia_planks','birch_planks':'acacia_planks','dark_oak_log':'acacia_log','cobblestone':'orange_terracotta',
            'dark_oak_stairs':'acacia_stairs','cobblestone_stairs':'red_sandstone_stairs','dark_oak_slab':'acacia_slab','cobblestone_slab':'red_sandstone_slab','dark_oak_fence':'acacia_fence','cobblestone_wall':'red_sandstone_wall'}
  elif biome=='snowy':
   mapping={'dark_oak_planks':'spruce_planks','birch_planks':'spruce_planks','dark_oak_log':'spruce_log','cobblestone':'stone_bricks',
            'dark_oak_stairs':'spruce_stairs','cobblestone_stairs':'stone_brick_stairs','dark_oak_slab':'spruce_slab','cobblestone_slab':'stone_brick_slab','dark_oak_fence':'spruce_fence','cobblestone_wall':'stone_brick_wall'}
  else:return st
  if biome=='desert' and name=='dark_oak_fence':
   return state('sandstone_wall',**{k:('low' if v=='true' else 'none') for k,v in props.items() if k in ('north','south','east','west')},up='true',waterlogged='false')
  if name not in mapping:return st
  target=mapping[name]
  if not any(target.endswith(t) for t in ('_stairs','_slab','_wall','_fence','_log')):props={}
  return state(target,**props)
 grid={p:(material(st),nbt) for p,(st,nbt) in grid.items()}
 # A server-only marker identifies this generated building without a new visible block.
 result=assemble(tower,grid,value(tower,'size')[1])
 entities=copy.deepcopy(value(tower,'entities')[1]) if 'entities' in tower else []
 entities.append({'pos':(9,(6,[7.5,1.0,7.5])),'blockPos':L([7,1,7]),'nbt':(10,{'id':S('minecraft:marker'),'Tags':(9,(8,['mca_village_outpost']))})})
 result['entities']=(9,(10,entities))
 return result

def facility_start(original, biome):
 # Facilities are independent jigsaw pieces. Appending them to a giant start piece
 # makes vanilla treat streets/greenhouses as internal children and permits overlap.
 palette=value(original,'palette')[1];grid={pos(b):(copy.deepcopy(palette[value(b,'state')]),copy.deepcopy(b.get('nbt',(10,None))[1])) for b in value(original,'blocks')[1]}
 sx,sy,sz=value(original,'size')[1]
 street=[pos(b)[1] for b in value(original,'blocks')[1] if value(palette[value(b,'state')],'Name')=='minecraft:jigsaw'
         and value(value(b,'nbt'),'target')=='minecraft:street']
 y=min(street,default=1)
 for kind,x,facing in [('outpost',sx-1,'east'),('infirmary',0,'west')]:
  choices=sorted(range(1,sz-1),key=lambda z:abs(z-sz//2))
  z=next((z for z in choices if value(grid.get((x,y,z),(VOID,None))[0],'Name')!='minecraft:jigsaw'),None)
  if z is None:raise ValueError(f'No free {kind} connector in {biome} start')
  material='prismarine_bricks' if biome=='underwater' else {'desert':'sandstone','savanna':'acacia_planks','snowy':'spruce_planks'}.get(biome,'cobblestone')
  if y>0:grid[(x,y-1,z)]=(state(material),None)
  grid[(x,y,z)]=(state('jigsaw',orientation=facing+'_up'),{
   'id':S('minecraft:jigsaw'),'name':S('mca:facility_source'),'target':S('mca:facility_entrance'),
   'pool':S(f'mca:village/{biome}/facilities/{kind}'),'final_state':S('minecraft:air'),
   'joint':S('aligned'),'selection_priority':(3,100),'placement_priority':(3,100)})
 return assemble(original,grid,(sx,max(sy,y+1),sz))

def outpost_template(tower,biome):
 result=village_outpost(tower,biome);palette=value(result,'palette')[1];grid={}
 for b in value(result,'blocks')[1]:
  st=copy.deepcopy(palette[value(b,'state')]);nbt=copy.deepcopy(b.get('nbt',(10,None))[1])
  if value(st,'Name')=='minecraft:jigsaw':st=copy.deepcopy(VOID);nbt=None
  if biome=='underwater':
   st=convert(st)
   if value(st,'Name').endswith('_door'):
    props={k:v[1] for k,v in st.get('Properties',(10,{}))[1].items()};props['waterlogged']='false';st=state('mca:underwater_prismarine_door',**props)
  grid[pos(b)]=(st,nbt)
 material='prismarine_bricks' if biome=='underwater' else {'desert':'sandstone','savanna':'red_sandstone','snowy':'stone_bricks'}.get(biome,'cobblestone')
 for x in range(6,9):
  for z in range(12,15):grid[(x,0,z)]=(state(material),None)
 # Anchor at floor height, raising the entire outpost one block relative to its old feet-height connector.
 # Explicit approach air removes vegetation/terrain instead of preserving it as structure_void.
 for x in range(6,9):
  for z in (13,14):
   for y in (1,2):grid[(x,y,z)]=(state('water' if biome=='underwater' else 'air'),None)
 grid[(7,0,14)]=(state('jigsaw',orientation='south_up'),{'id':S('minecraft:jigsaw'),'name':S('mca:facility_entrance'),
  'target':S('minecraft:empty'),'pool':S('minecraft:empty'),'final_state':S('minecraft:'+material),'joint':S('aligned')})
 result=assemble(result,grid,value(result,'size')[1]);entities=copy.deepcopy(value(result,'entities')[1])
 for x,z in [(6,7),(8,7)]:entities.append({'pos':(9,(6,[x+.5,1.0,z+.5])),'blockPos':L([x,1,z]),'nbt':(10,{
  'id':S('minecraft:pillager'),'PersistenceRequired':(1,1),'equipment':(10,{'mainhand':(10,{'id':S('minecraft:crossbow'),'count':(3,1)})})})})
 result['entities']=(9,(10,entities));return result

def infirmary_template(temple,biome):
 if biome=='plains':
  palette=value(temple,'palette')[1];grid={pos(b):(copy.deepcopy(palette[value(b,'state')]),copy.deepcopy(b.get('nbt',(10,None))[1])) for b in value(temple,'blocks')[1]}
  for xyz,part in [((3,1,2),'foot'),((2,1,2),'head')]:grid[xyz]=(state('white_bed',part=part,facing='west',occupied='false'),None)
  entrance=(0,0,3);orientation='west_up';size=value(temple,'size')[1]
 else:
  grid={};size=(9,9,9);entrance=(4,1,0);orientation='north_up'
  wood={'taiga':'spruce','savanna':'acacia','snowy':'spruce'}.get(biome,'oak')
  wall={'taiga':'spruce_planks','desert':'smooth_sandstone','savanna':'white_terracotta','snowy':'spruce_planks','underwater':'prismarine_bricks'}[biome]
  beam={'taiga':'spruce_log','desert':'chiseled_sandstone','savanna':'acacia_log','snowy':'spruce_log','underwater':'dark_prismarine'}[biome]
  floor={'desert':'cut_sandstone','savanna':'acacia_planks','snowy':'spruce_planks','underwater':'prismarine_bricks'}.get(biome,'cobblestone')
  for x in range(9):
   for z in range(9):
    grid[(x,0,z)]=(state(floor),None)
    for y in range(1,5):
     boundary=x in (1,7) or z in (1,7);corner=x in (1,7) and z in (1,7)
     if 1<=x<=7 and 1<=z<=7:
      st=state(beam if corner else 'glass' if boundary and y==2 and (x in (3,5) or z in (3,5)) else wall) if boundary else state('water',level='0') if biome=='underwater' else state('cave_air')
      grid[(x,y,z)]=(st,None)
    if 1<=x<=7 and 1<=z<=7:
     grid[(x,5,z)]=(state('sea_lantern' if biome=='underwater' and (x,z)==(4,4) else wall),None)
  # Different roofs distinguish local clinics from vanilla temples.
  if biome in ('taiga','savanna','snowy'):
   for x in range(9):
    height=5+min(x,8-x)//2
    for z in range(9):grid[(x,height,z)]=(state(wood+'_stairs',facing='east' if x<4 else 'west',half='bottom',shape='straight',waterlogged='false'),None)
   if biome=='snowy':
    for z in range(9):grid[(4,8,z)]=(state('snow_block'),None)
  elif biome=='desert':
   for x in range(1,8):
    for z in range(1,8):
     if x in (1,7) or z in (1,7):grid[(x,6,z)]=(state('sandstone_wall',north='none',south='none',east='none',west='none',up='true',waterlogged='false'),None)
  door='mca:underwater_prismarine_door' if biome=='underwater' else 'birch_door' if biome=='desert' else wood+'_door'
  for y,half in [(1,'lower'),(2,'upper')]:grid[(4,y,1)]=(state(door,facing='north',half=half,hinge='left',open='false',powered='false'),None)
  if biome=='underwater':
   for y in (1,2):grid[(4,y,1)][0]['Properties'][1]['waterlogged']=S('true')
  color={'underwater':'cyan','savanna':'orange','desert':'red','snowy':'light_blue'}.get(biome,'white')
  for z,part in [(3,'head'),(4,'foot')]:grid[(2,1,z)]=(state(color+'_bed',part=part,facing='north',occupied='false'),None)
  grid[(6,1,3)]=(state('brewing_stand',has_bottle_0='false',has_bottle_1='false',has_bottle_2='false'),None)
  if biome!='underwater':grid[(4,4,6)]=(state('wall_torch',facing='north'),None)
  for z in (0,1):grid[(4,0,z)]=(state(floor),None)
 grid[entrance]=(state('jigsaw',orientation=orientation),{'id':S('minecraft:jigsaw'),'name':S('mca:facility_entrance'),
  'target':S('minecraft:empty'),'pool':S('minecraft:empty'),'final_state':S('minecraft:air'),'joint':S('aligned')})
 base=copy.deepcopy(temple);base['entities']=(9,(10,[]))
 return assemble(base,grid,size)

def write_facilities(archive,tower):
 temple=Reader(archive.read('data/minecraft/structure/village/plains/houses/plains_temple_4.nbt')).read()
 for biome in ('plains','taiga','desert','savanna','snowy','underwater'):
  for kind,template in [('outpost',outpost_template(tower,biome)),('infirmary',infirmary_template(temple,biome))]:
   relative=f'village/{biome}/facilities/{kind}';target=ROOT/'structure'/(relative+'.nbt');target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes(encode(template))
   processor='mca:underwater/preserve_water' if biome=='underwater' else 'minecraft:empty'
   pool={'fallback':'minecraft:empty','elements':[{'weight':1,'element':{'element_type':'minecraft:single_pool_element','location':'mca:'+relative,'processors':processor,'projection':'rigid'}}]}
   target=ROOT/'worldgen/template_pool'/(relative+'.json');target.parent.mkdir(parents=True,exist_ok=True);target.write_text(json.dumps(pool,indent=2)+'\n')
 dry=ROOT/'worldgen/processor_list/underwater/dry_infirmary.json';dry.parent.mkdir(parents=True,exist_ok=True)
 dry.write_text(json.dumps({'processors':[{'processor_type':'minecraft:block_ignore','blocks':[{'Name':'minecraft:structure_void'}]}]},indent=2)+'\n')

def main():
 parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--minecraft-jar',required=True);args=parser.parse_args()
 with zipfile.ZipFile(args.minecraft_jar) as archive:
  generated={};count=0
  tower=Reader(archive.read("data/minecraft/structure/pillager_outpost/watchtower.nbt")).read()
  write_facilities(archive,tower)
  pool_root=ROOT/'worldgen/template_pool/village/underwater'
  for pool in sorted(pool_root.rglob('*.json')):
   if 'facilities' in pool.parts:continue
   data=json.loads(pool.read_text())
   for entry in data['elements']:
    element=entry['element'];location=element.get('location','')
    if location.startswith('mca:village/underwater/') and not location.endswith('/reef_garden'):
     location=location.replace('mca:village/underwater/','minecraft:village/plains/').replace('tidal_','plains_')
    if not location.startswith('minecraft:village/plains/'):continue
    suffix=location.split('minecraft:village/plains/')[1]
    destination='village/underwater/'+suffix.replace('plains_','tidal_')
    if destination not in generated:
     original=Reader(archive.read('data/minecraft/structure/'+location.split(':')[1]+'.nbt')).read()
     category=suffix.removeprefix('zombie/').split('/')[0]
     if category=='town_centers':out=facility_start(redesign(original,'center',count),'underwater')
     elif '/houses/' in '/'+suffix:
      simple=suffix.rsplit('/',1)[-1]
      out=greenhouse(original) if 'farm' in simple else redesign(original,'garden' if any(t in simple for t in ['animal_pen','stable','meeting_point']) else 'house',count)
     elif category=='decor':out=redesign(original,'garden',count)
     else:out=transform(original)
     path=ROOT/'structure'/(destination+'.nbt');path.parent.mkdir(parents=True,exist_ok=True);path.write_bytes(encode(out))
     assert Reader(path.read_bytes()).read()==out
     generated[destination]=out;count+=1
    element['location']='mca:'+destination;element['processors']='mca:underwater/preserve_water'
   pool.write_text(json.dumps(data,indent=2)+'\n')
  # Replace the old oak-tree feature with an MCA coral planter structure.
  tree=ROOT/'structure/village/underwater/reef_garden.nbt';tree.parent.mkdir(parents=True,exist_ok=True)
  base={'DataVersion':(3,4671),'size':L([7,6,7]),'palette':(9,(10,[state('jigsaw',orientation='up_north')])),'blocks':(9,(10,[{'pos':L([3,0,3]),'state':(3,0),'nbt':(10,{'id':S('minecraft:jigsaw'),'pool':S('minecraft:empty'),'name':S('minecraft:bottom'),'target':S('minecraft:bottom'),'joint':S('rollable'),'final_state':S('minecraft:prismarine')})}])),'entities':(9,(10,[]))}
  base['DataVersion']=next(iter(generated.values()))['DataVersion']
  tree.write_bytes(encode(redesign(base,'garden',0)))
  (pool_root/'trees.json').write_text(json.dumps({'fallback':'minecraft:empty','elements':[{'weight':1,'element':{'element_type':'minecraft:single_pool_element','location':'mca:village/underwater/reef_garden','processors':'mca:underwater/preserve_water','projection':'rigid'}}]},indent=2)+'\n')
  waterloggable={}
  for path in (ROOT/'structure/village/underwater').rglob('*.nbt'):
   for original_state in value(Reader(path.read_bytes()).read(),'palette')[1]:
    props=original_state.get('Properties',(10,{}))[1]
    if 'waterlogged' not in props:continue
    plain={k:({k2:v2[1] for k2,v2 in v[1].items()} if k=='Properties' else v[1]) for k,v in original_state.items()}
    plain['Properties']['waterlogged']='false';waterloggable[json.dumps(plain,sort_keys=True)]=plain
  rules=[]
  for original_state in waterloggable.values():
   wet=copy.deepcopy(original_state);wet['Properties']['waterlogged']='true'
   rules.append({'input_predicate':{'predicate_type':'minecraft:blockstate_match','block_state':original_state},
    'location_predicate':{'predicate_type':'minecraft:block_match','block':'minecraft:water'},'output_state':wet})
  processors=[{'processor_type':'minecraft:rule','rules':rules},
   {'processor_type':'minecraft:block_ignore','blocks':[{'Name':'minecraft:structure_void'},{'Name':'minecraft:air'}]}]
  (ROOT/'worldgen/processor_list/underwater/preserve_water.json').write_text(json.dumps({'processors':processors},indent=2)+'\n')
  # Retire the old unconditional flooding rules even for data packs referring to these processors.
  for processor in (ROOT/'worldgen/processor_list/underwater').glob('*.json'):
   if processor.name in ('preserve_water.json','dry_infirmary.json'):continue
   data=json.loads(processor.read_text())
   for block in data['processors']:
    if 'rules' not in block:continue
    block['rules']=[r for r in block.get('rules',[]) if r.get('input_predicate',{}).get('block') not in ['minecraft:air','minecraft:oak_door','minecraft:torch','minecraft:wall_torch']]
   data['processors']=[p for p in data['processors'] if p.get('rules') or p.get('processor_type')!='minecraft:rule']
   processor.write_text(json.dumps(data,indent=2)+'\n')
  # Every vanilla village biome gets its own inhabited village-outpost start.
  outpost_count=0
  for biome in ('plains','taiga','desert','savanna','snowy'):
   pool=json.loads(archive.read(f'data/minecraft/worldgen/template_pool/village/{biome}/town_centers.json'))
   for entry in pool['elements']:
    element=entry['element'];location=element['location'].split(':')[1]
    original=Reader(archive.read('data/minecraft/structure/'+location+'.nbt')).read()
    destination=location.replace(f'village/{biome}/',f'village/{biome}_with_outpost/')
    path=ROOT/'structure'/(destination+'.nbt');path.parent.mkdir(parents=True,exist_ok=True)
    path.write_bytes(encode(facility_start(original,biome)))
    element['location']='mca:'+destination;outpost_count+=1
   target=ROOT.parent/f'minecraft/worldgen/template_pool/village/{biome}/town_centers.json'
   target.parent.mkdir(parents=True,exist_ok=True);target.write_text(json.dumps(pool,indent=2)+'\n')
  print(f'Generated {count+1} MCA underwater templates; {outpost_count} biome village starts with outposts.')
if __name__=='__main__':main()
