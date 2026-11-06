#!/usr/bin/env python3
"""Check generated village structures and resource links before packaging."""
import json
from pathlib import Path
from structure_nbt import Reader, value, pos

ROOT = Path(__file__).resolve().parents[1] / 'common/src/main/resources'
DATA = ROOT / 'data/mca'

def check():
    rules = json.loads((DATA / 'worldgen/processor_list/underwater/preserve_water.json').read_text())['processors'][0]['rules']
    wet_states = {json.dumps(rule['input_predicate']['block_state'], sort_keys=True) for rule in rules}
    templates = list((DATA / 'structure/village/underwater').rglob('*.nbt'))
    for biome in ('plains','taiga','desert','savanna','snowy'):
        templates += list((DATA / f'structure/village/{biome}_with_outpost').rglob('*.nbt'))
        templates += list((DATA / f'structure/village/{biome}/facilities').rglob('*.nbt'))
    greenhouses = outposts = doors = infirmaries = starts = 0
    for path in templates:
        data = Reader(path.read_bytes()).read()
        palette = value(data, 'palette')[1]
        size = value(data, 'size')[1]
        blocks = {pos(block): palette[value(block, 'state')] for block in value(data, 'blocks')[1]}
        assert len(blocks) == len(value(data, 'blocks')[1]), f'{path}: duplicate coordinates'
        assert all(all(0 <= p[i] < size[i] for i in range(3)) for p in blocks), f'{path}: out of bounds'
        underwater = 'underwater' in path.parts
        farm = 'farm' in path.name
        names = [value(state, 'Name') for state in blocks.values()]
        if underwater:
            assert not any('dead_' in name for name in names), f'{path}: dead coral'
            assert 'minecraft:torch' not in names and 'minecraft:wall_torch' not in names
        for p, state in blocks.items():
            name = value(state, 'Name')
            props = {key: val[1] for key, val in state.get('Properties', (10, {}))[1].items()}
            if underwater and 'waterlogged' in props:
                plain = {'Name': name, 'Properties': {**props, 'waterlogged': 'false'}}
                assert json.dumps(plain, sort_keys=True) in wet_states, f'{path}: missing waterlogging rule'
                assert not farm, f'{path}: waterloggable greenhouse interior'
            if name.endswith('_door'):
                delta = 1 if props['half'] == 'lower' else -1
                other = blocks.get((p[0], p[1] + delta, p[2]))
                assert other and value(other, 'Name') == name, f'{path}: unpaired door at {p}'
                assert value(value(other, 'Properties'), 'half') != props['half']
                doors += props['half'] == 'lower'
            if name.endswith('_bed'):
                dx, dz = {'north': (0, -1), 'south': (0, 1), 'east': (1, 0), 'west': (-1, 0)}[props['facing']]
                sign = 1 if props['part'] == 'foot' else -1
                other = blocks.get((p[0] + dx * sign, p[1], p[2] + dz * sign))
                assert other and value(other, 'Name') == name, f'{path}: unpaired bed at {p}'
                assert value(value(other, 'Properties'), 'part') != props['part']
        if farm:
            greenhouses += 1
            for expected in ['minecraft:farmland', 'minecraft:wheat', 'minecraft:carrots', 'minecraft:potatoes', 'minecraft:glowstone', 'minecraft:composter', 'mca:prismarine_greenhouse_door']:
                assert expected in names, f'{path}: missing {expected}'
            assert 'minecraft:cave_air' in names
            sx, _, sz = size
            for x in range(sx):
                for z in range(sz):
                    assert value(blocks[(x, 5, z)], 'Name') in {'minecraft:glass', 'minecraft:glowstone'}, f'{path}: roof leak'
                    if x in (0, sx - 1) or z in (0, sz - 1):
                        for y in range(1, 5):
                            assert value(blocks[(x, y, z)], 'Name') in {'minecraft:glass', 'mca:prismarine_greenhouse_door'}, f'{path}: wall leak'
        if 'town_centers' in path.parts:
            starts += 1
            connectors = [b for b in value(data, 'blocks')[1] if 'nbt' in b and value(b,'nbt').get('target') == (8,'mca:facility_entrance')]
            assert len(connectors) == 2, f'{path}: missing guaranteed facilities'
            assert not value(data,'entities')[1], f'{path}: facilities still embedded in start'
            boxes=[]
            for connector in connectors:
                nbt=value(connector,'nbt');assert value(nbt,'selection_priority') == 100
                pool=json.loads((DATA/'worldgen/template_pool'/(value(nbt,'pool').split(':')[1]+'.json')).read_text())
                assert len(pool['elements']) == 1 and pool['elements'][0]['weight'] == 1
                location=pool['elements'][0]['element']['location']
                child=Reader((DATA/'structure'/(location.split(':')[1]+'.nbt')).read_bytes()).read()
                cp=value(child,'palette')[1]
                entrances=[b for b in value(child,'blocks')[1] if 'nbt' in b and value(b,'nbt').get('name') == (8,'mca:facility_entrance')]
                assert len(entrances) == 1
                entrance=entrances[0];front=value(value(cp[value(entrance,'state')],'Properties'),'orientation').split('_')[0]
                parent_front=value(value(palette[value(connector,'state')],'Properties'),'orientation').split('_')[0]
                dirs=['north','east','south','west'];rotation=(dirs.index(parent_front)+2-dirs.index(front))%4
                def rotate(x,z):
                    for _ in range(rotation):x,z=-z,x
                    return x,z
                cx,cy,cz=pos(entrance);cx,cz=rotate(cx,cz)
                px,py,pz=pos(connector);dx,dz={'east':(1,0),'west':(-1,0),'north':(0,-1),'south':(0,1)}[parent_front]
                tx,tz=px+dx-cx,pz+dz-cz
                sx,sy,sz=value(child,'size')[1];corners=[rotate(x,z) for x in (0,sx-1) for z in (0,sz-1)]
                box=(min(x for x,z in corners)+tx,min(z for x,z in corners)+tz,max(x for x,z in corners)+tx,max(z for x,z in corners)+tz)
                assert box[2] < 0 or box[0] >= size[0] or box[3] < 0 or box[1] >= size[2], f'{path}: facility overlaps village start'
                boxes.append(box)
            a,b=boxes
            assert a[2] < b[0] or b[2] < a[0] or a[3] < b[1] or b[3] < a[1], f'{path}: facility overlap'
        if 'facilities' in path.parts and path.stem == 'outpost':
            outposts += 1
            entities = value(data, 'entities')[1]
            markers = [e for e in entities if value(value(e, 'nbt'), 'id') == 'minecraft:marker']
            assert len(markers) == 1, f'{path}: missing generated-outpost marker'
            assert value(value(markers[0],'nbt'),'Tags')[1] == ['mca_village_outpost']
            mx, my, mz = value(markers[0], 'blockPos')[1]
            door = blocks.get((mx, my, mz + 5))
            assert door and value(door, 'Name').endswith('_door'), f'{path}: missing entrance door'
            entrance = [b for b in value(data, 'blocks')[1] if 'nbt' in b and value(b, 'nbt').get('name') == (8, 'mca:facility_entrance')]
            assert len(entrance) == 1 and pos(entrance[0]) == (7, 0, 14), f'{path}: outpost must be raised one block'
            assert value(value(entrance[0], 'nbt'), 'final_state') != 'minecraft:air', f'{path}: entrance floor hole'
            for x in range(6, 9):
                for z in (13, 14):
                    for y in (1, 2):
                        assert value(blocks[(x, y, z)], 'Name') in {'minecraft:air', 'minecraft:water'}, f'{path}: blocked entrance approach'
            ground_torches = 0
            for xyz, st in blocks.items():
                if value(st, 'Name').endswith('wall_torch'):
                    facing = value(value(st, 'Properties'), 'facing')
                    dx, dz = {'north': (0, 1), 'south': (0, -1), 'east': (-1, 0), 'west': (1, 0)}[facing]
                    x, y, z = xyz
                    support = blocks.get((x + dx, y, z + dz))
                    assert support and value(support, 'Name') not in {'minecraft:air', 'minecraft:water', 'minecraft:structure_void'}, f'{path}: unsupported torch {xyz}'
                    if y == 3: ground_torches += 1
            assert ground_torches == 4, f'{path}: missing ground-floor lighting'
            assert sum(name.endswith('glass_pane') for name in names) >= 46, f'{path}: missing glazed windows'
            assert sum(name.endswith('torch') for name in names) >= 10, f'{path}: missing lighting'
            pillagers = [e for e in entities if value(value(e, 'nbt'), 'id') == 'minecraft:pillager']
            assert len(pillagers) == 2, f'{path}: missing outpost defenders'
            for entity in pillagers:
                x, y, z = value(entity, 'blockPos')[1]
                assert (x, y - 1, z) in blocks, f'{path}: no spawn floor'
                for dy in (0, 1):
                    assert value(blocks[(x, y + dy, z)], 'Name') in {'minecraft:air', 'minecraft:structure_void'}, f'{path}: blocked defender'
            assert any(value(value(b, 'nbt'), 'LootTable') == 'minecraft:chests/pillager_outpost'
                       for b in value(data, 'blocks')[1] if 'nbt' in b and 'LootTable' in value(b, 'nbt')), f'{path}: missing outpost loot'
        if 'facilities' in path.parts and path.stem == 'infirmary':
            infirmaries += 1
            assert names.count('minecraft:brewing_stand') == 1, f'{path}: incorrect brewing stand count'
            heads=[p for p,state in blocks.items() if value(state,'Name').endswith('_bed') and value(value(state,'Properties'),'part') == 'head']
            assert len(heads) == 1, f'{path}: incorrect bed count'
            if underwater:
                assert 'minecraft:water' in names
                assert 'minecraft:cave_air' not in names
                assert 'mca:underwater_prismarine_door' in names
                for x in range(1,8):
                    for z in range(1,8):
                        assert value(blocks[(x,5,z)],'Name') in {'minecraft:prismarine_bricks','minecraft:sea_lantern'}
    for namespace in ('mca', 'minecraft'):
        for pool in (ROOT / f'data/{namespace}/worldgen/template_pool/village').rglob('*.json'):
            for entry in json.loads(pool.read_text())['elements']:
                location = entry['element'].get('location', '')
                if location.startswith('mca:'):
                    assert (DATA / 'structure' / (location.split(':')[1] + '.nbt')).is_file(), f'{pool}: missing {location}'
    assert greenhouses == 2 and outposts == 6 and infirmaries == 6 and starts == 40
    print(f'PASS: {len(templates)} templates, {doors} paired doors, {greenhouses} dry food greenhouses, {starts} collision-reserved village starts, {outposts} outpost designs, {infirmaries} infirmary designs, {len(wet_states)} waterlogging rules.')

if __name__ == '__main__':
    check()
