"""Check the real JEI client UI on a non-input desktop, using disposable worlds."""
from pathlib import Path
import argparse,ctypes,hashlib,json,os,shutil,socket,subprocess,time
from verification_clients import Client,client_command,k,u

root=Path(__file__).resolve().parents[1];digest=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
parser=argparse.ArgumentParser(description=__doc__)
parser.add_argument('--profile',choices=['vanilla','combined','disabled'],required=True)
parser.add_argument('--output',type=Path,required=True)
parser.add_argument('--assets',type=Path,default=Path(os.environ.get('WEATHER_CLIENT_ASSETS',str(Path(os.environ['APPDATA'])/'.minecraft/assets'))),help='Complete launcher assets; read only')
args=parser.parse_args();output=args.output.resolve();assert output.is_relative_to(root/'build/verification');output.mkdir(parents=True,exist_ok=False)
assets=args.assets.resolve();index=assets/'indexes/1.11.json'
assert hashlib.sha1(index.read_bytes()).hexdigest()=='c64959fe73672e9b053b157f57b6aba318d0b3b5','Need the original complete 1.11 assets'
objects=json.loads(index.read_text())['objects']
for item in objects.values():
 f=assets/'objects'/item['hash'][:2]/item['hash'];assert f.is_file() and hashlib.sha1(f.read_bytes()).hexdigest()==item['hash'],'Missing or invalid asset: '+str(f)
os.environ['WEATHER_CLIENT_ASSETS']=str(assets)
build=json.loads((root/'build/verification/probe/probe-build.json').read_text())
for name,h in build['source_sha256'].items():assert digest(root/name)==h,'Rebuild source: '+name
main=next(f for f in (root/'build/libs').glob('*.jar') if digest(f)==build['main_sha256'])
probe=root/'build/verification/probe/skyweather-probe.jar'
mods=[main,probe,root/'build/compatibility-mods/published/JEI.jar']
if args.profile=='combined':mods += [root/'build/compatibility-mods/published'/name for name in ['HarvestCraft.jar','MysticalAgriculture.jar','Forestry.jar']]
hashes={f.name:digest(f) for f in mods};server=output/'server';client=output/'client'
fixture=root/'build/verification/runtime-fixture';forge='forge-1.11.2-13.20.1.2588-universal.jar'
for directory in [server,client]:
 (directory/'mods').mkdir(parents=True);(directory/'config').mkdir()
 for f in mods:shutil.copy2(f,directory/'mods'/f.name)
 if args.profile=='disabled':(directory/'config/skysrealisticweather.cfg').write_text('integrations {\n B:jei=false\n}\n')
for name in [forge,'minecraft_server.1.11.2.jar','eula.txt','whitelist.json','banned-ips.json','banned-players.json']:shutil.copy2(fixture/name,server/name)
(server/'ops.json').write_text('[]')
quote=lambda p:"'"+str(p).replace("'","''")+"'"
subprocess.run(['powershell','-NoProfile','-Command','New-Item -ItemType Junction -Path '+quote(server/'libraries')+' -Target '+quote((fixture/'libraries').resolve())+' | Out-Null'],check=True,creationflags=subprocess.CREATE_NO_WINDOW)
with socket.socket() as sock:sock.bind(('127.0.0.1',0));port=sock.getsockname()[1]
(server/'server.properties').write_text(f'level-type=FLAT\nlevel-name=world\nlevel-seed=123948\nserver-ip=127.0.0.1\nserver-port={port}\nonline-mode=false\nmax-tick-time=-1\nview-distance=3\nspawn-npcs=false\nspawn-monsters=false\nspawn-animals=false\ndifficulty=0\ngenerate-structures=false\nallow-flight=true\nsnooper-enabled=false\ngenerator-settings=3;minecraft:bedrock,79*minecraft:dirt,minecraft:grass;1;\n')
config=server/'config/skysrealisticweather.cfg'
with config.open('a') as f:f.write('plant_growth {\n S:mappings <\n minecraft:wheat=temperate\n >\n}\n')
(client/'options.txt').write_text('renderDistance:3\nguiScale:2\npauseOnLostFocus:false\nenableVsync:false\nmaxFps:10\nclouds:false\nsoundCategory_master:0.0\nsoundCategory_music:0.0\n')
inputs=output/'inputs';inputs.mkdir()
for f in [Path(__file__),root/'scripts/verification_clients.py',root/'build/verification/probe/probe-build.json',*list((root/'src/integrationTest/java/zone/moddev/mc/skysrealisticweather/verify/jei').glob('*.java'))]:shutil.copy2(f,inputs/f.name)
java=Path.home()/'.gradle/jdks/temurin-8-amd64-windows.2/bin/java.exe'
command=[str(java),'-Xms128m','-Xmx1g','-XX:+UseSerialGC','-XX:ActiveProcessorCount=1','-XX:CICompilerCount=2','-Dskyweather.probe=jei-client','-jar',forge,'nogui']
process=None;c=None;desktop=None;passed=False;error=None;start=time.monotonic()
try:
 with (server/'server.log').open('wb') as log:
  process=subprocess.Popen(command,cwd=server,stdin=subprocess.PIPE,stdout=log,stderr=subprocess.STDOUT,creationflags=subprocess.CREATE_NO_WINDOW|subprocess.BELOW_NORMAL_PRIORITY_CLASS)
  assert k.SetProcessAffinityMask(int(process._handle),1<<min(20,(os.cpu_count()or 1)-1));print('JEI server',process.pid,flush=True)
  deadline=time.monotonic()+180
  while 'JEI_SERVER_READY' not in (server/'server.log').read_text(errors='replace'):
   if process.poll() is not None or time.monotonic()>deadline:raise RuntimeError('Server startup failed; see retained log')
   time.sleep(.25)
  name='WeatherJei'+str(os.getpid());desktop=u.CreateDesktopW(name,None,None,0,0x10000000,None);assert desktop,ctypes.WinError(ctypes.get_last_error())
  c=Client(client_command(client,'JeiTester',['skyweather.jeiClient=true','skyweather.port='+str(port),'jeiprobe.disabled='+str(args.profile=='disabled').lower(),'file.encoding=UTF-8']),client,'WinSta0\\'+name,min(19,(os.cpu_count()or 1)-1))
  print('JEI client',c.pid,'hidden, muted, 10 FPS',flush=True);deadline=time.monotonic()+420
  while True:
   clientlog=(client/'client.log').read_text(encoding='utf8',errors='replace');serverlog=(server/'server.log').read_text(encoding='utf8',errors='replace')
   if 'JEI_CLIENT PASS' in clientlog and 'JEI_SERVER PASS' in serverlog:passed=True;break
   if c.poll() is not None or process.poll() is not None or time.monotonic()>deadline or 'JEI_CLIENT_FAIL' in clientlog:raise RuntimeError('JEI check failed or timed out; see retained logs')
   time.sleep(.25)
except Exception as e:error=repr(e);print(error,flush=True)
finally:
 if c:c.close()
 if process and process.poll() is None:
  try:process.stdin.write(b'stop\n');process.stdin.flush();process.wait(timeout=25)
  except Exception:process.terminate();process.wait()
 if desktop:u.CloseDesktop(desktop)
 unchanged=all(digest(d/'mods'/f.name)==hashes[f.name] for d in [server,client] for f in mods)
 passed=passed and unchanged
 result=dict(passed=passed,error=error,profile=args.profile,main_sha256=digest(main),probe_sha256=digest(probe),artifacts=hashes,original_jars_unchanged=unchanged,assets_index_sha1=hashlib.sha1(index.read_bytes()).hexdigest(),verified_asset_objects=len(objects),seconds=round(time.monotonic()-start,2),server_pid=None if process is None else process.pid,client_pid=None if c is None else c.pid,priority='BelowNormal',server_cpu=20,client_cpu=19,fps=10,muted=True,desktop='non-input; never switched',inputs_sha256={f.name:digest(f) for f in inputs.iterdir()})
 (output/'result.json').write_text(json.dumps(result,indent=2));print(json.dumps(result),flush=True)
assert passed,'Retained failed JEI attempt: '+str(output)
