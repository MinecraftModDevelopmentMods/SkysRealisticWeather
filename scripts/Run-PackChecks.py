"""Pack integration checks in independent, hidden, low-priority Forge profiles."""
from pathlib import Path
import argparse,ctypes,hashlib,json,os,re,shutil,subprocess,time
from ctypes import wintypes

project=Path(__file__).resolve().parents[1]
p=argparse.ArgumentParser(description=__doc__)
p.add_argument('--profile',choices=['core','crafttweaker','tan','jei','hwyla','top','combined','full'],default='core')
p.add_argument('--phase',choices=['contracts','reload'],default='contracts')
p.add_argument('--disabled',action='store_true',help='Install optional mods with all weather bridges disabled')
p.add_argument('--output',type=Path,required=True)
args=p.parse_args();output=args.output.resolve()
assert output.is_relative_to((project/'build/verification').resolve()),'Use disposable verification output within this project'
output.mkdir(parents=True,exist_ok=True)
logpath=output/(args.phase+'.log');assert not logpath.exists(),'Preserve earlier stage evidence'
version=re.search(r'^mod_version=(.+)$',(project/'gradle.properties').read_text(),re.M).group(1)
main=project/f'build/libs/SkysRealisticWeather-{version}.jar'
probe=project/'build/verification/probe/weather-pack-probe.jar'
digest=lambda path:hashlib.sha256(path.read_bytes()).hexdigest()
receipt=json.loads((project/'build/verification/probe/probe-build.json').read_text())
assert digest(main)==receipt['main_sha256'],'Rebuild candidate'
for name,want in receipt['source_sha256'].items():assert digest(project/name)==want,'Unbuilt source: '+name
directory=output/'server'
if args.phase=='reload':
 old=json.loads((output/'contracts.json').read_text());assert old['passed'] and old['main_sha256']==digest(main) and old['probe_sha256']==digest(probe),'Reload needs matching passing contracts'
else:
 assert not directory.exists(),'Preserve prior runtime'
 directory.mkdir();(directory/'mods').mkdir();(directory/'scripts').mkdir()
 if args.disabled:
  (directory/'config').mkdir();(directory/'config/skysrealisticweather.cfg').write_text('integrations {\n'+''.join(' B:'+key+'=false\n' for key in ['crafttweaker','toughasnails','jei','waila','theoneprobe'])+'}\n')
fixture=project/'build/verification/worldgen-performance/whole-chunk-v1/1-vanilla'
forge=next(fixture.glob('forge-*-universal.jar')).name
if args.phase=='contracts':
 for name in [forge,'minecraft_server.1.10.2.jar','eula.txt','ops.json','whitelist.json','banned-ips.json','banned-players.json']:shutil.copy2(fixture/name,directory/name)
 quoted=lambda path:"'"+str(path).replace("'","''")+"'"
 subprocess.run(['powershell','-NoProfile','-Command','New-Item -ItemType Junction -Path '+quoted(directory/'libraries')+' -Target '+quoted((fixture/'libraries').resolve())+' | Out-Null'],check=True)
 props=(fixture/'server.properties').read_text()
 for key,value in {'level-name':'pack-checks','level-type':'FLAT','server-ip':'127.0.0.1','server-port':'0','online-mode':'false','enable-query':'false','enable-rcon':'false','max-tick-time':'-1','view-distance':'3','generate-structures':'false'}.items():
  pattern=r'^'+re.escape(key)+'=.*';props=re.sub(pattern,key+'='+value,props,flags=re.M) if re.search(pattern,props,re.M) else props+'\n'+key+'='+value
 (directory/'server.properties').write_text(props)
 (directory/'scripts/weather.zs').write_text('import mods.skysrealisticweather.Climate;\nClimate.exemptCrop("minecraft:wheat");\nClimate.mapBiome("minecraft:plains","TROPICAL_MONSOON");\n',encoding='utf-8')
dependencies={'crafttweaker':['CraftTweaker-1.10.2-3.0.26.jar'],'tan':['ToughAsNails-1.9.4-1.1.1.jar'],
 'jei':['jei_1.10.2-3.14.7.420.jar'],'hwyla':['Hwyla-1.8.17-B31_1.10.2.jar'],'top':['compatlayer-253729-2532012.jar','theoneprobe-1.1x-1.4.19.jar'],'core':[]}
dependencies['combined']=sum([dependencies[key] for key in ['crafttweaker','tan','jei','hwyla','top']],[])
dependencies['full']=dependencies['combined']+["Pam's HarvestCraft 1.10.2j.jar",'agricraft-2.0.0-0.11.0-a21.jar','infinitylib-0.11.0.jar','mysticalagriculture-1.10.2-1.5.10.jar','forestry_1.10.2-5.2.17.388.jar']
for name in dependencies[args.profile]:
 source=project/'build/verification/pack-support-review'/name
 if not source.exists():source=project/'build/verification/plant-growth-review'/name
 shutil.copy2(source,directory/'mods'/name)
for jar in [main,probe]:shutil.copy2(jar,directory/'mods'/jar.name)
thirdparty={name:digest(directory/'mods'/name) for name in dependencies[args.profile]}
java=Path.home()/'.gradle/jdks/temurin-8-amd64-windows.2/bin/java.exe'
command=[str(java),'-Xms128m','-Xmx1g','-XX:+UseSerialGC','-XX:ActiveProcessorCount=1','-XX:CICompilerCount=2','-Dpack.probe=true','-Dpack.disabled='+str(args.disabled).lower(),'-Dpack.profile='+args.profile,'-Dpack.reload='+str(args.phase=='reload').lower(),'-jar',forge,'nogui']
kernel=ctypes.WinDLL('kernel32',use_last_error=True);kernel.SetProcessAffinityMask.argtypes=[wintypes.HANDLE,ctypes.c_size_t];kernel.SetProcessAffinityMask.restype=wintypes.BOOL
start=time.monotonic();timeout=False
with logpath.open('wb') as log:
 process=subprocess.Popen(command,cwd=directory,stdout=log,stderr=subprocess.STDOUT,creationflags=subprocess.CREATE_NO_WINDOW|subprocess.BELOW_NORMAL_PRIORITY_CLASS)
 assert kernel.SetProcessAffinityMask(int(process._handle),1<<min(20,(os.cpu_count() or 1)-1))
 print(args.profile,args.phase,'PID',process.pid,flush=True)
 try:process.wait(timeout=600)
 except subprocess.TimeoutExpired:timeout=True;process.terminate();process.wait()
text=logpath.read_text(encoding='utf-8',errors='replace')
unchanged=all(digest(directory/'mods'/name)==want for name,want in thirdparty.items())
passed=not timeout and process.returncode==0 and 'PACK_PASS' in text and 'PACK_FAIL' not in text and unchanged
result=dict(passed=passed,profile=args.profile,phase=args.phase,bridges_disabled=args.disabled,exit_code=process.returncode,timed_out=timeout,seconds=round(time.monotonic()-start,2),main_sha256=digest(main),probe_sha256=digest(probe),third_party_sha256=thirdparty,third_party_unchanged=unchanged)
(output/(args.phase+'.json')).write_text(json.dumps(result,indent=2),encoding='utf-8')
for line in text.splitlines():
 if 'PACK_' in line:print(line,flush=True)
print(json.dumps(result),flush=True)
assert passed,'Failed; preserve '+str(logpath)
