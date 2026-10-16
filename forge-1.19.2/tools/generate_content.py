"""Generate native definitions and language/model/recipe data from reviewed mappings.

The pinned baseline reconciliation and executed behavior audits are under docs/.
Registration and definition counts alone do not establish functional coverage.
"""
from pathlib import Path
import json,re,math
PORT=Path(__file__).resolve().parents[1];RES=PORT/'src/main/resources';ASSETS=RES/'assets/eln'
catalog=json.loads((PORT/'docs/literal-registrations.json').read_text(encoding='utf-8'))
names={(e['family'],e['name']):e for e in catalog}
parts=[]
def slug(s):return re.sub('[^a-z0-9]+','_',s.lower()).strip('_')
def add(name,behavior,family='six',**options):
    old=names.get((family,name))
    legacy=options.pop('legacyId',old['legacy_id'] if old else None)
    if legacy is None:raise ValueError('Missing reviewed ID: '+name)
    default=dict(id=slug(name),name=name,family=family,legacyId=legacy,behavior=behavior,
        model=(old.get('model') or '').lower() if old else '',modelGroups=[],icon='',value=0.,resistance=.001,
        nominalVoltage=50.,maxCurrent=20.,power=1000.,capacity=480000.,initialCharge=.5,batteryHalfLife=2.,color=0xB36B32)
    default.update(options)
    icon=re.sub('[^a-z0-9]','',name.lower())
    if not default['icon']:
        for path in [f'textures/blocks/{icon}.png',f'textures/items/{icon}.png']:
            if (ASSETS/path).is_file(): default['icon']=path[9:-4];break
    if not default['icon']:default['icon']='blocks/lowvoltagecable'
    parts.append(default)

add('Ground Cable','GROUND',nominalVoltage=120000,maxCurrent=100000)
add('Hub','HUB',legacyId=(2<<6)+8,model='hub',modelGroups=['main']+['con'+str(i) for i in range(6)],value=48.,nominalVoltage=120000,maxCurrent=100000,power=0,inventorySlots=['VOLTAGE_CABLE']*4)
add('Electrical Source','VOLTAGE_SOURCE',value=50,nominalVoltage=120000,maxCurrent=100000)
add('Signal Source','SIGNAL_SOURCE',value=5,nominalVoltage=5,maxCurrent=.1)
add('Current Source','CURRENT_SOURCE',value=1,nominalVoltage=120000,maxCurrent=100000)
# ElectricalCableDescriptor / CurrentCableDescriptor equations from upstream 1.24.6.
for name,v,r,i in [('Signal Cable',5,.05,.12),('Low Voltage Cable',50,.0125,24),
    ('Medium Voltage Cable',200,.05,12),('High Voltage Cable',800,.1,7.5),
    ('Very High Voltage Cable',3200,1/15,5.625),('Creative Cable',51200,1.706666666666667e-9,18000),
    ('Low Current Cable',120000,.01,5),('Medium Current Cable',120000,.01,20),('High Current Cable',120000,.01,100)]:
    add(name,'CABLE',nominalVoltage=v,resistance=r,maxCurrent=i,color=0xD4953A if v!=5 else 0x649968)
add('Signal Bus Cable','SIGNAL_BUS',legacyId=(32<<6)+20,model='',resistance=.05,nominalVoltage=5,maxCurrent=.12,power=.5)
for sub,title,group in [(1,'Type J Socket','Socket50V'),(2,'Type E Socket','Socket200V')]:
    add(title,'POWER_SOCKET',legacyId=(67<<6)+sub,model='powersocket',modelGroups=['SocketBase',group],resistance=.1,nominalVoltage=300,power=0,heldModel=True)
add('Scanner','INVENTORY_SCANNER',legacyId=(104<<6)+15,model='scanner',modelGroups=['main','LED_0','LED_1'],heldModel=True,nominalVoltage=5,power=0,maxCurrent=.1)
for tier,(level,voltage,watts,reach,start,end) in enumerate([('Low',50,250,200,.9,.7),('Medium',200,1000,250,.9,.75),('High',800,2000,300,.95,.8)]):
    for receiver in [False,True]:
        title=level+' Power '+('Receiver' if receiver else 'Transmitter')+' Antenna'
        add(title,'POWER_ANTENNA','transparent',legacyId=(7<<6)+tier*2+int(receiver),model='lowpowerreceiverantenna' if receiver else 'lowpowertransmitterantenna',modelGroups=['main'],heldModel=True,nominalVoltage=voltage,power=watts,
            powerAntenna=dict(transmitter=not receiver,cable=level.lower()+'_voltage_cable',range=0 if receiver else reach,efficiencyStart=start,efficiencyEnd=end))
add('Overhead Lines','OVERHEAD_LINES','transparent',legacyId=71<<6,model='overheadgantry',modelGroups=['OverheadGantry'],resistance=.0125,nominalVoltage=800,power=0,maxCurrent=100000)
add('Copper Thermal Cable' ,'THERMAL_CABLE',icon='blocks/copperthermalcable',color=0xC87545,
    thermal=dict(rs=.0005,rp=50.,capacity=.0025*3*(1/.0005+1/50),maximum=980.,minimum=-200.,slow=False))
for sub,name,kind,reach,model,groups,slots in [
    (5,'Humidity Sensor','HUMIDITY',0,'electricalweathersensor',['main'],[]),
    (8,'Electrical Anemometer Sensor','WIND',25,'anemometer',['base_wall','anemometer'],[]),
    (12,'Electrical Entity Sensor','ENTITY',10,'proximitysensor',['Detector','HaloMask'],['ENTITY_FILTER']),
    (13,'Electrical Fire Detector','FIRE',15,'firedetector',['Detector','Led'],[]),
    (14,'Electrical Fire Buzzer','FIRE_BUZZER',15,'firedetector',['Detector','Led'],['PORTABLE_STORE'])]:
    add(name,'ENVIRONMENT_SENSOR',legacyId=(104<<6)+sub,model=model,modelGroups=groups,inventorySlots=slots,heldModel=True,
        nominalVoltage=0 if kind=='FIRE_BUZZER' else 5,maxCurrent=0 if kind=='FIRE_BUZZER' else .1,
        resistance=.05,power=20000/(3600*40) if kind=='FIRE_BUZZER' else .5,
        environment=dict(kind=kind,range=reach,outputEdge=-1 if kind=='FIRE_BUZZER' else 1))
for sub,name,model,digital,hours in [(4,'Analog Watch','wallclock',False,40),(5,'Digital Watch','digitalwallclock',True,15)]:
    add(name,'WATCH',legacyId=(117<<6)+sub,model=model,modelGroups=['base','glass','digitDot','digit0','digit1','digit2','digit3'] if digital else ['base','cHour','cMin'],
        inventorySlots=['PORTABLE_STORE'],nominalVoltage=0,maxCurrent=0,power=20000/(3600*hours),watch=dict(digital=digital),heldModel=True)
add('Electrical Timer','TIMER',legacyId=109<<6,model='electricaltimer',modelGroups=['main','rot','led'],value=2.,nominalVoltage=5,maxCurrent=.1,heldModel=True)
for sub,name,sound,period in [(0,'Nuclear Alarm','alarma',11.),(1,'Standard Alarm','smallalarm_critical',1.2)]:
    add(name,'ALARM',legacyId=(103<<6)+sub,model='alarmmedium',modelGroups=['main','rot','light'],icon='blocks/electricalalarm',nominalVoltage=5,maxCurrent=.1,heldModel=True,alarm=dict(sound=sound,period=period))
for name in ['10A Diode','25A Diode','Signal Diode']:
    rated=.1 if name=='Signal Diode' else 25 if '25' in name else 10
    heat=rated*1.2;capacity=heat*30/130
    add(name,'DIODE',value=1/rated,resistance=.05 if name=='Signal Diode' else .0125,maxCurrent=rated,model='',heldModel=True,
        nominalVoltage=5 if name=='Signal Diode' else 50,thermal=dict(rs=1000/capacity/2,rp=130/heat,capacity=capacity,maximum=130.,minimum=-100.,slow=True))
add('Power Resistor','RESISTOR',value=.01,model='',inventorySlots=['COAL_DUST'],passive=dict(kind='RESISTOR'))
add('Rheostat','RESISTOR',value=.01,model='',inventorySlots=['COAL_DUST'],passive=dict(kind='RESISTOR',controlled=True))
add('Thermistor','RESISTOR',legacyId=(96<<6)+38,value=.01,model='',inventorySlots=['COAL_DUST'],passive=dict(kind='RESISTOR',temperatureCoefficient=-.01))
add('Creative Power Resistor','RESISTOR',value=100,nominalVoltage=120000,maxCurrent=100000,model='')
add('Power Capacitor','CAPACITOR',value=1e-6,model='',inventorySlots=['REDSTONE','DIELECTRIC'],passive=dict(kind='CAPACITOR'))
add('Creative Power Capacitor','CAPACITOR',value=.001,nominalVoltage=120000,model='')
add('Power Inductor','INDUCTOR',value=0.,model='',inventorySlots=['INDUCTOR_WINDING','FERROMAGNETIC_CORE'],passive=dict(kind='INDUCTOR'))
add('Variable inductor','INDUCTOR',value=100.,model='',inventorySlots=['FERROMAGNETIC_CORE'],passive=dict(kind='INDUCTOR',controlled=True))
add('Creative Power Inductor','INDUCTOR',value=.1,nominalVoltage=120000,model='')
add('Large Rheostat','RESISTOR','transparent',legacyId=(96<<6)+39,value=1.,model='largerheostat',modelGroups=['main','wiper'],inventorySlots=['COAL_DUST'],passive=dict(kind='RESISTOR',controlled=True,large=True))
add('Nixie Tube','NIXIE','transparent',legacyId=(117<<6)+7,model='nixietube',modelGroups=['base','display','tube'],nominalVoltage=5,maxCurrent=.1,heldModel=True)
for name,i in [('5A Switch',5),('20A Switch',20),('50A Switch',50),('100A Switch',100)]:
    add(name,'SWITCH',resistance=.005,maxCurrent=i,nominalVoltage=600)
for i in [5,10,15,20,30,40,50,60,100,125,200,400]:
    add(f'{i}A Electrical Breaker','BREAKER',resistance=.001,maxCurrent=i,nominalVoltage=600)
original_breaker=next(d for d in parts if d['id']=='5a_electrical_breaker')
original_breaker.update(name='Electrical Breaker',originalBreaker=True,inventorySlots=['VOLTAGE_CABLE'],heldModel=True)
add('Electrical Fuse Holder','FUSE_HOLDER',legacyId=(98<<6)+6,model='electricalfuse',modelGroups=['Case','Fuse','FuseType','FuseOk'],inventorySlots=['FUSE'],heldModel=True,power=0)
for v,i in [(5,10),(12,20),(12,50),(12,100)]:
    add(f'{v}V Control Relay {i}A','RELAY',value=v*.5,nominalVoltage=600,maxCurrent=i,resistance=.005)
# Original 1.24.6 contacts retain their own IDs alongside recovered later-development equipment.
contact_levels=[('Low',50,.0125,24,0x8CD6AD),('Medium',200,.05,12,0x8CBDD9),('High',800,.1,7.5,0xF5CC8F),('Very High',3200,1/15,5.625,0xDB948C)]
for sub,(name,voltage,r,current,color) in zip([1,2,0,4],contact_levels):
    add(name+' Voltage Switch','SWITCH',legacyId=(97<<6)+sub,model='lowvoltageswitch' if voltage<=200 else 'highvoltageswitch',
        modelGroups=['case','lever'],resistance=r,nominalVoltage=voltage,maxCurrent=current,power=voltage*current/1.2,
        contact=dict(relay=False,cable=slug(name)+'_voltage_cable'),heldModel=True,color=color)
for sub,(name,voltage,r,current,color) in enumerate(contact_levels+[('Signal',5,.05,.12,0xCCDED1)]):
    title=name+' Relay' if name=='Signal' else name+' Voltage Relay'
    add(title,'RELAY',legacyId=(94<<6)+sub,model='relaysmall' if name=='Signal' else 'relaybig' if voltage<=200 else 'relay800',
        modelGroups=['main','relay0','relay1','backplate'],resistance=r,nominalVoltage=voltage,maxCurrent=current,
        contact=dict(relay=True,signal=name=='Signal',cable='signal_cable' if name=='Signal' else slug(name)+'_voltage_cable'),heldModel=True,color=color)
for sub,(name,current) in enumerate([('Low',5),('Medium',20),('High',100)]):
    loss=.02*current**2;heat_capacity=loss*30/130
    add(name+' Current Relay','RELAY',legacyId=(126<<6)+sub,model='relaybig' if sub==0 else 'relay800',
        modelGroups=['main','relay0','relay1','backplate'],resistance=.01,nominalVoltage=120000,maxCurrent=current,
        contact=dict(relay=True,currentThermal=True,cable=slug(name)+'_current_cable'),heldModel=True,color=0xFFFFFF,
        thermal=dict(rs=.25/heat_capacity,rp=130/loss,capacity=heat_capacity,maximum=130.,minimum=-10.,slow=True))
for name in ['Voltage Probe','Electrical Probe']:
    add(name,'SENSOR',nominalVoltage=120000,maxCurrent=100000,value=2,inventorySlots=['ELECTRICAL_CABLE'])
for name in ['Analog vuMeter','LED vuMeter','Multicolor LED vuMeter','Digital Display']:
    add(name,'METER',nominalVoltage=120000 if name=='Digital Display' else 5,maxCurrent=100000 if name=='Digital Display' else .1,heldModel=True)
for name in ['Energy Meter','Advanced Energy Meter']:
    add(name,'ENERGY_METER',nominalVoltage=120000,maxCurrent=100000,inventorySlots=['VOLTAGE_CABLE'],heldModel=True)
add('MQTT Energy Meter','ENERGY_METER',legacyId=6284,model='mqttenergymeter',nominalVoltage=120000,maxCurrent=100000,inventorySlots=['ELECTRICAL_CABLE'],heldModel=True)
add('MQTT Signal Controller','MQTT_CONTROLLER',legacyId=6981,model='mqttsignalcontroller',nominalVoltage=5,maxCurrent=.1,heldModel=True)
add('Modbus RTU','MODBUS',legacyId=7488,model='rtu',nominalVoltage=5,maxCurrent=.1,heldModel=True)
add('Tutorial Sign','TUTORIAL_SIGN',legacyId=7496,model='tutoplate',heldModel=True,nominalVoltage=0,power=0)
add('Computer Probe','COMPUTER_PROBE','computer_probe',legacyId=0,model='',icon='blocks/computerprobe_xn',nominalVoltage=5,maxCurrent=.1,power=0)
add('Thermal Probe','THERMAL_SENSOR' ,value=1,inventorySlots=['THERMAL_CABLE'])
add('Temperature Probe','THERMAL_SENSOR',value=1,inventorySlots=['SENSOR_CABLE'])
add('Thermometer Sensor','AMBIENT_SENSOR',value=1)
for name,kind in [('Redstone-to-Voltage Converter','REDSTONE_INPUT'),('Voltage-to-Redstone Converter','REDSTONE_OUTPUT'),
    ('Electrical Daylight Sensor','DAYLIGHT_SENSOR'),('Electrical Light Sensor','LIGHT_SENSOR'),('Electrical Weather Sensor','WEATHER_SENSOR'),
    ('Signal Trimmer','SIGNAL_SOURCE'),('Signal Switch','SIGNAL_SOURCE'),('Signal Button','BUTTON'),
    ('Wireless Signal Transmitter','WIRELESS_TX'),('Wireless Signal Receiver','WIRELESS_RX'),('Signal Processor','PROCESSOR'),
    ('Data Logger','LOGGER'),('Modern Data Logger','LOGGER'),('Industrial Data Logger','LOGGER')]:
    add(name,kind,nominalVoltage=5,value=0 if name=='Signal Trimmer' else 5,maxCurrent=.1,**(dict(inventorySlots=["LOGGER_PAPER","LOGGER_PRINT"],heldModel=True) if kind=="LOGGER" else dict(inventorySlots=["REDSTONE"],heldModel=True) if kind=="PROCESSOR" else {}))
for n,(label,top,inputs) in enumerate([(x,x,1 if x=='NOT' else 3) for x in ['NOT','AND','NAND','OR','NOR','XOR','XNOR']]+
    [('PAL','PAL',3),('Schmitt Trigger','SCHMITT',1),('D Flip Flop','DFF',2),('Oscillator','OSC',1),('JK Flip Flop','JKFF',3)]):
    add(label+' Chip','LOGIC',legacyId=(118<<6)+n,model='logicgates',
        modelGroups=['Case','Output',top]+[f'Input{i}' for i in range(1,inputs+1)],nominalVoltage=5,maxCurrent=.1)
for d in parts:
    if d['id'] in ('signal_trimmer','signal_switch','signal_button'):d['heldModel']=True
    if d['behavior'] in ('WIRELESS_TX','WIRELESS_RX'):
        d['wireless']=dict(kind='TX' if d['behavior']=='WIRELESS_TX' else 'RX');d['heldModel']=True
        if d['behavior']=='WIRELESS_RX':d['value']=0.
add('Wireless Signal Repeater','WIRELESS_REPEATER',legacyId=(92<<6)+16,model='wirelesssignalrepeater',wireless=dict(kind='REPEATER'),heldModel=True,nominalVoltage=0,power=0)
for sub,name,reset in [(12,'Wireless Button',True),(16,'Wireless Switch',False)]:
    add(name,'WIRELESS_TX',legacyId=(95<<6)+sub,model='ledswitch',modelGroups=['main','button','led'],wireless=dict(kind='SOURCE',autoReset=reset),heldModel=True,nominalVoltage=0,power=0)
add('Classic Lamp Socket','LAMP',legacyId=4096,model='classiclampsocket',modelGroups=['socket'],nominalVoltage=50,power=30,maxCurrent=1,
    inventorySlots=['LIGHT_BULB','ELECTRICAL_CABLE'],lamp=dict(range=4,onOffModel=False))
parts[-1]['name']='Lamp Socket A'
for sub,name,model,reach,chain,swing,faces,paint,sideways in [
    (1,'Lamp Socket B Projector','classiclampsocket',10,0,False,None,False,True),
    (4,'Robust Lamp Socket','robustlamp',3,0,False,None,False,True),
    (5,'Flat Lamp Socket','flatlamp',3,0,False,None,False,True),
    (6,'Simple Lamp Socket','simplelamp',3,0,False,None,False,True),
    (7,'Fluorescent Lamp Socket','fluorescentlamp',4,0,False,None,False,False),
    (8,'Street Light','streetlight',0,0,False,[0],False,True),
    (9,'Sconce Lamp Socket','sconcelamp',3,0,False,[2,3,4,5],True,True),
    (12,'Suspended Lamp Socket','robustlampsuspended',3,3,True,[1],False,True),
    (13,'Long Suspended Lamp Socket','robustlampsuspended',4,7,True,[1],False,True),
    (17,'Suspended Lamp Socket (No Swing)','robustlampsuspended',3,3,False,[1],False,True),
    (18,'Long Suspended Lamp Socket (No Swing)','robustlampsuspended',4,7,False,[1],False,True)]:
    add(name,'LAMP',legacyId=(64<<6)+sub,model=model,inventorySlots=['LIGHT_BULB','ELECTRICAL_CABLE'],
        lamp=dict(range=reach,chainLength=chain,swing=swing,paintable=paint,allowedFaces=faces,sidewaysConnections=sideways,
            minimumAngle=-90. if sub==1 else 0.,maximumAngle=90. if sub==1 else 0.,onOffModel=sub!=1),
        ghosts=[[0,1,0],[0,2,0]] if sub==8 else [])
for sub,v,capacity,charging,consumption,level,cable_r in [(15,50,6000,10,5,6,.0125),(16,200,12000,25,10,8,.05)]:
    add(f'{v}V Emergency Lamp','EMERGENCY_LAMP',legacyId=(64<<6)+sub,model='emergencyexitlighting',capacity=capacity,nominalVoltage=v,resistance=cable_r,emergency=dict(chargePower=charging,consumption=consumption,lightLevel=level))
for sub,name,model in [(0,'Basic Floodlight','floodlight'),(1,'Motorized Floodlight','floodlightmotor')]:
    add(name,'FLOODLIGHT','transparent',legacyId=(68<<6)+sub,model=model,nominalVoltage=200,resistance=.05,inventorySlots=['LIGHT_BULB','LIGHT_BULB'],lamp=dict(socket='TUBE'))
for sub,name,model,groups in [
    (0,'Christmas Tree','christmas_tree',['StarOn_Star.002','Strip1_Star.000','Strip2_Star.001','Tree_Cone.006']),
    (1,'Holiday Candle','candle_light',['CandleLamp_Cylinder.001','Glass_Cylinder.000','LampOn_Cylinder.002']),
    (2,'String Lights','christmas_lights',['Lights_Cube.009','LightOn_Cube.002'])]:
    ghosts=[[-z,y,x] for x in range(3) for y in range(2) for z in range(-1,2) if (x,y,z)!=(0,0,0)]+[[0,2,1],[0,3,1]] if sub==0 else []
    add(name,'FESTIVE','transparent',legacyId=(69<<6)+sub,model=model,modelGroups=groups,nominalVoltage=200,resistance=1000,power=40,ghosts=ghosts)
add('800V Defence Turret','TURRET','transparent',legacyId=66<<6,model='turret',heldModel=True,
    modelGroups=['Turret','Holder','Joint','LeftGun','RightGun','Sensor','Fire'],nominalVoltage=800,power=1000,value=1000,inventorySlots=['ENTITY_FILTER'])
transporter_cells={(x,y,z) for x in range(-2,1) for y in range(2) for z in [-1,1]}
transporter_cells|={(-3,y,z) for y in range(2) for z in [-1,1]}
transporter_cells|={(x,2,0) for x in range(-4,0)}|{(0,1,0),(-1,0,0)}
add('Experimental Transporter','TELEPORTER','transparent',legacyId=65<<6,model='transporter',heldModel=True,
    modelGroups=['main','ext_control','ext_power','door_out','door_in','door_in_charge','indoor_open','indoor_closed','gyro','gyro_alpha','whiteblur','outlampline0_alpha','outlampline0','scr0_electrictity','scr1_cables','scr2_transporter','scr3_userin','scr5_dooropen','src4_doorclosed']+[f'led{i}' for i in range(10)],
    nominalVoltage=800,power=2000,value=2000,ghosts=[list(c) for c in sorted(transporter_cells)],
    powerPorts=[dict(offset=[-1,0,z],side=side) for z in [-1,1] for side in range(4)])
auto_miner_cells={(x,y,z) for x in range(-2,0) for y in range(-1,1) for z in range(-1,2)}
auto_miner_cells|={(1,y,z) for y in range(-1,1) for z in [-1,1]}
auto_miner_cells|={(1,0,0),(0,0,1),(0,1,0),(0,0,-1)}
auto_miner_cells.remove((-1,-1,0))
add('Auto Miner','AUTO_MINER','transparent',legacyId=42<<6,model='autominer',heldModel=True,
    modelGroups=['AutominerCore','AutominerGUI','LampSocket','LampOff','LampOn','MinerHead','MinerPipe','ButtonsFixed']+[f'Button{i}' for i in range(5)]+[f'ledA{i}' for i in range(11) if i!=3]+[f'ledP{i}' for i in range(8)],
    nominalVoltage=800,power=50,ghosts=[list(c) for c in sorted(auto_miner_cells)],inventorySlots=['MINING_DRILL','ORE_SCANNER','MINING_PIPE'],
    powerPorts=[dict(offset=[-2,-1,1],side=2),dict(offset=[-2,-1,-1],side=0)])
add('Fabricator','FABRICATOR' ,'transparent',legacyId=70<<6,model='fabricator',modelGroups=['Cube.002_Cube.013','Cube.003_Cube.012','Cube.001_Cube.010'],customItemRendering=True,nominalVoltage=200,power=1000,resistance=.0125,inventorySlots=['FABRICATOR_OUTPUT','SILICON_WAFER','COPPER_PLATE'])
add('50V Egg Incubator','INCUBATOR','transparent',legacyId=41<<6,model='eggincubator',modelGroups=['main','lamp','lampf'],heldModel=True,nominalVoltage=50,power=50,resistance=.0125,inventorySlots=['EGG'])
add('Tree Resin Collector','RESIN_COLLECTOR',legacyId=116<<6,model='treeresincolector',modelGroups=['main','fill'])
add('Lamp Supply','LAMP_SUPPLY',legacyId=65<<6,model='distributionboard',modelGroups=['base','window'],inventorySlots=['ELECTRICAL_CABLE'],value=32.)
for n,(name,top,inputs) in enumerate([
    ('OpAmp','OP',2),('PID Regulator','PID',2),('Voltage controlled sawtooth oscillator','VCO-SAW',1),
    ('Voltage controlled sine oscillator','VCO-SIN',1),('Amplifier','AMP',1),('Voltage controlled amplifier','VCA',2),
    ('Configurable summing unit','SUM',3),('Sample and hold','SAH',2),('Lowpass filter','LPF',1)]):
    add(name,'ANALOG',legacyId=(124<<6)+n,model='analogchips',modelGroups=['Case','Output',top]+[f'Input{i}' for i in range(1,inputs+1)],
        nominalVoltage=5,maxCurrent=.1,value=5 if name=='Lowpass filter' else 1,resistance=.002)
for n,name,v,p,low,high,offset,ghosts in [
    (1,'Small Solar Panel',14.75,65,.5,.5,[0,1,0],[]),
    (2,'Small Rotating Solar Panel',14.75,65,.25,.75,[0,1,0],[]),
    (3,'2x3 Solar Panel',118,520,.5,.5,[1,1,0],[[x,0,z] for x in range(2) for z in range(-1,2) if (x,z)!=(0,0)]),
    (4,'2x3 Rotating Solar Panel',118,520,.375,.625,[1,1,1],[[x,0,z] for x in range(2) for z in range(-1,2) if (x,z)!=(0,0)])]:
    add(name,'SOLAR','transparent',legacyId=(48<<6)+n,nominalVoltage=v,value=v,maxCurrent=p/v*1.5,power=p,
        resistance=v*v*.01/p/2,solar=dict(minimumAngle=low*math.pi,maximumAngle=high*math.pi,offset=offset,
        ground=[1,0,0] if ghosts else None),ghosts=ghosts,inventorySlots=['SOLAR_TRACKER'] if low!=high else [])
# BatteryDescriptor defaults; native runtime performs the original voltage-curve energy calibration.
for name,v,power,seconds,spec,half,charge in [
    ('Cost Oriented',50,250,480,'lowcost',2400,.5),('Capacity Oriented',12.5,125,3840,'capacity',2400,.5),
    ('Voltage Oriented',200,250,480,'highvoltage',2400,.5),('Current Oriented',50,1000,80,'current',2400,.5),
    ('Life Oriented',50,250,480,'life',19200,.5),('Single-use',50,500,120,'coal',19200,1.),
    ('Experimental',100,2000,120,'highvoltage',19200,.5)]:
    add(name+' Battery','BATTERY','transparent',nominalVoltage=v,value=v,power=power,maxCurrent=power*3/v,
        capacity=power*seconds,resistance=power*.04/(power/v)**2/2,initialCharge=charge,batteryHalfLife=half,
        model='batterybig',modelGroups=['main','plugPlus','plugMinus','battery_'+spec])
for name,model in [('DC-DC Converter','transformator'),('Variable DC-DC Converter','variabledcdc')]:
    add(name,'TRANSFORMER','transparent',model=model,modelGroups=['main','sbire'],value=1,nominalVoltage=120000,maxCurrent=5,
        inventorySlots=['PRIMARY_WINDING','SECONDARY_WINDING','FERROMAGNETIC_CORE','CASING'])
add('Electrical Furnace','FURNACE','transparent',nominalVoltage=50,power=1000,model='electricfurnace',modelGroups=['furnace'],
    inventorySlots=['HEATING_ELEMENT','THERMAL_INSULATION','REGULATOR'],
    thermal=dict(rs=1e9,rp=.1,capacity=40.,maximum=1e6,minimum=-273.,slow=False))
for name,kind,model,v,p in [('48V Macerator','MACERATOR','maceratora',50,200),('240V Macerator','MACERATOR','maceratorb',200,2000),
    ('48V Plate Machine','PLATE_MACHINE','platemachinea',50,200),('240V Plate Machine','PLATE_MACHINE','platemachineb',200,2000),
    ('48V Compressor','COMPRESSOR','compressora',50,200),('240V Compressor','COMPRESSOR','compressorb',200,2000)]:
    add(name,kind,'transparent',model=model,nominalVoltage=v,power=p,resistance=.0125 if v==50 else .05)
    parts[-1]['name']=name.replace('48V','50V').replace('240V','200V')
for name,v,model,sub in [('50V Magnetizer',50,'magnetizera',0),('200V Magnetizer',200,'magnetizerb',4)]:
    add(name,'MAGNETIZER','transparent',legacyId=(36<<6)+sub,model=model,nominalVoltage=v,power=200 if v==50 else 2000,resistance=.0125 if v==50 else .05)
add('Old 800V Arc Furnace','ARC_FURNACE','transparent',legacyId=34<<6,model='arcfurnaceold',nominalVoltage=800,power=10000,resistance=.1)
add('Energy Converter','FE_CONVERTER','single',legacyId=0,model='',nominalVoltage=50,power=1000)
for name,kind,model,mass,v,p in [
    ('Joint','SHAFT','straightjoint',.5,0,0),('Joint hub','SHAFT_HUB','verticalhub',5,0,0),
    ('Flywheel','FLYWHEEL','flywheel',50,0,0),('Clutch','CLUTCH','clutch',.5,0,0),
    ('Shaft Motor','SHAFT_MOTOR','motor',5,3200,1200),('Generator','SHAFT_GENERATOR','generator',5,3200,4000),
    ('Gas Turbine','GAS_TURBINE','gasturbine',5,0,8500),('Steam Turbine','STEAM_TURBINE','turbine',5,0,16250),
    ('Rolling Shaft ','ROLLING_SHAFT','platemachinea',5,0,4000)]:
    add(name,kind,'transparent',model=model,shaftMass=mass,nominalVoltage=v,power=p,maxCurrent=20)
large_shaft_cells=[[z,y,-x] for x in range(-1,2) for y in range(3) for z in range(-1,2) if [x,y,z]!=[0,0,0] and not(y==0 and x!=0)]
assert len(large_shaft_cells)==20
for sub,name,kind,model,v,p,rate in [
    (22,'Large Steam Turbine','STEAM_TURBINE','turbine',5,7200*27*2.257,7200*27),
    (23,'Large Gas Turbine','GAS_TURBINE','gasturbine',5,4*27*2130.975,4*27),
    (24,'Large Generator','SHAFT_GENERATOR','generator',12800,4000*27,0),
    (25,'Large Shaft Motor','SHAFT_MOTOR','motor',12800,1200*27,0)]:
    add(name,kind,'transparent',legacyId=(4<<6)+sub,model=model,shaftMass=135.,nominalVoltage=v,power=p,
        modelScale=3.,ghosts=large_shaft_cells,shaftPorts=[dict(offset=[1,1,0],side=1),dict(offset=[-1,1,0],side=3)])
    if rate:parts[-1]['tankCapacity']=rate
for sub,name,kind,model,p in [
    (26,'Polarized Shaft Generator','SHAFT_GENERATOR','polarizedshaftgenerator',4000),
    (27,'Polarized Shaft Motor','SHAFT_MOTOR','polarizedshaftmotor',1200)]:
    add(name,kind,'transparent',legacyId=(4<<6)+sub,model=model,shaftMass=5.,nominalVoltage=3200,power=p,shaftElectrical=dict(polarized=True))
for d in parts:
    if d['behavior'] in ('SHAFT_MOTOR','SHAFT_GENERATOR'):
        motor=d['behavior']=='SHAFT_MOTOR'
        heat=d['power'] if motor else d['power']*.05
        capacity=heat*30/130
        d['resistance']=.06666666666666667 if motor else .1
        d.setdefault('shaftElectrical',dict(polarized=False))
        d['thermal']=dict(rs=1000/capacity/2,rp=130/heat,capacity=capacity,maximum=130.,minimum=-100.,slow=True)
next(d for d in parts if d['behavior']=='ROLLING_SHAFT')['name']='Rolling Shaft Machine'
next(d for d in parts if d['behavior']=='CLUTCH')['inventorySlots']=['CLUTCH_PLATE','CLUTCH_PIN']
for d in parts:
    if d['behavior'] in ('STEAM_TURBINE','GAS_TURBINE'):
        d['nominalVoltage']=5
        d.setdefault('tankCapacity',7200 if d['behavior']=='STEAM_TURBINE' else 4)
add('Radial Motor','RADIAL_MOTOR','transparent',legacyId=(4<<6)+19,model='starter_motor',shaftMass=5.,nominalVoltage=5,power=68191.2,tankCapacity=64,ghosts=[[z,y,-x] for x in range(-1,2) for y in range(-1,2) for z in [-1,-2]])
add('Tachometer','TACHOMETER','transparent',legacyId=(4<<6)+15,model='tachometer',modelGroups=['Stand','Cowl','Shaft'],shaftMass=.5,nominalVoltage=5)
add('Fixed Shaft','FIXED_SHAFT','transparent',legacyId=(4<<6)+18,model='fixedshaft',modelGroups=['Stand','Shaft'],shaftMass=10.)
add('Crank Shaft','CRANK','transparent',legacyId=(4<<6)+20,model='straightjoint',modelGroups=['Stand','Cowl','Shaft'],shaftMass=5.,power=200.)
for sub,name,v,p in [(0,'Weak 50V Battery Charger',50,200),(1,'50V Battery Charger',50,400),(4,'200V Battery Charger',200,1000)]:
    add(name,'BATTERY_CHARGER',legacyId=(66<<6)+sub,model='batterychargera',modelGroups=['main','led0','led1','led2','led3'],nominalVoltage=v,power=p,maxCurrent=24 if v==50 else 12,resistance=.0125 if v==50 else .05,
        inventorySlots=['ELECTRICAL_ITEM']*4+['MACHINE_BOOSTER'])
add('Wind Turbine','WIND_TURBINE','transparent',legacyId=49<<6,model='windturbinemini',modelGroups=['main','rot','halo'],nominalVoltage=50,power=160,maxCurrent=160*5/59,resistance=.0125,
    ghosts=[[x,y,z] for x,y,z in [(0,1,0),(0,2,-1),(0,2,1),(0,3,-1),(0,3,1),(0,2,0),(0,3,0)]])
add('Water Turbine','WATER_TURBINE','transparent',legacyId=(49<<6)+16,model='smallwaterwheel',modelGroups=['Support','Generator','Wheel'],nominalVoltage=50,power=30,maxCurrent=30*5/59,resistance=.0125,
    ghosts=[[1,y,z] for y in range(2) for z in range(-1,2)])
for name,v,p,model in [('48V Fuel Generator',50,1200,'fuelgenerator50v'),('240V Fuel Generator',200,6000,'fuelgenerator200v')]:
    add(name,'FUEL_GENERATOR','transparent',model=model,modelGroups=['main','switch'],nominalVoltage=v,power=p,maxCurrent=p*5/(v*1.25),resistance=.0125 if v==50 else .05,tankCapacity=2000)
    parts[-1]['name']=f'{v}V Fuel Generator'

add('Small Passive Thermal Dissipator','PASSIVE_COOLER','transparent',model='passivethermaldissipatora',modelGroups=['main'],
    thermal=dict(rs=1/250,rp=30/250,capacity=250*10/30,maximum=200.,minimum=-40.,slow=False))
for name,v,p,cooling,model in [('Small Active Thermal Dissipator',50,50,800,'activethermaldissipatora'),
    ('200V Active Thermal Dissipator',200,60,1200,'200vactivethermaldissipatora')]:
    add(name,'ACTIVE_COOLER','transparent',legacyId=(64<<6)+(32 if v==50 else 34),model=model,modelGroups=['main','rot'],nominalVoltage=v,power=p,
        resistance=.0125 if v==50 else .05,
        thermal=dict(rs=1/(200+cooling),rp=30/200,capacity=(200+cooling)*10/30,maximum=130.,minimum=-40.,slow=False))
add('Stone Heat Furnace','HEAT_FURNACE','transparent',legacyId=(3<<6),model='stonefurnace',modelGroups=['main','tiroir'],
    value=1.,power=4000.,inventorySlots=['FUEL','REGULATOR','THERMAL_INSULATION','COMBUSTION_CHAMBER'],
    thermal=dict(rs=2/4000/2,rp=780/4000,capacity=4000*10/780,maximum=780.,minimum=-100.,slow=False))
add('Fuel Heat Furnace','FUEL_HEAT_FURNACE','transparent',legacyId=(3<<6)+1,model='fuelheater',
    modelGroups=['Main','BurnerA','BurnerB','BurnerC','PowerLED','HeatLED'],value=0.,power=2000.,tankCapacity=25,
    inventorySlots=['FUEL_BURNER','REGULATOR'],
    thermal=dict(rs=2/2000/2,rp=780/2000,capacity=2000*10/780,maximum=780.,minimum=-100.,slow=False))
add('Thermal Heat Exchanger','HEAT_EXCHANGER','transparent',legacyId=(65<<6)+2,model='thermal_heat_exchanger',
    modelGroups=['Plane_Plane.001'],power=16000.,tankCapacity=1000,customItemRendering=True,
    thermal=dict(rs=2/16000/2,rp=780/16000,capacity=16000*10/780,maximum=780.,minimum=-100.,slow=False))
# TurbineDescriptor's fixed reference ambient comes from the original Plains profile at tick 6000.
reference_kelvin=273.15+15+(27-15)*(.05+.95*(.6*.6*(3-2*.6)))
for name,sub,v,p,delta,capacity,model,cable_rs in [
    ('50V Turbine',1,50.,1000.,250.,25.,'turbineb',.0125),
    ('200V Turbine',8,200.,2000.,350.,50.,'turbinebblue',.05)]:
    efficiency=abs(1-reference_kelvin/(delta+reference_kelvin))
    add(name,'HEAT_TURBINE','transparent',legacyId=(4<<6)+sub,model=model,modelGroups=['main'],nominalVoltage=v,power=p,
        resistance=cable_rs*.1,heatTurbine=dict(nominalDelta=delta,wattsPerVolt=p/(v/25)),
        thermal=dict(rs=(delta/40)/(p/efficiency),rp=delta/(p/40),capacity=capacity,maximum=delta*2,minimum=-40.,slow=False))

# Original electrical cables/resistors use the 20 Hz thermal list; thermal pipes and cooling machines use 400 Hz.
for d in parts:
    if d['behavior']=='CABLE' and d['id']!='signal_cable':
        loss=2*d['maxCurrent']**2*d['resistance'];capacity=loss*30/130
        d['thermal']=dict(rs=.5/capacity/2,rp=130/loss,capacity=capacity,maximum=130.,minimum=-10. if '_current_cable' in d['id'] else -100.,slow=True)
    elif d['behavior']=='RESISTOR':
        capacity=1000*120/130
        d['thermal']=dict(rs=.5/capacity/2,rp=130/1000,capacity=capacity,maximum=130.,minimum=-100.,slow=True)
        if d.get('passive',{}).get('large'):d['thermal']=dict(rs=1/4000,rp=800/4000,capacity=4000*10/800,maximum=1000.,minimum=-100.,slow=False)
    elif d['behavior']=='BATTERY':
        loss=2*(d['power']*1.2/d['nominalVoltage'])**2*d['resistance']
        d['thermal']=dict(rs=1e9,rp=60/loss,capacity=loss*30/60/2,maximum=60.,minimum=-100.,slow=False)

# Exact registration IDs and reserved multiblock cells from 1.24.6 registerGridDevices(123).
pole_ghosts=[[0,y,0] for y in range(1,4)]
grid_ghosts=[[x,y,z] for x in range(2) for y in range(2) for z in [-1,0] if [x,y,z]!=[0,0,0]]
tower_ghosts=[[x,0,z] for x in range(-1,2) for z in range(-1,2) if x or z]+[[0,y,0] for y in range(1,9)]
switch_ghosts=[[x,y,z] for x in range(-1,2) for y in range(5) for z in range(-2,3)
    if not (x in [-1,1] and y>=2 and z in [-1,0,1]) and not (x==0 and y>=1 and z in [-2,2]) and [x,y,z]!=[0,0,0]]
for n,name,kind,model,reach,ghosts in [
    (3,'Grid DC-DC Converter','TRANSFORMER','gridconverter',8,grid_ghosts),
    (4,'Utility Pole','OVERHEAD','utilitypole',40,pole_ghosts),
    (5,'Utility Pole w/DC-DC Converter','POLE_TRANSFORMER','utilitypole',40,pole_ghosts),
    (6,'Transmission Tower','OVERHEAD','transmissiontower',96,tower_ghosts),
    (7,'Direct Utility Pole','DIRECT','utilitypole',40,pole_ghosts),
    (8,'Grid Switch','SWITCH','elngridswitch',12,switch_ghosts)]:
    add(name,'GRID','transparent',legacyId=(123<<6)+n,model=model,nominalVoltage=51200,
        resistance=.1,maxCurrent=7.5,grid=dict(kind=kind,range=reach,voltageLimit=51200,
            cable='high_voltage_cable',ghosts=ghosts,anchors={}))

materials=[]
materials.append(dict(id="electric_minecart",name="Electric Minecart",legacyId=127<<6,icon="items/electricminecart",kind="ELECTRIC_MINECART"))
brush_names=["Black","Red","Green","Brown","Blue","Purple","Cyan","Silver","Gray","Pink","Lime","Yellow","Light Blue","Magenta","Orange","White"]
for sub,title in enumerate(brush_names):
    name=title+" Brush"
    materials.append(dict(id=slug(name),name=name,legacyId=(119<<6)+sub,icon="items/"+name.replace(" ","").lower(),kind="BRUSH",variant=sub))
for sub,name,cable in [(7,'Lead Fuse for low voltage cables','low_voltage_cable'),(8,'Lead Fuse for medium voltage cables','medium_voltage_cable'),(9,'Lead Fuse for high voltage cables','high_voltage_cable'),(10,'Lead Fuse for very high voltage cables','very_high_voltage_cable'),(11,'Blown Lead Fuse',None)]:
    materials.append(dict(id=slug(name),name=name,legacyId=(98<<6)+sub,icon='items/electricalfuse' if cable else 'items/blownelectricalfuse',kind='FUSE',fuseCable=cable,hidden=cable is None,heldModel=True))
for sub,name in [(0,'Overheating Protection'),(1,'Overvoltage Protection')]:
    materials.append(dict(id=slug(name),name=name,legacyId=(5<<6)+sub,icon='items/'+name.replace(' ','').lower(),kind='RESOURCE'))
materials.append(dict(id='signal_antenna',name='Signal Antenna',legacyId=(120<<6)+33,icon='items/signalantenna',kind='RESOURCE'))
for sub,name in enumerate(['Silicon Wafer','Transistor','NTC Thermistor','Nibble Memory Chip','Arithmetic Logic Unit']):
    materials.append(dict(id=slug(name),name=name,legacyId=(126<<6)+sub,icon='items/'+name.replace(' ','').lower(),kind='RESOURCE'))
materials.append(dict(id='data_logger_print',name='Data Logger Print',legacyId=(120<<6)+32,icon='items/empty-texture',kind='DATA_LOG_PRINT',hidden=True))
for name,id,kind in [('AllMeter' ,(14<<6)+2,'ALLMETER'),('Wireless Analyser',(14<<6)+8,'WIRELESS_ANALYSER'),('Config Copy Tool',(14<<6)+16,'CONFIG_COPY'),
    ('unreleasedium',(69<<6)+5,'RESOURCE'),('Inert Canister',(69<<6)+8,'RESOURCE'),('Canister of Water',(69<<6)+11,'RESOURCE'),('Canister of Arc Water',(69<<6)+12,'RESOURCE')]:
    materials.append(dict(id=slug(name),name=name,legacyId=id,icon='items/'+name.replace(' ','').lower(),kind=kind))
for name,id in [('Copper Dust',577),('Iron Dust',578),('Lapis Dust',579),('Diamond Dust',580),
    ('Lead Dust',5),('Tungsten Dust',6),('Gold Dust',7),('Coal Dust',8),('Alloy Dust',9),('Cinnabar Dust',10),
    ('Copper Ingot',513),('Lead Ingot',516),('Tungsten Ingot',517),('Ferrite Ingot',518),('Alloy Ingot',519),('Mercury',520),
    ('Copper Plate',7686),('Iron Plate',7687),('Gold Plate',7688),('Lead Plate',7689),('Silicon Plate',7690),('Alloy Plate',7691),('Coal Plate',7692)]:
    icon='items/'+re.sub('[^a-z0-9]','',name.lower())
    if not (ASSETS/'textures'/(icon+'.png')).exists():icon='items/irondust'
    materials.append(dict(id=slug(name),name=name,legacyId=id,icon=icon))
for name,id,icon,kind,value,max_temp in [
    ('On/OFF Regulator 1 Percent',192,'onoffregulator','ON_OFF_REGULATOR',.01,0),
    ('On/OFF Regulator 10 Percent',193,'onoffregulator','ON_OFF_REGULATOR',.1,0),
    ('Analogic Regulator',200,'analogicregulator','ANALOG_REGULATOR',0,0),
    ('Combustion Chamber',384,'combustionchamber','COMBUSTION_CHAMBER',500,0),
    ('Thermal Insulation',385,'thermalinsulation','THERMAL_INSULATION',.5,500)]:
    materials.append(dict(id=slug(name),name=name,legacyId=id,icon='items/'+icon,kind=kind,value=value,maximumTemperature=max_temp))
for n,name,power,variant in [(0,'Small Fuel Burner',5000,2),(1,'Medium Fuel Burner',10000,1),(2,'Big Fuel Burner',25000,0)]:
    materials.append(dict(id=slug(name),name=name,legacyId=(124<<6)+n,icon='items/'+name.replace(' ','').lower(),kind='FUEL_BURNER',value=power,variant=variant))
for name,id in [('Electrical Motor',10<<6),('Advanced Electrical Motor',(10<<6)+1),('Cheap Chip',120<<6),('Advanced Chip',(120<<6)+1),('Machine Block',(120<<6)+2),('Electrical Probe Chip',(120<<6)+3),('Thermal Probe Chip',(120<<6)+4),('Silicon Dust',(120<<6)+16),('Advanced Machine Block',(120<<6)+23),('Synthetic Diamond',(69<<6)+4),('Arc Clay Ingot',(69<<6)+6),('Arc Metal Ingot',(69<<6)+7)]:
    materials.append(dict(id=slug(name),name=name,legacyId=id,icon='items/'+name.replace(' ','').lower(),kind='RESOURCE'))
for sub,name in [(60,'Motor Cowling'),(61,'Heavy Winding'),(62,'Reinforced Shaft'),(63,'Turbine Blade Set')]:
    materials.append(dict(id=slug(name),name=name,legacyId=(120<<6)+sub,icon='items/'+name.replace(' ','').lower(),kind='RESOURCE'))
materials.append(dict(id='solar_tracker',name='Solar Tracker',legacyId=11<<6,icon='items/solartracker',kind='SOLAR_TRACKER'))
for name,id in [('Machine Booster',(120<<6)+22),('Silicon Ingot',(120<<6)+17),('Basic Magnet',(120<<6)+28),('Advanced Magnet',(120<<6)+29),
    ('Dielectric',(120<<6)+52),('Tree Resin',64<<6),('Rubber',(64<<6)+1),('Graphite Rod',69<<6),
    ('2x Graphite Rods',(69<<6)+1),('3x Graphite Rods',(69<<6)+2),('4x Graphite Rods',(69<<6)+3)]:
    materials.append(dict(id=slug(name),name=name,legacyId=id,icon='items/'+name.replace(' ','').lower(),kind='MACHINE_BOOSTER' if name=='Machine Booster' else 'RESOURCE'))
for n,name,v,p in [
    (0,'Small 50V Copper Heating Element',50,150),(1,'50V Copper Heating Element',50,250),
    (2,'Small 200V Copper Heating Element',200,400),(3,'200V Copper Heating Element',200,600),
    (4,'Small 50V Iron Heating Element',50,180),(5,'50V Iron Heating Element',50,375),
    (6,'Small 200V Iron Heating Element',200,600),(7,'200V Iron Heating Element',200,900),
    (8,'Small 50V Tungsten Heating Element',50,240),(9,'50V Tungsten Heating Element',50,500),
    (10,'Small 200V Tungsten Heating Element',200,800),(11,'200V Tungsten Heating Element',200,1200),
    (12,'Small 800V Tungsten Heating Element',800,3600),(13,'800V Tungsten Heating Element',800,4812),
    (14,'Small 3.2kV Tungsten Heating Element',3200,4000),(15,'3.2kV Tungsten Heating Element',3200,12000)]:
    materials.append(dict(id=slug(name),name=name,legacyId=64+n,icon='items/'+name.replace(' ','').lower(),kind='HEATING_ELEMENT',value=p,nominalVoltage=v))
for n,name in enumerate(['Copper Cable','Iron Cable','Tungsten Cable']):
    materials.append(dict(id=slug(name),name=name,legacyId=(65<<6)+n,icon='items/'+name.replace(' ','').lower(),kind='RESOURCE'))
for n,name,factor in [(0,'Cheap Ferromagnetic Core',100),(1,'Average Ferromagnetic Core',50),(2,'Optimal Ferromagnetic Core',1)]:
    materials.append(dict(id=slug(name),name=name,legacyId=(7<<6)+n,icon='items/'+name.replace(' ','').lower(),kind='FERROMAGNETIC_CORE',value=factor))
for sub,name,a,b,c,d,wear,explodes in [(54,'Iron Clutch Plate',5120,640,640,160,.0001,False),(56,'Gold Clutch Plate',10240,2048,1024,512,.001,False),(57,'Copper Clutch Plate',8192,4096,1024,512,.0003,False),(58,'Lead Clutch Plate',15360,1024,1536,768,.0015,False),(59,'Coal Clutch Plate',1024,128,128,32,.1,True)]:
    materials.append(dict(id=slug(name),name=name,legacyId=(120<<6)+sub,icon='items/'+name.replace(' ','').lower(),kind='CLUTCH_PLATE',clutchPlate=dict(maximumStatic=a,minimumStatic=b,maximumDynamic=c,minimumDynamic=d,wearSpeed=wear,explodes=explodes)))
materials.append(dict(id='clutch_pin',name='Clutch Pin',legacyId=(120<<6)+55,icon='items/clutchpin',kind='CLUTCH_PIN'))
materials.append(dict(id='casing',name='Casing',legacyId=(120<<6)+53,icon='items/casing',kind='CASING'))
for sub,name,color in [(40,'Player Filter',0x00FF00),(41,'Monster Filter',0xFF0000),(42,'Animal Filter',0x4C4CFF)]:
    materials.append(dict(id=slug(name),name=name,legacyId=(120<<6)+sub,icon='items/'+name.replace(' ','').lower(),kind='ENTITY_FILTER',value=color))
for sub,name,capacity,charge,discharge,priority in [(0,'Portable Battery',40000,125,250,2),(1,'Portable Battery Pack',160000,500,1000,2),(16,'Portable Condensator',4000,2000,2000,1),(17,'Portable Condensator Pack',16000,8000,8000,1)]:
    materials.append(dict(id=slug(name),name=name,legacyId=(122<<6)+sub,icon='items/'+name.replace(' ','').lower(),kind='PORTABLE_BATTERY',electrical=dict(capacity=capacity,chargePower=charge,dischargePower=discharge,priority=priority)))
for sub,name,capacity,charge,low,near,high,far in [(0,'Small Flashlight',6000,100,10,6,12,9),(1,'Improved Flashlight',24000,400,15,8,15,13)]:
    materials.append(dict(id=slug(name),name=name,legacyId=(121<<6)+sub,icon='items/'+name.replace(' ','').lower()+'off',kind='ELECTRICAL_FLASHLIGHT',
        electrical=dict(capacity=capacity,chargePower=charge,dischargePower=0,priority=0,flashlight=dict(lightLow=low,rangeLow=near,wattsLow=20,lightHigh=high,rangeHigh=far,wattsHigh=50))))
for group,name,kind in [(16,'Ore Scanner','ORE_SCANNER_UPGRADE'),(17,'Mining Pipe','MINING_PIPE')]:
    materials.append(dict(id=slug(name),name=name,legacyId=group<<6,icon='items/'+name.replace(' ','').lower(),kind=kind))
materials.append(dict(id='x_ray_scanner',name='X-Ray Scanner',legacyId=(122<<6)+32,icon='items/x-rayscanner',kind='ELECTRICAL_SCANNER',heldModel=True,
    electrical=dict(capacity=100000,chargePower=400,dischargePower=300,priority=0,scanner=dict(watts=300,width=32,height=20,fieldOfView=math.pi/2))))
for tool in ['Sword','Pickaxe','Axe','Shovel','Hoe']:
    materials.append(dict(id='copper_'+tool.lower(),name='Copper '+tool,legacyId=-1,icon='items/copper_'+tool.lower(),kind='COPPER_TOOL'))
for prefix in ['Copper','E-Coal']:
    for suffix,slot,points in [('Helmet','HEAD',3),('Chestplate','CHEST',8),('Leggings','LEGS',6),('Boots','FEET',3)]:
        name=prefix+' '+suffix
        entry=dict(id=slug(name),name=name,legacyId=-1,icon='items/'+('copper' if prefix=='Copper' else 'ecoal')+'_'+suffix.lower(),kind='ARMOR',armor=dict(slot=slot,protection=points if prefix=='E-Coal' else dict(HEAD=2,CHEST=6,LEGS=5,FEET=2)[slot]))
        if prefix=='E-Coal':entry['electrical']=dict(capacity=8000,chargePower=2000,dischargePower=0,priority=0)
        materials.append(entry)
for sub,name,seconds,joules in [(0,'Cheap Electrical Drill',8,4000),(1,'Average Electrical Drill',5,5000),(2,'Fast Electrical Drill',3,6000),(3,'Turbo Electrical Drill',1,10000),(4,'Irresponsible Electrical Drill',.1,20000)]:
    materials.append(dict(id=slug(name),name=name,legacyId=(15<<6)+sub,icon='items/'+name.replace(' ','').lower(),kind='ELECTRICAL_DRILL',drill=dict(operationTime=seconds,operationEnergy=joules)))
for sub,name,capacity,kind in [(8,'Portable Electrical Mining Drill',120000,'DRILL'),(12,'Portable Electrical Axe',40000,'AXE')]:
    materials.append(dict(id=slug(name),name=name,legacyId=(121<<6)+sub,icon='items/'+name.replace(' ','').lower(),kind='ELECTRICAL_TOOL',
        electrical=dict(capacity=capacity,chargePower=10000,dischargePower=0,priority=0,tool=dict(type=kind,strengthOn=22,strengthOff=1,energyPerBlock=200))))
for sub,name,icon,kind,v,p,level,hours,grow,reach,socket in [
    (0,'Small 50V Incandescent Light Bulb','incandescentironlamp','INCANDESCENT',50,20,12,16,0,0,'DOUILLE'),
    (1,'50V Incandescent Light Bulb','incandescentironlamp','INCANDESCENT',50,30,14,16,0,0,'DOUILLE'),
    (2,'200V Incandescent Light Bulb','incandescentironlamp','INCANDESCENT',200,30,14,16,0,0,'DOUILLE'),
    (4,'Small 50V Carbon Incandescent Light Bulb','incandescentcarbonlamp','INCANDESCENT',50,15,11,6,0,0,'DOUILLE'),
    (5,'50V Carbon Incandescent Light Bulb','incandescentcarbonlamp','INCANDESCENT',50,25,13,6,0,0,'DOUILLE'),
    (16,'Small 50V Economic Light Bulb','fluorescentlamp','ECO',50,10,12,64,0,0,'DOUILLE'),
    (17,'50V Economic Light Bulb','fluorescentlamp','ECO',50,15,14,64,0,0,'DOUILLE'),
    (18,'200V Economic Light Bulb','fluorescentlamp','ECO',200,15,14,64,0,0,'DOUILLE'),
    (32,'50V Farming Lamp','farminglamp','INCANDESCENT',50,120,15,16,.5,0,'DOUILLE'),
    (36,'200V Farming Lamp','farminglamp','INCANDESCENT',200,120,15,16,.5,0,'DOUILLE'),
    (37,'50V LED Bulb','ledlamp','LED',50,15,14,512,0,0,'DOUILLE'),
    (38,'200V LED Bulb','ledlamp','LED',200,15,14,512,0,0,'DOUILLE'),
    (44,'50V Halogen Bulb','halogenlamp','HALOGEN',50,250,15,128,0,8,'TUBE'),
    (45,'200V Halogen Bulb','halogenlamp','HALOGEN',200,500,15,128,0,16,'TUBE')]:
    materials.append(dict(id=slug(name),name=name,legacyId=(4<<6)+sub,icon='items/'+icon,kind='LIGHT_BULB',nominalVoltage=v,value=p,
        bulb=dict(type=kind,socket=socket,nominalLight=(level+.49)/15,hours=hours,growRate=grow,range=reach)))

def write(relative,data):
    p=RES/relative;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(json.dumps(data,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
assert len({p['id'] for p in parts})==len(parts),'Duplicate component item name'
assert len({(p['family'],p['legacyId']) for p in parts})==len(parts),'Duplicate original component identity'
write('assets/eln/parts.json',parts);write('assets/eln/materials.json',materials)
write('assets/eln/original_processing_expected.json',json.loads((PORT/'tools/original_processing.json').read_text(encoding='utf-8')))

language=json.loads((ASSETS/'lang/en_us.json').read_text(encoding='utf-8'))
language.update({'block.eln.arc_clay_block':'Block of Arc Clay','block.eln.arc_metal_block':'Block of Arc Metal'})
language.update({'gui.eln.modbus_unit':'Unit','gui.eln.modbus_add_tx':'Add TX','gui.eln.modbus_add_rx':'Add RX','gui.eln.modbus_delete':'X','gui.eln.modbus_channel':'Channel','gui.eln.modbus_address':'Address','gui.eln.modbus_registered':'Unit registered','gui.eln.modbus_unassigned':'Unit unassigned or ID already used','gui.eln.modbus_page':'Page %1$s','gui.eln.previous':'<','gui.eln.next':'>'})
language.update({'gui.eln.tutorial_beacon':'Set beacon name','gui.eln.tutorial_missing':'No text associated to this beacon'})
language['message.eln.mqtt_reloaded']='MQTT endpoint configuration reloaded.'
language.update({'gui.eln.mqtt_name':'Name','gui.eln.mqtt_server':'Server','gui.eln.mqtt_id':'ID','gui.eln.mqtt_reset':'Reset','gui.eln.mqtt_mode':'%1$s: %2$s','gui.eln.mqtt_mode_0':'Disabled','gui.eln.mqtt_mode_1':'Read','gui.eln.mqtt_mode_2':'Write'})
language['tooltip.eln.signal_processor']='Calculates a 0–5 V signal from A, B and C. Fit redstone for the equation operators.'
language.update({'gui.eln.processor_redstone':'%1$s redstone required','gui.eln.processor_invalid':'Invalid equation','gui.eln.processor_empty':'Enter an equation','gui.eln.processor_inputs':'A: red   B: green   C: blue'})
language.update({'gui.eln.passive_hardware':'Hardware', 'gui.eln.passive_resistance':'Resistance: %s Ω', 'gui.eln.passive_capacitance':'Capacitance: %s F', 'gui.eln.passive_inductance':'Inductance: %s H', 'gui.eln.passive_voltage':'Maximum: %s V'})
language.update({'item.eln.empty_white_brush':'Empty White Brush','tooltip.eln.brush_uses':'Paint remaining: %s blocks','message.eln.brush_dry':'Brush is dry','message.eln.color_care_on':'Cable color matching enabled','message.eln.color_care_off':'Cable color matching disabled','tooltip.eln.painted_wire':'Paint with a brush. Wrench toggles color matching; sneak + wrench removes.','tooltip.eln.power_socket':'Mount on a wall. Shares real power with the nearest lamp supply on its channel.'})
language.update({'tooltip.eln.inventory_scanner':'Scans the block in front: tank fill, inventory fill or occupied slots. Right-click changes mode; output is on the back.','gui.eln.scanner_slots':'Occupied slots','gui.eln.scanner_simple':'Total fill','message.eln.inventory_scanner':'%s: %s%%'})
language.update({'entity.eln.electric_minecart':'Electric Minecart','message.eln.cart_energy':'Cart energy: %s J','gui.eln.rail_carts':'Connected carts: %s','tooltip.eln.electric_minecart':'Charges from overhead lines above 700 V. Stores 10 kJ; a moving cart uses up to 40 J per tick.'})
language['tooltip.eln.brush_infinite']='infinite'
language.update({'tooltip.eln.incubator':'Hatches eggs into chicks using 10–20 kJ each. Rated 50 V / 50 W; accepts automated egg input.','gui.eln.incubator_remaining':'Energy remaining: %s J'})
language['tooltip.eln.festive']='Supply about 200 V on the xmas wireless lighting channel. Draws power through a 1 kΩ load.'
language['message.eln.festive_temperature']='Not as warm as it could be'
language.update({'tooltip.eln.fabricator':'Select a chip. Each batch uses a silicon wafer, a copper plate and 4 kJ. North power input; 40 Ω load.','gui.eln.fabricator_progress':'Work: %s / 4000 J','gui.eln.fabricator_operation':'%s ×%s · %s%% yield'})
language.update({'tooltip.eln.power_antenna_tx':'Mount on a solid block, aim at a facing receiver and drive the command input with 0–5 V. Wrench rotates the rear power/feedback/command ports.','tooltip.eln.power_antenna_rx':'Face a transmitter. Rear power output and independent 0–5 V feedback input; wrench rotates the ports.','gui.eln.beam_efficiency':'Beam efficiency: %s%%','gui.eln.beam_connection':'Receiver connected: %s'})
language.update({'gui.eln.nixie_digit':'Digit: %s','gui.eln.nixie_dots':'Decimal points: %s','gui.eln.nixie_blank':'Blanked: %s'})
language.update({'gui.eln.wireless_range':'Radio range: %s blocks','gui.eln.wireless_count':'Transmitters: %s','gui.eln.wireless_state':'State: %s'})
language.update({'gui.eln.breaker_window':'Window (V)','gui.eln.breaker_cable':'Fitted cable','gui.eln.fuse_hardware':'Fuse','gui.eln.protection_heat':'Heat rise: %s °C','tooltip.eln.fuse_holder':'Fit a fuse. Empty hand removes it; sneak + right-click opens the holder.','tooltip.eln.original_breaker':'Fit a cable, set minimum/maximum input voltage and close the contact.','tooltip.eln.fuse':'Sacrificial protection for the corresponding voltage cable.','tooltip.eln.protection_resource':'Component for Electrical Breaker crafting.'})
language.update({'tooltip.eln.passive_resistor':'Set resistance with coal dust.', 'tooltip.eln.passive_capacitor':'Fit redstone for capacitance and dielectric for voltage.', 'tooltip.eln.passive_inductor':'Fit copper windings and a ferromagnetic core.', 'tooltip.eln.passive_variable_inductor':'Fit a ferromagnetic core to the variable winding.', 'tooltip.eln.passive_control':'Control with a 0–5 V signal.', 'tooltip.eln.large_rheostat':'Dissipates 4 kW at an 800 °C temperature rise; separate thermal connection.', 'tooltip.eln.nixie':'Separate digit, blanking and decimal-point signal inputs.'})
# Shared source OBJ files contain multiple alternative components, so select the original groups.
for entry in parts:
    kind=entry['behavior']
    if entry.get('passive'):entry['heldModel']=True
    if kind in ('SHAFT','SHAFT_HUB','FLYWHEEL','CLUTCH','SHAFT_MOTOR','SHAFT_GENERATOR','GAS_TURBINE','STEAM_TURBINE','ROLLING_SHAFT','CRANK','TACHOMETER','FIXED_SHAFT','RADIAL_MOTOR','FUEL_GENERATOR','WIND_TURBINE','WATER_TURBINE','BATTERY_CHARGER'):
        entry['heldModel']=True
    if kind in ['RESISTOR','CAPACITOR','INDUCTOR','DIODE'] and entry['id']!='large_rheostat':
        stem={'RESISTOR':'Resistor','CAPACITOR':'Capacitor','INDUCTOR':'Inductor','DIODE':'Diode'}[kind]
        entry['model']='powerelectricprimitives'
        entry['modelGroups']=['Base',stem+'Core',stem+'Cables']
        if kind in ['RESISTOR','INDUCTOR']:entry['modelGroups']+=[stem+'BaseExtention']
        if entry['id']=='rheostat':entry['modelGroups']+=['ResistorTrack','ResistorWiper']
from restore_visuals import restore
restore(parts)
write('assets/eln/parts.json',parts)
for entry in parts+materials:
    language['item.eln.'+entry['id']]=language.get(entry['name'].replace(' ','_')+'.name',entry['name'])
    entity_model={'parent':'builtin/entity','display':{'gui':{'rotation':[30,225,0],'scale':[.625,.625,.625]},
        'ground':{'translation':[0,3,0],'scale':[.25,.25,.25]},'fixed':{'scale':[.5,.5,.5]},
        'thirdperson_righthand':{'rotation':[75,45,0],'translation':[0,2.5,0],'scale':[.375,.375,.375]},
        'firstperson_righthand':{'rotation':[0,45,0],'translation':[0,0,0],'scale':[.4,.4,.4]},
        'firstperson_lefthand':{'rotation':[0,225,0],'translation':[0,0,0],'scale':[.4,.4,.4]}}}
    icon_model={'parent':'minecraft:item/generated','textures':{'layer0':'eln:'+entry['icon']}}
    if entry.get('kind')=='COPPER_TOOL':icon_model['parent']='minecraft:item/handheld'
    if entry.get('kind')=='ELECTRICAL_SCANNER':
        entity_model['display']['firstperson_righthand']=dict(rotation=[0,90,35],translation=[0,0,0],scale=[.8,.8,.8])
        entity_model['display']['firstperson_lefthand']=dict(rotation=[0,-90,-35],translation=[0,0,0],scale=[.8,.8,.8])
    model=entity_model if entry.get('customItemRendering') else {'loader':'forge:separate_transforms','textures':{'particle':'eln:'+entry['icon']},'base':entity_model,'perspectives':{'gui':icon_model}} if entry.get('heldModel') else icon_model
    if entry.get('kind')=='DATA_LOG_PRINT':model={'parent':'builtin/entity'}
    if entry.get('kind')=='BRUSH':
        model={'loader':'forge:separate_transforms','textures':{'particle':'eln:'+entry['icon']},'base':icon_model,'perspectives':{'gui':{'parent':'builtin/entity'}}}
    if entry.get('kind')=='ELECTRICAL_FLASHLIGHT':
        model['overrides']=[]
        for state,suffix in [(1,'on'),(2,'boosted')]:
            target=entry['id']+'_'+suffix
            write('assets/eln/models/item/'+target+'.json',dict(parent='minecraft:item/generated',textures=dict(layer0='eln:'+entry['icon'][:-3]+suffix)))
            model['overrides'].append(dict(predicate={'eln:light_state':state},model='eln:item/'+target))
    write('assets/eln/models/item/'+entry['id']+'.json',model)
for item in ['wrench','multimeter','thermometer']:
    language['item.eln.'+item]=item.title()
    icon=item if (ASSETS/f'textures/items/{item}.png').exists() else 'multimeter'
    write(f'assets/eln/models/item/{item}.json',{'parent':'minecraft:item/generated','textures':{'layer0':'eln:items/'+icon}})
for key,text in {'password':'Password','unlock':'Unlock','change_password':'Set password','counter':'Counter','prepay':'Prepay','switch_on':'On','switch_off':'Off','set_counter':'Set counter (kJ)','reset_time':'Reset time','cable':'Cable','rejected':'Incorrect password or invalid value','unlocked':'Meter unlocked'}.items():language['gui.eln.meter_'+key]=text
language['message.eln.energy_meter']='Ua: %1$s V   Ub: %2$s V   I: %3$s A   P: %4$s W'
language['tooltip.eln.energy_meter']='Fit a cable to set the meter rating. Measures energy from red to blue; prepay stops delivery when credit runs out.'
for key,text in {'config':'Configuration' ,'back':'Back to display','reset':'Reset','print':'Print','zero':'Zero line','running':'Running','paused':'Paused','min':'Minimum','max':'Maximum','period':'Period (s)'}.items():language['gui.eln.logger_'+key]=text
for i,text in enumerate(['V','A','W','°C','%','J','No unit','°F','% RH']):language['gui.eln.logger_unit_'+str(i)]=text
language['message.eln.config_unavailable']='No compatible configuration'
language['message.eln.config_copied']='Configuration copied'
language['message.eln.config_applied']='Configuration applied'
language['message.eln.wireless_analysis']='%1$s Strength=%2$s Value=%3$s%%'
language['message.eln.wireless_none']='No wireless signal in area!'
language['entity.eln.replicator']='EAReplicator' 
language['item.eln.replicator_spawn_egg']='EAReplicator Spawn Egg'
write('assets/eln/models/item/replicator_spawn_egg.json',{'parent':'minecraft:item/template_spawn_egg'})
language.update({
 'itemGroup.eln' :'Electrical Age',
 'tooltip.eln.rating':'Nominal %1$s V · %2$s A',
 'tooltip.eln.face':'Place on a block face. Wrench: rotate; sneak + wrench: remove.',
 'tooltip.eln.machine':'Connect floor cables at the base. Right-click for controls.',
 'tooltip.eln.crank':'Right-click to add shaft energy (up to 20 rad/s).',
 'tooltip.eln.bulb':'%1$s V · %2$s W · %3$s h life',
 'message.eln.meter':'%1$s V   %2$s A   %3$s W',
 'message.eln.temperature':'%1$s °C',
 'gui.eln.voltage':'U (V)','gui.eln.power':'%1$s W','gui.eln.processing':'Processing','gui.eln.idle':'Idle',
 'gui.eln.panel_angle':'Angle (°)','gui.eln.solar_angle':'Panel: %1$s°','gui.eln.solar_output':'Output: %1$s W',
 'gui.eln.furnace_manual':'Manual','gui.eln.furnace_auto':'Automatic','gui.eln.electric_furnace_slots':'Coil / Insulation / Regulator',
 'gui.eln.smelt_progress':'Smelting: %1$s%%',
 'gui.eln.solar_manual':'Manual angle · tracker slot','gui.eln.solar_tracking':'Solar tracker installed',
 'gui.eln.solar_tracker':'Solar tracker',
 'gui.eln.transformer_slots':'Windings / core / casing','gui.eln.primary':'Primary: %1$s V  %2$s A',
 'gui.eln.beam_angle':'Aim (°)','gui.eln.lamp_slots':'Bulb / cable','gui.eln.lamp_light':'Light: %1$s / 15 · %2$s W',
 'gui.eln.emergency_charge':'Charge: %1$s%% · %2$s J','gui.eln.emergency_on':'Outage: backup light on','gui.eln.emergency_off':'Power present: backup light off',
 'tooltip.eln.resin_collector':'Mount on the side of a living tree; right-click to collect resin.','message.eln.resin':'Tree resin in pot: %1$s',
 'gui.eln.fluid_intake':'Intake: %1$s mB/s',
 'gui.eln.shaft_generating':'Generating: %1$s W','gui.eln.shaft_motoring':'Motoring: %1$s W',
 'gui.eln.mechanical_efficiency':'Efficiency: %1$s%%',
 'gui.eln.radial_state':'Fuel: %1$s mB/s · efficiency %2$s%%',
 'tooltip.eln.clutch_wear':'Plate condition: %1$s%%','gui.eln.clutch_slots':'Friction plate / locking pin','gui.eln.clutch_speeds':'L: %1$s · R: %2$s rad/s','gui.eln.clutch_control':'%1$s V · wear %2$s%%','gui.eln.clutch_slipping':'Slipping','gui.eln.clutch_locked':'Shafts locked',
 'gui.eln.flood_pan':'Pan: %1$s°','gui.eln.flood_tilt':'Tilt: %1$s°','gui.eln.flood_spread':'Spread: %1$s°',
 'gui.eln.horizontal_angle':'Pan (°)','gui.eln.tilt_spread':'Tilt, spread (°)','gui.eln.floodlight_power':'Light: %1$s / 15 · %2$s W','gui.eln.floodlight_angles':'Pan %1$s° · tilt %2$s° · spread %3$s°',
 'gui.eln.lamp_life':'Bulb life: %1$s h','gui.eln.lamp_wired':'Wired','gui.eln.lamp_supply':'Supply',
 'gui.eln.power_channel':'Channel','gui.eln.signal_channel':'Control','gui.eln.aggregate':'Mode',
 'gui.eln.aggregate_max':'Max','gui.eln.aggregate_min':'Min','gui.eln.aggregate_toggle':'Toggle',
 'gui.eln.board_power':'%1$s V · %2$s W','gui.eln.board_range':'Supply range: %1$s blocks',
 'gui.eln.secondary':'Output: %1$s V  %2$s A','gui.eln.transformer_ratio':'Ratio: %1$s',
 'gui.eln.tripped':'Tripped','gui.eln.fe_stored':'Stored: %1$s FE','gui.eln.invalid_setting':'Invalid setting',
 'gui.eln.enabled':'Enabled','gui.eln.disabled':'Disabled','gui.eln.apply':'Apply',
 'gui.eln.value':'Value','gui.eln.resistance':'R (Ω)','gui.eln.channel':'Channel','gui.eln.expression':'Formula',
 'gui.eln.pin_resistance':'Pin R (Ω)','gui.eln.capacitance':'C (F)','gui.eln.inductance':'L (H)','gui.eln.ratio':'Ratio',
 'gui.eln.current':'I (A)','gui.eln.frequency':'Cutoff (Hz)','gui.eln.gain':'Gain','gui.eln.truth_table':'PAL bits',
 'gui.eln.pid_params':'Kp,Ki,Kd','gui.eln.gains':'Gains','gui.eln.energy':'%1$s Wh',
 'gui.eln.readout':'%1$s V  %2$s A  %3$s °C','gui.eln.battery':'Charge %1$s%% · Life %2$s%%',
 'gui.eln.invalid':'Invalid number (R must be > 0)'})
language.update({'gui.eln.conversion_mode':'FE: 0 out / 1 in','gui.eln.shaft':'%1$s rad/s · %2$s J',
    'gui.eln.fluid':'Tank: %1$s mB','gui.eln.display_range':'Min,max','gui.eln.display_reading':'Display: %1$s',
    'gui.eln.export_power':'Electrical → BuildCraft / FE','gui.eln.import_power':'BuildCraft / FE → electrical'})
language.update({'gui.eln.sensor_voltage':'Voltage','gui.eln.sensor_current':'Current','gui.eln.sensor_power':'Power','gui.eln.sensor_temperature':'Temperature',
    'gui.eln.sensor_both':'Absolute','gui.eln.sensor_forward':'Forward','gui.eln.sensor_reverse':'Reverse',
    'gui.eln.sensor_output':'Output: %1$s V','gui.eln.sensor_cable':'Cable'})
language.update({'gui.eln.furnace_gain':'Burn rate','gui.eln.furnace_target':'Rise (°C)',
    'gui.eln.furnace_fuel':'Add fuel','gui.eln.furnace_stopped':'Stop fuel',
    'gui.eln.furnace_external':'External','gui.eln.furnace_internal':'Internal',
    'gui.eln.furnace_slots':'Fuel / Reg. / Insul. / Chambers',
    'gui.eln.furnace_temperature':'Temperature: %1$s °C',
    'gui.eln.furnace_state':'%1$s °C · %2$s W',
    'gui.eln.fuel_heater_slots':'Burner / Regulator','gui.eln.liquid_fuel':'Fuel: %1$s mB + %2$s J',
    'gui.eln.furnace_energy':'Fuel buffer: %1$s J','tooltip.eln.thermal':'Thermal resistance: %1$s K/W · Capacity: %2$s J/K'})
language.update({'gui.eln.turbine_warm':'Hot: %1$s °C','gui.eln.turbine_cold':'Cold: %1$s °C','gui.eln.turbine_efficiency':'Efficiency: %1$s %%',
    'message.eln.turbine_temperature':'Hot: %1$s °C · Cold: %2$s °C'})
language.update({'fluid_type.eln.steam':'Steam'})
language.update({'gui.eln.exchanger_input':'Input: %1$s mB','gui.eln.exchanger_output':'Output: %1$s mB',
    'gui.eln.exchanger_flow':'Flow: %1$s → %2$s mB/t','gui.eln.exchanger_control':'Control: %1$s %%'})
write('data/forge/tags/fluids/steam.json',{'replace':False,'values':['eln:steam','eln:flowing_steam']})
language.update({'tooltip.eln.probe':'Install a cable to connect the measurement terminals. Output is a separate 0–5 V signal.',
    'tooltip.eln.grid':'Connect two terminals with %1$s. Maximum span: %2$s blocks.'})
language.update({
    'message.eln.grid.cable':'Use %1$s to connect this grid device.',
    'message.eln.grid.start':'Starting grid connection. Click the next device with this cable.',
    'message.eln.grid.cancel':'Grid connection cancelled.',
    'message.eln.grid.connected':'Grid connection added.',
    'message.eln.grid.cost':'This span requires %1$s cable items.',
    'message.eln.grid.range':'The span exceeds the connection range.',
    'message.eln.grid.duplicate':'These devices are already connected.',
    'message.eln.grid.side':'Connect to one of the device’s grid terminals.',
    'message.eln.grid.invalid':'The starting grid device is no longer available.',
    'message.eln.grid.obstructed':'The device cannot rotate into an occupied space.'})
language['death.attack.eln.flywheel']='%1$s was struck by a spinning flywheel'
language['gui.eln.wind']='Wind: %1$s m/s'
language['gui.eln.wind_exposure']='Exposure: %1$s%%'
language['gui.eln.electrical_readout']='%1$s V · %2$s A'
language['tooltip.eln.portable_energy']='Stored energy: %1$s / %2$s J'
language['tooltip.eln.portable_rates']='Charge: %1$s W · discharge: %2$s W'
language['tooltip.eln.flashlight_power']='Charge: %1$s W · normal: %2$s W · boosted: %3$s W'
language['message.eln.flashlight_off']='Flashlight OFF'
language['message.eln.flashlight_on']='Flashlight ON'
language['message.eln.flashlight_boosted']='Flashlight BOOSTED'
language['message.eln.tree_felling_on']='Tree felling enabled'
language['message.eln.tree_felling_off']='Tree felling disabled'
language['tooltip.eln.turret']='800 V · 25 W standby · 1000 J per five-damage shot · configurable entity filter and 100–5000 W recharge'
language['gui.eln.turret_spare']='Do not attack:'
language['gui.eln.turret_attack']='Attack:'
language['gui.eln.turret_power']='Power (W)'
language['gui.eln.turret_buffer']='Charge: %1$s / 1000 J'
language['gui.eln.turret_shots']='Shots: %1$s'
language['death.attack.eln_laser']='%1$s was hit by a defence turret'
language['tooltip.eln.transporter']='Experimental 800 V transporter · named destination · 2–20 kW charging · unstable power can cause a malfunction'
language['gui.eln.transporter_name']='Name'
language['gui.eln.transporter_destination']='Destination'
language['gui.eln.transporter_power']='Power (W)'
language['gui.eln.transporter_start']='Start'
language['gui.eln.transporter_work']='Charge: %1$s / %2$s J'
language['gui.eln.transporter_status']='State: %1$s'
language['gui.eln.transporter_message']='%1$s'
for index,text in enumerate(['Idle','Waiting for occupants','Closing doors','Charging','Transporting','Opening doors','Reserved','Resetting']):language['gui.eln.transporter_phase_'+str(index)]=text
for event,text in {'same':'Choose a different destination','missing':'Destination not found','multiple':'Destination name is ambiguous','power':'Supply below 640 V','busy':'Destination busy or unpowered','found':'Destination reserved','unstable':'Unstable power: malfunction','obstructed':'Door obstructed'}.items():language['gui.eln.transporter_event_'+event]=text
language['tooltip.eln.auto_miner']='800 V · finite mining pipes · original depth-dependent ore scanning · chest output at the rear foot'
language['gui.eln.auto_miner_silk']='Silk Touch: %1$s'
language['gui.eln.auto_miner_depth']='Depth: %1$s m'
language['gui.eln.auto_miner_state']='Status: %1$s'
language['gui.eln.auto_miner_work']='Work: %1$s / %2$s J'
for job,text in {'none':'Await power/drill/pipes','done':'Sleeping','full':'Pipe stack full','chestFull':'Rear chest full/missing','ore':'Drilling','pipeAdd':'Adding pipe','pipeRemove':'Retracting pipe','unloaded':'Unloaded shaft'}.items():language['gui.eln.auto_miner_job_'+job]=text
for job,text in {'none':'* Waiting opcode.','done':'- SLEEP','full':'* Pipe stack full!','chestFull':'* Storage full!','ore':'- DRILL #%1$s','pipeAdd':'- ADD PIPE #%1$s','pipeRemove':'- REMOVE PIPE #%1$s','unloaded':'* Shaft unloaded'}.items():language['gui.eln.auto_miner_log_'+job]=text
language['message.eln.drill_single']='Single-block drilling'
language['message.eln.drill_area']='Area drilling enabled'
language['tooltip.eln.electric_drill']='Digs a 3×2×3 area. Right-click to switch to a single block.'
language['tooltip.eln.electric_axe']='Fells connected trees gradually. Right-click for ordinary chopping.'
language['tooltip.eln.drill_operation']='%1$s s per operation · %2$s J · %3$s W'
language['tooltip.eln.armor_charge']='Charge: %1$s W'
language['tooltip.eln.armor_absorption']='Energy shield: %1$s%% absorption'
language['gui.eln.yes']='Yes'
language['gui.eln.no']='No'
language['gui.eln.humidity']='Relative humidity: %1$s%%'
language['gui.eln.ambient_temperature']='Ambient: %1$s °C'
language['gui.eln.motion_state']='Movement detected: %1$s'
language['gui.eln.fire_state']='Fire detected: %1$s'
language['gui.eln.sensor_powered']='Powered: %1$s'
language['gui.eln.entity_filter']='Entity filter'
language['gui.eln.sensor_battery']='Battery'
language['gui.eln.sensor_battery_charge']='Charge: %1$s%%'
language['gui.eln.sensor_stored']='%1$s J stored'
language['gui.eln.sensor_consumption']='Consumption: %1$s W'
language['gui.eln.clock_running']='Clock running'
language['gui.eln.clock_stopped']='Clock stopped'
language['gui.eln.clock_time']='Time: %1$s'
language.update({'gui.eln.relay_nc':'Normally closed','gui.eln.relay_no':'Normally open','gui.eln.contact_closed':'Contact closed','gui.eln.contact_open':'Contact open'})
language.update({'tooltip.eln.hub':'Fit four cables and select the six internal links. The two crossing links start independently connected.',
    'gui.eln.hub_link_on':'%1$s–%2$s: ON','gui.eln.hub_link_off':'%1$s–%2$s: OFF'})
language.update({'tooltip.eln.clock':'Tells the time.','tooltip.eln.clock_battery':'Requires a portable battery or condensator.',
    'tooltip.eln.timer':'A high input retriggers the adjustable countdown. Output stays high until it expires.',
    'tooltip.eln.alarm':'Emits the original alarm sound and beacon light while its input is high.'})
language.update({'gui.eln.timer_duration':'Interval (s)','gui.eln.timer_set':'Set','gui.eln.timer_reset':'Reset',
    'gui.eln.timer_remaining':'Remaining: %1$s s','gui.eln.timer_input':'Input: %1$s','gui.eln.timer_output':'Output: %1$s',
    'gui.eln.alarm_engaged':'Alarm engaged: %1$s','gui.eln.alarm_sound_on':'Sound enabled','gui.eln.alarm_sound_off':'Sound muted','gui.eln.alarm_input':'Input: %1$s V'})
language['tooltip.eln.scanner_power']='Charge: %1$s W · operating: %2$s W'
language['tooltip.eln.scanner_care']='Right-click to switch on or off. Do not use it to break blocks.'
language['message.eln.scanner_boot']='Scanner booting'
language['message.eln.scanner_run']='Scanner running'
language['message.eln.scanner_stop']='Scanner shutting down'
language['message.eln.scanner_off']='Scanner OFF'
language['message.eln.scanner_broken']='Scanner damaged'
language['gui.eln.charger_batteries']='Batteries'
language['gui.eln.charger_booster']='Booster'
language['gui.eln.charger_buffer']='Buffer: %1$s J'
language['gui.eln.charger_speed']='Charge speed: %1$s%%'
language['gui.eln.charger_efficiency']='Efficiency: %1$s%%'
language['death.attack.eln.flywheel.player']='%1$s was struck by a spinning flywheel while escaping %2$s'
language.update({'key.eln.cover':'Cover modifier','key.categories.eln':'Electrical Age'})
write('assets/eln/lang/en_us.json',language)
for p in (ASSETS/'lang').glob('*.json'):
    if p.stem=='en_us':continue
    translated=json.loads(p.read_text(encoding='utf-8'))
    for entry in parts+materials:
        old=entry['name'].replace(' ','_')+'.name'
        if old in translated:translated['item.eln.'+entry['id']]=translated[old]
    p.write_text(json.dumps(translated,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
for name in ['six_node','machine_node','grid_ghost']:
    write(f'assets/eln/blockstates/{name}.json',{'variants':{'':{'model':'eln:block/node'}}})
write('assets/eln/blockstates/light_spot.json',{'variants':{f'level={i}':{'model':'eln:block/node'} for i in range(16)}})
write('assets/eln/models/block/node.json',{'textures':{'particle':'minecraft:block/copper_block'}})

def shaped(id,pattern,keys,count=1):
    write('data/eln/recipes/'+id+'.json',{'type':'minecraft:crafting_shaped','pattern':pattern,
        'key':{k:({'tag':v[1:]} if v.startswith('#') else {'item':v}) for k,v in keys.items()},'result':{'item':'eln:'+id,'count':count}})
shaped('low_voltage_cable',['CCC'],{'C':'minecraft:copper_ingot'},12)
shaped('signal_cable',['RRR','CCC'],{'R':'minecraft:redstone','C':'minecraft:copper_ingot'},12)
for id,previous,metal in [('medium_voltage_cable','low_voltage_cable','iron_ingot'),
    ('high_voltage_cable','medium_voltage_cable','gold_ingot'),('very_high_voltage_cable','high_voltage_cable','diamond')]:
    shaped(id,['CMC'],{'C':'minecraft:'+metal,'M':'eln:'+previous},4)
for id,previous in [('low_current_cable','low_voltage_cable'),('medium_current_cable','low_current_cable'),('high_current_cable','medium_current_cable')]:
    shaped(id,['CMC'],{'C':'eln:copper_plate','M':'eln:'+previous},4)
shaped('copper_thermal_cable',['CCC','CCC'],{'C':'minecraft:copper_ingot'},8)
shaped('ground_cable',[' C ',' C ','III'],{'C':'minecraft:copper_ingot','I':'minecraft:iron_ingot'})
shaped('power_resistor',['CRC'],{'C':'minecraft:copper_ingot','R':'minecraft:coal'})
shaped('power_capacitor',['PIP','CRC'],{'P':'minecraft:paper','I':'minecraft:iron_ingot','R':'minecraft:redstone','C':'minecraft:copper_ingot'})
shaped('power_inductor',['CCC','CIC','CCC'],{'C':'minecraft:copper_ingot','I':'minecraft:iron_ingot'})
shaped('small_solar_panel',['GGG','LLL','CCC'],{'G':'minecraft:glass','L':'minecraft:lapis_lazuli','C':'minecraft:copper_ingot'})
shaped('cost_oriented_battery',['ILI','CRC','ILI'],{'I':'minecraft:iron_ingot','L':'minecraft:clay_ball','C':'minecraft:copper_ingot','R':'minecraft:redstone'})
shaped('classic_lamp_socket',[' G ',' C ','IRI'],{'G':'minecraft:glass','C':'minecraft:copper_ingot','I':'minecraft:iron_ingot','R':'minecraft:redstone'})
shaped('multimeter',[' G ','IRI',' C '],{'G':'minecraft:glass_pane','I':'minecraft:iron_ingot','R':'minecraft:redstone','C':'minecraft:copper_ingot'})
shaped('thermometer',[' G ','IRI',' C '],{'G':'minecraft:glass_pane','I':'minecraft:iron_ingot','R':'minecraft:redstone','C':'minecraft:blaze_powder'})
shaped('wrench',['I I',' I ',' I '],{'I':'minecraft:iron_ingot'})
for material in ['copper','iron','tungsten']:
    shaped(material+'_cable',['III'],{'I':'#forge:ingots/'+material},6 if material=='tungsten' else 12)
    first=f'small_50v_{material}_heating_element'
    shaped(first,['C C','CCC','C C'],{'C':'eln:'+material+'_cable'})
    chain=[first,f'50v_{material}_heating_element',f'small_200v_{material}_heating_element',f'200v_{material}_heating_element']
    if material=='tungsten':chain+=['small_800v_tungsten_heating_element','800v_tungsten_heating_element','small_3_2kv_tungsten_heating_element','3_2kv_tungsten_heating_element']
    for previous,next in zip(chain,chain[1:]):shaped(next,['CC'],{'C':'eln:'+previous})
shaped('on_off_regulator_10_percent',['R R',' R ',' I '],{'R':'minecraft:redstone','I':'eln:iron_cable'})
shaped('on_off_regulator_1_percent',['RRR',' I '],{'R':'minecraft:redstone','I':'eln:iron_cable'})
for id,core in [('electrical_furnace','furnace'),('48v_macerator','grindstone'),('48v_compressor','piston'),
    ('48v_plate_machine','anvil'),('energy_converter','redstone_block')]:
    shaped(id,['ICI','RFR','ICI'],{'I':'minecraft:iron_ingot','C':'minecraft:copper_ingot','R':'minecraft:redstone','F':'minecraft:'+core})
shaped('joint',['ICI'],{'I':'eln:iron_plate','C':'eln:copper_plate'})
shaped('joint_hub',[' J ','JJJ',' J '],{'J':'eln:joint'})
shaped('flywheel',['III','IJI','III'],{'I':'eln:iron_plate','J':'eln:joint'})
shaped('clutch',['IPI','CJC','IRI'],{'I':'eln:iron_plate','P':'minecraft:piston','C':'eln:copper_plate','J':'eln:joint','R':'minecraft:redstone'})
for id,center in [('shaft_motor','minecraft:redstone_block'),('generator','minecraft:iron_block'),
    ('gas_turbine','minecraft:furnace'),('steam_turbine','minecraft:blast_furnace'),('rolling_shaft','minecraft:piston'),
    ('48v_fuel_generator','minecraft:furnace'),('240v_fuel_generator','eln:48v_fuel_generator')]:
    shaped(id,['ICI','JFJ','ICI'],{'I':'eln:iron_plate','C':'eln:copper_plate','J':'eln:joint','F':center})
shaped('5a_switch',['CLC'],{'C':'minecraft:copper_ingot','L':'minecraft:lever'})
for id,previous,metal in [('20a_switch','5a_switch','iron_ingot'),('50a_switch','20a_switch','gold_ingot'),('100a_switch','50a_switch','diamond')]:
    shaped(id,['CMC'],{'C':'minecraft:'+metal,'M':'eln:'+previous})
shaped('5a_electrical_breaker',['ICI','CRC'],{'I':'minecraft:iron_ingot','C':'minecraft:copper_ingot','R':'minecraft:comparator'})
ratings=[5,10,15,20,30,40,50,60,100,125,200,400]
for prev,n in zip(ratings,ratings[1:]):
    shaped(f'{n}a_electrical_breaker',['CMC'],{'C':'minecraft:copper_ingot','M':f'eln:{prev}a_electrical_breaker'})

for material in ['copper','iron','gold','lead','tungsten','alloy']:
    target='minecraft:'+material+'_ingot' if material in ['copper','iron','gold'] else 'eln:'+material+'_ingot'
    write(f'data/eln/recipes/smelt_{material}_dust.json',{'type':'minecraft:smelting','ingredient':{'item':'eln:'+material+'_dust'},'result':target,'experience':0.1,'cookingtime':200})
    ingot=target
    write(f'data/eln/recipes/grind_{material}.json',{'type':'eln:processing','machine':'MACERATOR','ingredient':{'tag':'forge:ingots/'+material},'result':{'item':'eln:'+material+'_dust'},'joules':2000})
    write(f'data/forge/tags/items/dusts/{material}.json',{'replace':False,'values':['eln:'+material+'_dust']})
    if target.startswith('eln:'):write(f'data/forge/tags/items/ingots/{material}.json',{'replace':False,'values':[target]})
for material in ['iron','copper','gold']:
    energy={'copper':4000,'iron':6000,'gold':12000}[material]
    write(f'data/eln/recipes/grind_raw_{material}.json',{'type':'eln:processing','machine':'MACERATOR','ingredient':{'item':'minecraft:raw_'+material},'result':{'item':'eln:'+material+'_dust','count':2},'joules':energy})
for material in ['iron','copper','gold','lead','alloy']:
    target=('minecraft:' if material in ['iron','copper','gold'] else 'eln:')+material+'_ingot'
    write(f'data/eln/recipes/plate_{material}.json',{'type':'eln:processing','machine':'PLATE_MACHINE','ingredient':{'tag':'forge:ingots/'+material},'result':{'item':'eln:'+material+'_plate'},'joules':10000})
    write(f'data/forge/tags/items/plates/{material}.json',{'replace':False,'values':['eln:'+material+'_plate']})
for material in ['diamond','coal','lapis']:
    original='minecraft:lapis_lazuli' if material=='lapis' else 'minecraft:'+material
    write(f'data/eln/recipes/grind_{material}.json',{'type':'eln:processing','machine':'MACERATOR','ingredient':{'item':original},'result':{'item':'eln:'+material+'_dust'},'joules':3000})
    (RES/f'data/eln/recipes/compress_{material}.json').unlink(missing_ok=True)

def process(id,kind,item,result,energy,input_count=1,count=1,extra=None):
    data={'type':'eln:processing','machine':kind,'ingredient':{'tag' if item.startswith('#') else 'item':item[1:] if item.startswith('#') else item},
        'input_count':input_count,'result':{'item':result,'count':count},'joules':energy}
    if extra:data['extra_results']=extra
    write('data/eln/recipes/'+id+'.json',data)
for id,item,result,energy,n in [('compress_coal','eln:coal_dust','eln:coal_plate',40000,4),
    ('compress_graphite','eln:coal_plate','eln:graphite_rod',80000,4),('compress_dielectric','minecraft:sand','eln:dielectric',2000,1),
    ('compress_resin','#minecraft:logs','eln:tree_resin',3000,1)]:process(id,'COMPRESSOR',item,result,energy,n)
process('magnetize_iron','MAGNETIZER','minecraft:iron_ingot','eln:basic_magnet',5000,2)
process('magnetize_alloy','MAGNETIZER','eln:alloy_ingot','eln:advanced_magnet',15000,2)
process('magnetize_redstone','MAGNETIZER','eln:copper_dust','minecraft:redstone',5000)
process('plate_silicon','PLATE_MACHINE','eln:silicon_ingot','eln:silicon_plate',10000,4)
for material,result,count in [('iron','minecraft:iron_ingot',2),('gold','minecraft:gold_ingot',2),('coal','minecraft:coal',2),
    ('redstone','minecraft:redstone',6),('lapis','minecraft:lapis_block',1),('diamond','minecraft:diamond',2),('emerald','minecraft:emerald',2)]:
    process('arc_'+material,'ARC_FURNACE','#forge:ores/'+material,result,5000,count=count)
for material,energy in [('copper',4000),('iron',6000),('gold',12000),('lead',8000)]:
    process('grind_ore_'+material,'MACERATOR','#forge:ores/'+material,'eln:'+material+'_dust',energy,count=2)

# Original resource processing and common ore-dictionary aliases.
for piece,count in [('helmet',16),('boots',12),('chestplate',24),('leggings',24)]:
    process('recycle_ecoal_'+piece,'MACERATOR','eln:e_coal_'+piece,'eln:coal_dust',40000,count=count)
for kind in ['cost','life','current','voltage','capacity']:
    process('recycle_'+kind+'_battery','MACERATOR','eln:'+kind+'_oriented_battery','eln:lead_dust',50000,count=6)
process('recycle_single_use_battery','MACERATOR','eln:single_use_battery','eln:copper_dust',40000,count=3)
for name,input_tag,output_tag,count,joules in [
    ('certus_ore','ores/certus_quartz','dusts/certus_quartz',3,12000),
    ('certus_crystal','gems/certus_quartz','dusts/certus_quartz',1,6000),
    ('quartz_ore','ores/quartz','dusts/quartz',3,12000),
    ('quartz_crystal','gems/quartz','dusts/quartz',1,6000),
    ('fluix_crystal','gems/fluix','dusts/fluix',1,6000)]:
    write('data/eln/recipes/grind_optional_'+name+'.json',{'type':'eln:processing','machine':'MACERATOR','ingredient':{'tag':'forge:'+input_tag},'result':{'tag':'forge:'+output_tag,'count':count},'joules':joules})
for name,metal in [('arc_clay_block','aluminum'),('arc_metal_block','steel')]:
    write('assets/eln/blockstates/'+name+'.json',{'variants':{'':{'model':'eln:block/'+name}}})
    write('assets/eln/models/block/'+name+'.json',{'parent':'minecraft:block/cube_all','textures':{'all':'eln:blocks/'+name}})
    write('assets/eln/models/item/'+name+'.json',{'parent':'eln:block/'+name})
    write('data/eln/loot_tables/blocks/'+name+'.json',{'type':'minecraft:block','pools':[{'rolls':1,'entries':[{'type':'minecraft:item','name':'eln:'+name}],'conditions':[{'condition':'minecraft:survives_explosion'}]}]})
    write('data/forge/tags/items/storage_blocks/'+metal+'.json',{'replace':False,'values':['eln:'+name]})
    write('data/forge/tags/blocks/storage_blocks/'+metal+'.json',{'replace':False,'values':['eln:'+name]})
write('data/forge/tags/items/storage_blocks/aluminium.json',{'replace':False,'values':['eln:arc_clay_block']})
write('data/forge/tags/blocks/storage_blocks/aluminium.json',{'replace':False,'values':['eln:arc_clay_block']})
for material,energy in [('coal',4000),('lapis',8000),('diamond',8000)]:
    process('grind_'+material,'MACERATOR','minecraft:lapis_lazuli' if material=='lapis' else 'minecraft:'+material,'eln:'+material+'_dust',energy)
for id,item,result,energy,count in [('grind_sand','minecraft:sand','eln:silicon_dust',12000,1),('grind_charcoal','minecraft:charcoal','eln:coal_dust',4000,1),('grind_coal_ore','#forge:ores/coal','minecraft:coal',4000,3),('grind_ore_tungsten','#forge:ores/tungsten','eln:tungsten_dust',8000,2),('grind_ore_cinnabar','#forge:ores/cinnabar','eln:cinnabar_dust',8000,1),('grind_cobblestone','minecraft:cobblestone','minecraft:gravel',4000,1),('grind_gravel','minecraft:gravel','minecraft:flint',4000,1),('grind_dirt','minecraft:dirt','minecraft:sand',4000,1)]:process(id,'MACERATOR',item,result,energy,count=count)
process('compress_synthetic_diamond','COMPRESSOR','eln:4x_graphite_rods','eln:synthetic_diamond',80000)
process('magnetize_optimal_core','MAGNETIZER','eln:basic_magnet','eln:optimal_ferromagnetic_core',5000,3)
process('arc_clay','ARC_FURNACE','minecraft:clay_ball','eln:arc_clay_ingot',400000,2)
process('arc_steel','ARC_FURNACE','minecraft:iron_ingot','eln:arc_metal_ingot',200000)
process('arc_water_canister','ARC_FURNACE','eln:canister_of_water','eln:canister_of_arc_water',7000000)
process('magnetize_inert_canister','MAGNETIZER','eln:inert_canister','minecraft:ender_pearl',150000)
for material in ['copper','lead','tungsten']:process('arc_'+material,'ARC_FURNACE','#forge:ores/'+material,'eln:'+material+'_ingot',5000,count=2)
process('arc_quartz','ARC_FURNACE','#forge:ores/quartz','minecraft:quartz',5000,count=2)
process('arc_alloy','ARC_FURNACE','eln:alloy_dust','eln:alloy_ingot',5000)
for material in ['silicon','cinnabar']:
    result='eln:silicon_ingot' if material=='silicon' else 'eln:mercury'
    write('data/eln/recipes/smelt_'+material+'_dust.json',dict(type='minecraft:smelting',ingredient={'item':'eln:'+material+'_dust'},result=result,experience=.1,cookingtime=200))
write('data/eln/recipes/smelt_resin.json',dict(type='minecraft:smelting',ingredient={'item':'eln:tree_resin'},result={'item':'eln:rubber','count':2},experience=.1,cookingtime=200))
for tag,values in {'forge:rubber':['eln:rubber'],'forge:ingots/copper':['eln:copper_ingot'],'forge:ingots/silicon':['eln:silicon_ingot'],'forge:ingots/aluminum':['eln:arc_clay_ingot'],'forge:ingots/aluminium':['eln:arc_clay_ingot'],'forge:ingots/steel':['eln:arc_metal_ingot'],'forge:plates/silicon':['eln:silicon_plate'],'forge:plates/coal':['eln:coal_plate'],'forge:dusts/coal':['eln:coal_dust'],'forge:dusts/lapis':['eln:lapis_dust'],'forge:dusts/diamond':['eln:diamond_dust'],'forge:dusts/silicon':['eln:silicon_dust']}.items():
    domain,name=tag.split(':');write(f'data/{domain}/tags/items/{name}.json',{'replace':False,'values':values})
from restore_crafting import restore as restore_original_crafting
restore_original_crafting(parts,materials,PORT,write)
# craftBrush(): sixteen source wool variants collapse to the vanilla wool tag.
write("data/eln/recipes/empty_white_brush.json",dict(type="minecraft:crafting_shapeless",ingredients=[{"tag":"minecraft:wool"},{"item":"eln:iron_cable"}],result=dict(item="eln:white_brush",count=1,nbt="{life:0}")))
brush_dyes=["black","red","green","brown","blue","purple","cyan","light_gray","gray","pink","lime","yellow","light_blue","magenta","orange","white"]
brush_legacy_pigments={0:"ink_sac",3:"cocoa_beans",4:"lapis_lazuli",15:"bone_meal"}
for i,(name,dye) in enumerate(zip(brush_names,brush_dyes)):
    pigment={"item":"minecraft:"+dye+"_dye"}
    if i in brush_legacy_pigments:pigment=[pigment,{"item":"minecraft:"+brush_legacy_pigments[i]}]
    write("data/eln/recipes/"+slug(name+" Brush")+".json",dict(type="minecraft:crafting_shapeless",ingredients=[pigment,{"item":"eln:white_brush"}],result=dict(item="eln:"+slug(name+" Brush"),count=1)))

computer_textures={side:'eln:blocks/computerprobe_'+old for side,old in [('down','yn'),('up','yp'),('north','zn'),('south','zp'),('west','xn'),('east','xp')]}
computer_textures['particle']=computer_textures['north']
write('assets/eln/models/block/computer_probe.json',{'parent':'minecraft:block/cube','textures':computer_textures})
write('assets/eln/blockstates/computer_probe.json',{'variants':{'':{'model':'eln:block/computer_probe'}}})
write('assets/eln/models/item/computer_probe.json',{'parent':'eln:block/computer_probe'})
(PORT/'docs/native-content.json').write_text(json.dumps({'parts':parts,'materials':materials,'note':'Reviewed native definitions. Consult baseline-coverage-reconciliation.json and the original-device audits for source mapping, behavior and executed validation; registration alone does not establish functional coverage.'},indent=2)+'\n')
print(f'Generated {len(parts)} native component definitions, {len(materials)} material items, translations, models and recipes.')
exec(compile((PORT/'tools/generate_worldgen.py').read_text(encoding='utf-8'),str(PORT/'tools/generate_worldgen.py'),'exec'))
sounds=json.loads((ASSETS/'sounds.json').read_text(encoding='utf-8'))
for name,distance in [('alarma',48),('smallalarm_critical',48),('timer',4)]:
    for sound in sounds[name]['sounds']:
        if isinstance(sound,dict):sound['attenuation_distance']=distance
write('assets/eln/sounds.json',sounds)
