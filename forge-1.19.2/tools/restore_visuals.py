"""Reviewed original render/icon bindings. No replacement bitmap artwork is generated.

OBJ coordinates are in block units: six-node X is surface depth; transparent-node
Y is height about the block centre. See the original *Render/*Descriptor classes.
"""
from pathlib import Path
import json, math, hashlib

PORT = Path(__file__).resolve().parents[1]
ASSETS = PORT / 'src/main/resources/assets/eln'

ICONS = {
    **{level+'_power_'+kind+'_antenna':'electricalantenna'+('tx' if kind=='transmitter' else 'rx') for level in ['low','medium','high'] for kind in ['transmitter','receiver']},
    'nuclear_alarm':'electricalalarm','standard_alarm':'electricalalarm',
    'creative_power_resistor':'powerresistor', 'creative_power_capacitor':'powercapacitor',
    'creative_power_inductor':'powerinductor', 'signal_trimmer':'trimmer',
    'signal_switch':'switch', 'signal_button':'button',
    '5v_control_relay_10a':'lowvoltagerelay', '12v_control_relay_20a':'mediumvoltagerelay',
    '12v_control_relay_50a':'highvoltagerelay', '12v_control_relay_100a':'veryhighvoltagerelay',
    '48v_macerator':'50vmacerator', '240v_macerator':'200vmacerator',
    '48v_plate_machine':'50vplatemachine', '240v_plate_machine':'200vplatemachine',
    '48v_compressor':'50vcompressor', '240v_compressor':'200vcompressor',
    'energy_converter':'elntoic2lvu_side',
    'rolling_shaft':'rollingshaftmachine','48v_fuel_generator':'50vfuelgenerator','240v_fuel_generator':'200vfuelgenerator',
    'utility_pole_w_dc_dc_converter':'utilitypolewdc-dcconverter','direct_utility_pole':'utilitypole',
    'grid_dc_dc_converter':'griddc-dcconverter','grid_switch':'griddc-dcconverter',
    '50v_emergency_lamp':'emergencylamp','200v_emergency_lamp':'emergencylamp',
    'radial_motor':'radialmotor',
    'weak_50v_battery_charger':'batterycharger','50v_battery_charger':'batterycharger','200v_battery_charger':'batterycharger',
    'classic_lamp_socket':'classiclampsocket','lamp_socket_b_projector':'spotlampsocket','suspended_lamp_socket_no_swing':'suspendedlampsocket_noswing_',
    'long_suspended_lamp_socket_no_swing':'longsuspendedlampsocket_noswing_',
}
MODELS = {
    'voltage_probe':('voltagesensor',['main']), 'electrical_probe':('electricalsensor',['main']),
    'thermal_probe':('thermalsensor',['main']), 'temperature_probe':('temperaturesensor',['main']),
    'analog_vumeter':('vumeter',['Vumeter','Pointer']),
    'led_vumeter':('led',['main','Led']), 'multicolor_led_vumeter':('led',['main','Led']),
    'digital_display':('digitaldisplay',['base','glass']+[f'digit{i}' for i in range(4)]+[f'dot{i}' for i in range(5)]+[f'colon{i}' for i in range(1,4)]),
    'signal_trimmer':('signalsourcepot',['main','rot']),
    'signal_switch':('ledswitch',['main','button','led']), 'signal_button':('ledswitch',['main','button','led']),
    'data_logger':('dataloggercrtfloor',['main','led']),
    'modern_data_logger':('flatscreenmonitor',['main','led']),
    'industrial_data_logger':('industrialpanel',['main']),
}
# Width and depth from the original CableRenderDescriptor registrations, in pixels.
CABLES = {
    'overhead_lines':('cable',.95,.95), 'signal_bus_cable':('cable',3.95,3.95), 'type_j_socket':('currentcable',1.9,.9), 'type_e_socket':('currentcable',1.9,.9),
    'signal_cable':('cable',.95,.95), 'low_voltage_cable':('cable',1.95,.95),
    'medium_voltage_cable':('cable',2.95,.95), 'high_voltage_cable':('cable',3.95,1.95),
    'very_high_voltage_cable':('cablevhv',3.95,1.95), 'creative_cable':('cablecreative',8.,4.),
    'low_current_cable':('currentcable',1.9,.9), 'medium_current_cable':('currentcable',2.9,1.9),
    'high_current_cable':('currentcable',3.9,1.9), 'copper_thermal_cable':('tex_thermalcablebase',4.,4.),
}

def mesh_data(path):
    vertices=[]; groups={}; current=''
    for line in path.read_text(encoding='utf-8').splitlines():
        t=line.split()
        if not t: continue
        if t[0]=='v': vertices.append(tuple(float(x) for x in t[1:4]))
        elif t[0] in ('g','o'): current=' '.join(t[1:])
        elif t[0]=='f' and len(t)>=4:
            for ref in t[1:]:
                n=int(ref.split('/')[0]); groups.setdefault(current,[]).append(vertices[n-1 if n>0 else n])
    metadata={}; current=''
    meta=path.with_suffix('.txt')
    if meta.exists():
        for line in meta.read_text(encoding='utf-8').splitlines():
            t=line.split()
            if not t: continue
            if t[0]=='o': current=t[1]
            elif t[0]=='f' and len(t)==3:
                metadata.setdefault(current,{})[t[1]]=float(t[2])
    return groups,metadata

def canonical(point,machine,scale):
    x,y,z=point
    return (.5-z*scale, .5+y*scale if machine else x*scale, .5+x*scale if machine else .5-y*scale)

def restore(parts):
    index=json.loads((ASSETS/'model_index.json').read_text(encoding='utf-8')); report=[]
    for d in parts:
        id=d['id']; kind=d['behavior']; machine=d['family']!='six'
        if kind=='COMPUTER_PROBE':
            d['model']='';d['icon']='blocks/computerprobe_xn';d['modelGroups']=[]
            d['visual']=dict(bounds=[0.,0.,0.,1.,1.,1.],scale=1.,cableTexture='sprites/cable.png',cableWidth=.95/16,cableHeight=.95/16,floorOnly=True)
            report.append({'item':id,'icon':d['icon'],'icon_sha256':hashlib.sha256((ASSETS/'textures/blocks/computerprobe_xn.png').read_bytes()).hexdigest(),'model':'','rendering':'original six-face cube','cubeTextures':['textures/blocks/computerprobe_'+face+'.png' for face in ['xn','xp','yn','yp','zn','zp']],'groups':[],'visual':d['visual']})
            continue
        icon=ICONS.get(id, d['name'].replace(' ','').lower())
        if kind=='SWITCH': icon='switch'
        if kind=='BREAKER': icon='electricalbreaker'
        icon='blocks/'+icon
        if not d.get('customItemRendering'):assert (ASSETS/'textures'/f'{icon}.png').is_file(), (id,icon)
        d['icon']='' if d.get('customItemRendering') else icon
        if id in MODELS: d['model'],d['modelGroups']=MODELS[id]
        if kind=='SOLAR':
            groups,_=mesh_data(ASSETS/index[d['model']])
            d['modelGroups']=[g for g in ('main','panel') if g in groups]
        if kind in ('RESISTOR','CAPACITOR','INDUCTOR','DIODE') and id!='large_rheostat':
            stem={'RESISTOR':'Resistor','CAPACITOR':'Capacitor','INDUCTOR':'Inductor','DIODE':'Diode'}[kind]
            d['model']='powerelectricprimitives'
            # ResistorDescriptor deliberately reuses CapacitorCables; there is no ResistorCables group.
            d['modelGroups']=['Base',stem+'Core',('Capacitor' if kind=='RESISTOR' else stem)+'Cables']
            if kind in ('RESISTOR','INDUCTOR'):d['modelGroups'].append(stem+'BaseExtention')
            if id=='rheostat':d['modelGroups'] += ['ResistorTrack','ResistorWiper']
        if kind in ('SWITCH','BREAKER'):d['modelGroups']=['case','lever']
        if kind=='EMERGENCY_LAMP':d['modelGroups']=['coreCeil','panelCeil','lightCeil','coreWall','coreWallR','coreWallL','lightWall']
        if kind=='LAMP':
            groups,_=mesh_data(ASSETS/index[d['model']])
            d['modelGroups']=[g for g in groups if g in ['socket','socket_unlightable','socket_lightable','lampOn','lampOff','lightAlpha','lightAlphaNoDepth','base','chain','lamp']]
        # All signal/power source-like components use the original main housing and indicator.
        if not d['modelGroups'] and d['model'] and not machine:
            groups,_=mesh_data(ASSETS/index[d['model']])
            d['modelGroups']=[g for g in groups if not g.startswith('Minecraft_') and g not in ('Cube','Cadre_Cube','halo')]
        texture='sprites/cable.png'; width=.95/16; height=.95/16
        if kind not in ('INVENTORY_SCANNER','NIXIE','TIMER','ALARM','WATCH','ENVIRONMENT_SENSOR','LOGIC','ANALOG','PROCESSOR','MQTT_CONTROLLER','MODBUS','SIGNAL_SOURCE','BUTTON','METER','LOGGER','WIRELESS_RX','WIRELESS_TX'):
            width=1.95/16
        if id in CABLES:
            t,w,h=CABLES[id];texture='sprites/'+t+'.png';width=w/16;height=h/16
        assert (ASSETS/texture).exists(),texture
        scale=.5 if kind=='RELAY' or kind=='SWITCH' and d['model']=='lowvoltageswitch' else 1.
        scale*=d.get('modelScale',1.)
        model_lift=(d.get('modelScale',1.)-1)/2
        points=[];applied_mechanical_offset=False
        model_offset=[0.,-.1,0.] if id=='utility_pole' else [2.5,-.5,1.5] if id=='grid_switch' else [-.5,-.5,.5] if id=='thermal_heat_exchanger' or kind=='FLOODLIGHT' else [-.5,-1.5,.5] if kind=='RADIAL_MOTOR' else [0.,0.,0.]
        model_yaw=90. if id=='grid_switch' else 0.
        if kind=='TELEPORTER':model_offset=[-1.,0.,0.]
        if kind=='AUTO_MINER':model_yaw=270.;model_offset=[0.,-1.5,0.]
        if kind=='FABRICATOR':model_yaw=90.;model_offset=[-.5,-.5,.5]
        if kind=='FESTIVE':
            model_yaw=270. if id=='string_lights' else 90.
            model_offset=[.5,-.5,.5] if id=='christmas_tree' else [-.5,-.5,-.5] if id=='string_lights' else [-.5,-.5,.5]
        if d['model']:
            path=ASSETS/index[d['model']]; groups,meta=mesh_data(path)
            if kind=='SOLAR':model_offset[2]=meta.get('main',{}).get('offZ',0.)
            if kind=='GRID':
                if id=='grid_switch':
                    d['modelGroups']=[g for g in groups if not g.startswith('Minecraft_') and not g.startswith('p') and not g.startswith('g')]
                else:
                    d['modelGroups']=['main']+[g for g in groups if g.startswith('p') or g.startswith('g')]
                    if id!='utility_pole' and 'foot' in groups:d['modelGroups']+=['foot']
                    if id=='utility_pole_w_dc_dc_converter':d['modelGroups']+=['transformer','cables']
                d['grid']['anchors']={g:[(min(v[i] for v in groups[g])+max(v[i] for v in groups[g]))/2 for i in range(3)]
                    for g in groups if (g.startswith('p') or g.startswith('g')) and groups[g]}
            selected=d['modelGroups'] or [g for g in groups if not g.startswith('Minecraft_')]
            for g in selected:
                assert g in groups,(id,g)
                if kind=='LAMP' and g.startswith('lightAlpha'):continue
                points+=groups[g]
                if kind=='TURRET' and g not in ('Holder','Joint'):
                    for yaw in range(-70,71,10):
                        cy,sy=math.cos(math.radians(yaw)),math.sin(math.radians(yaw))
                        for elevation in ([0] if g in ('Turret','Sensor') else [-40,0,70]):
                            c,s=math.cos(math.radians(-elevation)),math.sin(math.radians(-elevation))
                            for arm in [0.,1.]:
                                shift=(.25 if g=='LeftGun' else -.25 if g=='RightGun' else 0.)*arm
                                for x,y,z in groups[g]:
                                    xx,yy,zz=c*x-s*y,s*x+c*y,z+shift
                                    points.append((xx*cy+zz*sy,yy,zz*cy-xx*sy))
                if kind=='TELEPORTER' and g in ('door_out','door_in','door_in_charge','gyro','gyro_alpha','whiteblur'):
                    ox,oy,oz=[meta.get(g,{}).get('origin'+axis,0.) for axis in 'XYZ']
                    for angle in range(-90,1,10):
                        c,s=math.cos(math.radians(angle)),math.sin(math.radians(angle))
                        points += [(ox+c*(x-ox)-s*(y-oy),oy+s*(x-ox)+c*(y-oy),z) for x,y,z in groups[g]]
                if kind=='SOLAR' and d['solar']['minimumAngle']!=d['solar']['maximumAngle'] and g=='panel':
                    # Original tracker stops at +/-45 degrees. Its yaw follows the world, not the pedestal.
                    maximum=round(d['solar']['maximumAngle']*180/math.pi-90)
                    for angle in range(-maximum,maximum+1,2):
                        c=math.cos(math.radians(angle));s=math.sin(math.radians(angle))
                        for x,y,z in groups[g]:
                            ox,oy=meta.get(g,{}).get('originX',0.),meta.get(g,{}).get('originY',0.)
                            vx,vy=ox+(x-ox)*c-(y-oy)*s,oy+(x-ox)*s+(y-oy)*c
                            for turn in range(4):
                                cy,sy=math.cos(turn*math.pi/2),math.sin(turn*math.pi/2)
                                points.append((vx*cy+z*sy,vy,z*cy-vx*sy))
                # Include moving switch/pointer endpoints in the server selection envelope.
                m=meta.get(g,{})
                if g in ('lever','Pointer','rot','relay0','relay1') and ('alphaOff' in m or 'rOff' in m):
                    for a in [m.get('alphaOff',m.get('rOff',0)),m.get('alphaOn',m.get('rOn',0)),0]:
                        a=math.radians(a);c=math.cos(a);s=math.sin(a)
                        ox,oy,oz=[m.get('origin'+axis,0) for axis in 'XYZ']
                        for vx,vy,vz in groups[g]:
                            x,y,z=vx-ox,vy-oy,vz-oz
                            points.append((ox+c*x+s*z,oy+y,oz-s*x+c*z) if g=='lever' else
                                (ox+c*x-s*y,oy+s*x+c*y,oz+z) if g.startswith('relay') else
                                (ox+x,oy+c*y-s*z,oz+s*y+c*z))
            if kind=='RESIN_COLLECTOR':
                points += [(x,y,z+meta.get('fill',{}).get('emptyT',0.)) for x,y,z in groups['fill']]
            if kind=='FUSE_HOLDER':points=[(x,-z,y) for x,y,z in points]
            if kind=='FLOODLIGHT':
                radius=max(math.sqrt((x-.5)**2+(y-.5)**2+(z+.5)**2) for g in groups if g!='Lamp_Base_Cube.008' for x,y,z in groups[g])
                points += [(.5+dx*radius,.5+dy*radius,-.5+dz*radius) for dx in [-1,1] for dy in [-1,1] for dz in [-1,1]]
            if kind=='TRANSFORMER':
                core,_=mesh_data(ASSETS/index['feromagneticcorea']);points+=core['fero']
                case,case_meta=mesh_data(ASSETS/index['transformatorcase'])
                points+=case['Case']
                for door in ['DoorL','DoorR']:
                    m=case_meta.get(door,{})
                    ox,oy,oz=[m.get('origin'+axis,0) for axis in 'XYZ']
                    for angle in range(-90,91,10):
                        c,s=math.cos(math.radians(angle)),math.sin(math.radians(angle))
                        points += [(ox+c*(x-ox)+s*(z-oz),y,oz-s*(x-ox)+c*(z-oz)) for x,y,z in case[door]]
                for n in range(1,65):
                    winding_scale=4*min(n/16,1)*2/(n+1)
                    for k in [0,n-1]:
                        for x,y,z in groups['sbire']:
                            for side in [-1,1]:points.append((side*x,winding_scale*(y-.125*(n-1)/4+k*.25/4),side*z))
            if kind=='LAMP' and d['lamp'].get('chainLength',0):
                n=d['lamp']['chainLength'];base=meta.get('base',{}).get('length',0);link=meta.get('chain',{}).get('length',0)
                points=list(groups['base'])
                for i in range(n):points += [(x+base+i*link,y,z) for x,y,z in groups['chain']]
                points += [(x+base+n*link,y,z) for x,y,z in groups['socket']]
                if d['lamp'].get('swing'):
                    copy=points[:]
                    for angle in [-15,15]:
                        c,s=math.cos(math.radians(angle)),math.sin(math.radians(angle))
                        points += [(c*x-s*y,s*x+c*y,z) for x,y,z in copy]+[(c*x+s*z,y,c*z-s*x) for x,y,z in copy]
            if kind=='LAMP' and id in ['robust_lamp_socket','sconce_lamp_socket']:points=[(x,z,-y) for x,y,z in points]
            if kind=='SHAFT_HUB':
                points += [(-x,y,-z) for x,y,z in points] + [(-z,y,x) for x,y,z in points] + [(z,y,-x) for x,y,z in points] + [(-y,x,z) for x,y,z in points] + [(y,-x,z) for x,y,z in points]
            if kind in ('WIND_TURBINE','WATER_TURBINE'):
                rotor='rot' if kind=='WIND_TURBINE' else 'Wheel'
                ox,oy,oz=[meta.get(rotor,{}).get('origin'+axis,0.) for axis in 'XYZ']
                for angle in range(0,360,2):
                    c,s=math.cos(math.radians(angle)),math.sin(math.radians(angle))
                    points += [(x,oy+c*(y-oy)-s*(z-oz),oz+s*(y-oy)+c*(z-oz)) for x,y,z in groups[rotor]]
            if kind in ('SHAFT','SHAFT_HUB','FLYWHEEL','CLUTCH','SHAFT_MOTOR','SHAFT_GENERATOR','GAS_TURBINE','STEAM_TURBINE','ROLLING_SHAFT','CRANK','TACHOMETER','FIXED_SHAFT','RADIAL_MOTOR'):
                points=[(z+model_offset[2],y+model_offset[1],-x-model_offset[0]) for x,y,z in points]
                applied_mechanical_offset=True
            if kind=='GRID' or any(model_offset) or model_yaw:
                c,s=math.cos(math.radians(model_yaw)),math.sin(math.radians(model_yaw))
                points=[(c*(x+(0 if applied_mechanical_offset else model_offset[0]))+s*(z+(0 if applied_mechanical_offset else model_offset[2])),y+(0 if applied_mechanical_offset else model_offset[1]),c*(z+(0 if applied_mechanical_offset else model_offset[2]))-s*(x+(0 if applied_mechanical_offset else model_offset[0]))) for x,y,z in points]
            if d.get('powerAntenna'):
                angle=math.radians(270 if d['powerAntenna']['transmitter'] else 90);c,s=math.cos(angle),math.sin(angle)
                points=[(x,y*c-z*s,y*s+z*c) for x,y,z in points]
                points=[(.5+z,.5-x,.5-y) for x,y,z in points]
            else:points=[(x,y+model_lift,z) for x,y,z in (canonical(v,machine,scale) for v in points)]
            bounds=[min(v[i] for v in points) for i in range(3)]+[max(v[i] for v in points) for i in range(3)]
            # The source logger housings genuinely extend most of a block from their mounting face.
        else: bounds=[0.,0.,0.,1.,1.,1.] if machine else [.5-width/2,0.,.5-width/2,.5+width/2,height,.5+width/2]
        d['visual']={'scale':scale,'bounds':bounds,'cableTexture':texture,'cableWidth':width,'cableHeight':height,
                     'floorOnly':kind in ('GRID','SOLAR','TURRET') or id in ('data_logger','modern_data_logger'),'cubeTexture':'textures/blocks/elntoic2lvu_side.png' if kind=='FE_CONVERTER' else '',
                     'modelOffset':model_offset,'modelYaw':model_yaw,'modelLift':model_lift}
        report.append({'item':id,'icon':d['icon'],'icon_sha256':None if d.get('customItemRendering') else hashlib.sha256((ASSETS/'textures'/f'{icon}.png').read_bytes()).hexdigest(),
                       'model':d['model'],'groups':d['modelGroups'],'visual':d['visual']})
    (PORT/'docs/visual-bindings.json').write_text(json.dumps(report,indent=2)+'\n')
