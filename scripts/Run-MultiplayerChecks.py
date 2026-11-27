"""Run two packaged clients and a disposable server with no desktop focus changes."""
from pathlib import Path
import ctypes as C,hashlib,json,os,shutil,socket,subprocess,time,argparse,uuid
from ctypes import wintypes as W
from verification_clients import Client,client_command,k,u
root=Path(__file__).resolve().parents[1];proof=root/'build/verification/multiplayer-performance-1112';h=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
p=argparse.ArgumentParser();p.add_argument('--case',choices=['calendar','biome','admin','thermal','control'],required=True);p.add_argument('--control-weather',action='store_true');p.add_argument('--output',type=Path,required=True);a=p.parse_args();out=a.output.resolve();assert out.is_relative_to(proof);out.mkdir(parents=True,exist_ok=False)
server=out/'server';(server/'mods').mkdir(parents=True);(server/'config').mkdir();fixture=root/'build/verification/runtime-fixture';forge='forge-1.11.2-13.20.1.2588-universal.jar'
with socket.socket() as sock:sock.bind(('127.0.0.1',0));port=sock.getsockname()[1]
for name in [forge,'minecraft_server.1.11.2.jar','eula.txt','ops.json','banned-ips.json','banned-players.json','whitelist.json']:shutil.copy2(fixture/name,server/name)
quote=lambda p:"'"+str(p).replace("'","''")+"'"
subprocess.run(['powershell','-NoProfile','-Command','New-Item -ItemType Junction -Path '+quote(server/'libraries')+' -Target '+quote((fixture/'libraries').resolve())+' | Out-Null'],check=True,creationflags=subprocess.CREATE_NO_WINDOW)
(server/'server.properties').write_text(f'level-name=world\nlevel-type=FLAT\nlevel-seed=123948\nserver-ip=127.0.0.1\nserver-port={port}\nonline-mode=false\nmax-tick-time=-1\nview-distance=3\nspawn-npcs=false\nspawn-monsters=false\nspawn-animals=false\ndifficulty=0\nenable-rcon=false\nsnooper-enabled=false\ngenerate-structures=false\nallow-flight=true\ngamemode='+('1' if a.case=='admin' else '0')+'\n')
if a.case=='thermal':
 # Keep both watched-chunk test sites on solid ground at the same altitude.
 with (server/'server.properties').open('a') as properties:properties.write('generator-settings=3;minecraft:bedrock,79*minecraft:dirt,minecraft:grass;1;\n')
offline=lambda name:str(uuid.UUID(bytes=hashlib.md5(('OfflinePlayer:'+name).encode()).digest(),version=3))
(server/'ops.json').write_text(json.dumps([dict(uuid=offline('WeatherTester1'),name='WeatherTester1',level=4,bypassesPlayerLimit=True)]))
config='general {\n S:climateMode='+('PER_BIOME' if a.case in ['biome','thermal'] else 'UNIVERSAL')+'\n S:activePreset=burlington\n}\nbiome_climates {\n S:"minecraft:jungle"=tropical_monsoon\n}\n'
if a.case=='calendar':
 # A complete test-only preset: let the production loader seed every other default.
 preset=next(v for v in json.loads((root/'src/main/resources/assets/skysrealisticweather/presets.json').read_text()) if v['id']=='burlington')
 config+='presets {\n burlington {\n S:name=Network fixture\n S:description=Deterministic network storm\n S:group=user\n D:strength=1\n S:weatherEvents <\n WINTER_STORM,0,0,0,100,0,0,8000,8000,0.8,0.8\n >\n'
 for name,values in zip(['spring','summer','autumn','winter'],preset['periods']):
  config+=name+' {\n'+''.join((' I:' if key=='days' else ' D:')+key+'='+str(value)+'\n' for key,value in zip(['days','temperature','wetness','thunder','fog'],values))+'}\n'
 config+='}\n}\n'
(server/'config/skysrealisticweather.cfg').write_text(config)
build_receipt=json.loads((root/'build/verification/probe/probe-build.json').read_text());main=next(p for p in (root/'build/libs').glob('*.jar') if h(p)==build_receipt['main_sha256']);probe=root/('build/verification/probe/weather-control-probe.jar' if a.case=='control' else 'build/verification/probe/skyweather-probe.jar');artifacts=[probe] if a.case=='control' and not a.control_weather else [main,probe]
artifact_hashes={p.name:h(p) for p in artifacts}
inputs=out/'inputs';inputs.mkdir()
for path in [Path(__file__),root/'scripts/verification_clients.py',root/'build/verification/probe/probe-build.json']:shutil.copy2(path,inputs/path.name)
for jar in artifacts:shutil.copy2(jar,server/'mods'/jar.name)
mode={'calendar':'network','biome':'biome-network','admin':'admin-network','thermal':'thermal-network','control':'control'}[a.case]
java=Path.home()/'.gradle/jdks/temurin-8-amd64-windows.2/bin/java.exe';cmd=[str(java),'-Xms128m','-Xmx1g','-XX:+UseSerialGC','-XX:ActiveProcessorCount=1','-XX:CICompilerCount=2','-Dskyweather.probe='+mode,'-Dskyweather.control='+str(a.case=='control').lower(),'-Dskyweather.controlWeather='+str(a.control_weather).lower(),'-Dskyweather.preset=burlington','-Dfile.encoding=UTF-8','-jar',forge,'nogui']
clients=[];process=None;desktop=None;start=time.monotonic();passed=False;error=None
try:
 with (server/'server.log').open('wb') as log:
  process=subprocess.Popen(cmd,cwd=server,stdin=subprocess.PIPE,stdout=log,stderr=subprocess.STDOUT,creationflags=subprocess.CREATE_NO_WINDOW|subprocess.BELOW_NORMAL_PRIORITY_CLASS);assert k.SetProcessAffinityMask(int(process._handle),1<<min(20,(os.cpu_count()or 1)-1))
  print(a.case,'server',process.pid,'port',port,flush=True);deadline=time.monotonic()+180
  marker='BIOME_NETWORK_READY' if a.case=='biome' else 'Done ('
  while marker not in (server/'server.log').read_text(encoding='utf8',errors='replace'):
   if process.poll() is not None or time.monotonic()>deadline:raise RuntimeError('Server startup failed')
   time.sleep(.25)
  desktop_name='WeatherNetwork'+str(os.getpid());desktop=u.CreateDesktopW(desktop_name,None,None,0,0x10000000,None);assert desktop,C.WinError(C.get_last_error())
  for index in [1,2]:
   target=out/f'client-{index}';(target/'mods').mkdir(parents=True)
   for jar in artifacts:shutil.copy2(jar,target/'mods'/jar.name)
   (target/'options.txt').write_text('renderDistance:3\npauseOnLostFocus:false\nenableVsync:false\nmaxFps:10\nclouds:false\nsoundCategory_master:0.0\nsoundCategory_music:0.0\n')
   props=['skyweather.port='+str(port),'skyweather.control='+str(a.case=='control').lower(),'file.encoding=UTF-8']
   if a.case=='biome':props+=['skyweather.biomeNetwork=true']
   if a.case=='admin':props+=['skyweather.adminProbe=true']
   if a.case=='thermal':props+=['skyweather.heatNetwork=true']
   c=Client(client_command(target,'WeatherTester'+str(index),props),target,'WinSta0\\'+desktop_name,min(19,(os.cpu_count()or 1)-1));clients.append((target,c));print('client',index,c.pid,'on hidden desktop',flush=True)
  deadline=time.monotonic()+260
  expected={'calendar':'CLIENT_NETWORK PASS','admin':'CLIENT_ADMIN PASS','biome':'BIOME_NETWORK PASS','thermal':'THERMAL_CLIENT PASS','control':'CONTROL_CLIENT PASS'}[a.case]
  while True:
   logs=[(d/'client.log').read_text(encoding='utf8',errors='replace') for d,c in clients];serverlog=(server/'server.log').read_text(encoding='utf8',errors='replace')
   if all(expected in s for s in logs):break
   if time.monotonic()>deadline or any(c.poll() is not None and expected not in s for s,(d,c) in zip(logs,clients)):raise RuntimeError('Client validation failed or timed out')
   if process.poll() is not None or 'Encountered an unexpected exception' in serverlog: raise RuntimeError('Server crashed; see retained server.log')
   if 'SKY_WEATHER_CHECK failed' in serverlog or any('SKY_WEATHER_CHECK failed' in s for s in logs):raise RuntimeError('Probe assertion failed')
   time.sleep(.25)
  if a.case!='biome':
   want={'calendar':'NETWORK_AND_SLEEP PASS','admin':'ADMIN_NETWORK PASS','thermal':'THERMAL_NETWORK PASS','control':'CONTROL_SERVER PASS'}[a.case]
   while want not in (server/'server.log').read_text(encoding='utf8',errors='replace'):
    if time.monotonic()>deadline or process.poll() is not None:raise RuntimeError('Server confirmation missing')
    time.sleep(.25)
  passed=True
except Exception as e:error=repr(e);print(error,flush=True)
finally:
 for d,c in clients:c.close()
 if process and process.poll() is None:
  try:process.stdin.write(b'stop\n');process.stdin.flush();process.wait(timeout=25)
  except Exception:process.terminate();process.wait()
 if desktop:u.CloseDesktop(desktop)
 result=dict(passed=passed,error=error,case=a.case,weather_in_control=a.control_weather,seconds=round(time.monotonic()-start,2),main_sha256=h(main),probe_sha256=artifact_hashes[probe.name],client_processes=[c.pid for d,c in clients],server_pid=None if process is None else process.pid,desktop='non-input desktop; never switched',muted=True,fps=10,server_cpu=20,client_cpu=19,priority='BelowNormal',artifacts=artifact_hashes,generated_config_sha256=h(server/'config/skysrealisticweather.cfg'))
 (out/'result.json').write_text(json.dumps(result,indent=2));print(json.dumps(result),flush=True)
assert passed,'Retained '+str(out)
