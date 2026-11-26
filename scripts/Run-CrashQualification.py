"""Crash-focused, disposable full-world or synthetic Forge qualification; never launches the source."""
from pathlib import Path
import argparse,ctypes,hashlib,json,os,re,shutil,subprocess,time
from ctypes import wintypes
project=Path(__file__).resolve().parents[1]
p=argparse.ArgumentParser(description=__doc__)
p.add_argument('--case',choices=['edge','stress','sylvester'],required=True)
p.add_argument('--variant',choices=['control','weather'],default='weather')
p.add_argument('--phase',choices=['initial','reload'],default='initial')
p.add_argument('--ticks',type=int,default=6000)
p.add_argument('--output',type=Path,required=True)
args=p.parse_args();output=args.output.resolve()
proof=project/'build/verification/crash-qualification';snapshot=proof/'input-sylvester'
for protected in [Path('D:/MinecraftMineralogy/1 Sylvester').resolve(),snapshot.resolve()]:
 if output==protected or protected in output.parents:raise ValueError('Never write qualification output into the protected source or snapshot')
if args.phase=='initial' and (output/'server').exists():raise FileExistsError('Preserving an existing runtime; choose fresh output')
output.mkdir(parents=True,exist_ok=True)
version=re.search(r'^mod_version=(.+)$',(project/'gradle.properties').read_text(encoding='utf-8'),re.M).group(1)
main=project/('build/libs/SkysRealisticWeather-'+version+'.jar');probe=project/'build/verification/probe/weather-qualification-probe.jar'
def digest(path):
 h=hashlib.sha256()
 with path.open('rb') as f:
  for data in iter(lambda:f.read(8*1024*1024),b''):h.update(data)
 return h.hexdigest()
receipt=json.loads((project/'build/verification/probe/probe-build.json').read_text(encoding='utf-8'))
assert digest(main)==receipt['main_sha256'],'Rebuild main candidate'
for name,want in receipt['source_sha256'].items():assert digest(project/name)==want,'Unbuilt source: '+name
logpath=output/(args.phase+'.log');assert not logpath.exists(),'Preserving existing stage log'
for previous in output.glob('*.json'):
 old=json.loads(previous.read_text(encoding='utf-8'));assert old['case']==args.case and old['variant']==args.variant and old['probe_sha256']==digest(probe) and (args.variant=='control' or old['candidate_sha256']==digest(main)),'Candidate/profile changed; use a fresh output'
if args.phase=='reload':assert json.loads((output/'initial.json').read_text(encoding='utf-8'))['passed'],'Reload needs passing initial run'
server=output/'server';fixture=project/'build/verification/worldgen-performance/whole-chunk-v1/1-vanilla'
forge=next(fixture.glob('forge-*-universal.jar')).name
excluded={'OreSpawn-4.0.8.110021.jar':'duplicate older OreSpawn; saved world records 4.0.16','ironagefurniture+1.10.2+v0.2.0.5.jar':'duplicate older furniture; saved world records 0.3.0','OptiFine_1.10.2_HD_U_E7.jar':'client-only renderer; no dedicated-server coverage'}
if not server.exists():
 server.mkdir();(server/'mods').mkdir()
 if args.case=='sylvester':
  shutil.copytree(snapshot/'world',server/'world');shutil.copytree(snapshot/'config',server/'config')
  for source in (snapshot/'mods').rglob('*.jar'):
   if source.name in excluded:continue
   target=server/'mods'/source.relative_to(snapshot/'mods');target.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(source,target)
  for name in ['biomessetting.txt','harvestcraft.cfg']:
   if(snapshot/name).exists():shutil.copy2(snapshot/name,server/name)
  backup=server/'config/aroma1997/AromaBackup.cfg';text=backup.read_text(encoding='utf-8');text=re.sub(r'S:location=.*','S:location=backups',text);text=text.replace('B:onStartup=true','B:onStartup=false');text=re.sub(r'I:delay=\d+','I:delay=0',text);backup.write_text(text,encoding='utf-8')
 else:
  for name in ["Pam's HarvestCraft 1.10.2j.jar",'agricraft-2.0.0-0.11.0-a21.jar','infinitylib-0.11.0.jar','mysticalagriculture-1.10.2-1.5.10.jar','forestry_1.10.2-5.2.17.388.jar']:
   shutil.copy2(project/'build/verification/plant-growth-review'/name,server/'mods'/name)
 for name in [forge,'minecraft_server.1.10.2.jar','eula.txt','ops.json','whitelist.json','banned-ips.json','banned-players.json']:shutil.copy2(fixture/name,server/name)
 quoted=lambda s:"'"+str(s).replace("'","''")+"'"
 subprocess.run(['powershell','-NoProfile','-Command','New-Item -ItemType Junction -Path '+quoted(server/'libraries')+' -Target '+quoted((fixture/'libraries').resolve())+' | Out-Null'],check=True)
 props=(snapshot/'server.properties' if args.case=='sylvester' else fixture/'server.properties').read_text(encoding='utf-8')
 changes={'level-name':'world','server-ip':'127.0.0.1','server-port':'0','online-mode':'false','enable-rcon':'false','enable-query':'false','view-distance':'4','max-tick-time':'-1','snooper-enabled':'false'}
 if args.case!='sylvester':changes.update({'level-type':'FLAT','generate-structures':'false','spawn-npcs':'false'})
 for key,value in changes.items():
  pattern=r'^'+re.escape(key)+'=.*'
  props=re.sub(pattern,key+'='+value,props,flags=re.M) if re.search(pattern,props,re.M) else props+'\n'+key+'='+value
 (server/'server.properties').write_text(props,encoding='utf-8')
 for name in ['target-chunks.tsv','crop-points.tsv']:shutil.copy2(proof/name,server/name)
 for path in (server/'config').rglob('forge.cfg'):
  text=path.read_text(encoding='utf-8');text=text.replace('B:removeErroringEntities=true','B:removeErroringEntities=false').replace('B:removeErroringTileEntities=true','B:removeErroringTileEntities=false');path.write_text(text,encoding='utf-8')
if args.variant=='weather':shutil.copy2(main,server/'mods'/main.name)
shutil.copy2(probe,server/'mods'/probe.name)
third_party={str(path.relative_to(server/'mods')):digest(path) for path in (server/'mods').rglob('*.jar') if path.name not in [main.name,probe.name]}
phase='soak' if args.case=='sylvester' else args.case
java=Path.home()/'.gradle/jdks/temurin-8-amd64-windows.2/bin/java.exe'
command=[str(java),'-Xms256m','-Xmx3g' if args.case=='sylvester' else '-Xmx1g','-XX:+UseSerialGC','-XX:ActiveProcessorCount=1','-XX:CICompilerCount=2','-Dcrash.probe=true','-Dcrash.phase='+phase,'-Dcrash.ticks='+str(args.ticks),'-jar',forge,'nogui']
kernel=ctypes.WinDLL('kernel32',use_last_error=True);kernel.SetProcessAffinityMask.argtypes=[wintypes.HANDLE,ctypes.c_size_t];kernel.SetProcessAffinityMask.restype=wintypes.BOOL
start=time.monotonic();timed_out=False
with logpath.open('wb') as log:
 process=subprocess.Popen(command,cwd=server,stdout=log,stderr=subprocess.STDOUT,creationflags=subprocess.CREATE_NO_WINDOW|subprocess.BELOW_NORMAL_PRIORITY_CLASS)
 assert kernel.SetProcessAffinityMask(int(process._handle),1<<min(20,(os.cpu_count() or 1)-1))
 print(args.case,args.variant,args.phase,'PID',process.pid,'single CPU, below normal',flush=True)
 try:process.wait(timeout=1800)
 except subprocess.TimeoutExpired:timed_out=True;process.terminate();process.wait()
text=logpath.read_text(encoding='utf-8',errors='replace');unchanged=all(digest(server/'mods'/name)==value for name,value in third_party.items())
passed=not timed_out and process.returncode==0 and 'QUALIFY_PASS' in text and 'QUALIFY_FAIL' not in text and unchanged
result={'passed':passed,'case':args.case,'variant':args.variant,'phase':args.phase,'seconds':round(time.monotonic()-start,2),'exit_code':process.returncode,'timed_out':timed_out,'candidate_sha256':digest(main),'probe_sha256':digest(probe),'candidate_loaded':args.variant=='weather','third_party_sha256':third_party,'third_party_unchanged':unchanged,'excluded_source_jars':excluded if args.case=='sylvester' else {},'command':command,'pid':process.pid}
(output/(args.phase+'.json')).write_text(json.dumps(result,indent=2),encoding='utf-8')
for line in text.splitlines():
 if 'QUALIFY_' in line:print(line,flush=True)
if not passed:raise RuntimeError('Failed, preserved '+str(logpath))
