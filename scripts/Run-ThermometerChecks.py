"""Packaged instrument/temperature-zone checks on a hidden, low-priority single-CPU Forge server."""
from pathlib import Path
import argparse, ctypes, hashlib, json, os, re, shutil, subprocess, time
from ctypes import wintypes

project=Path(__file__).resolve().parents[1]
parser=argparse.ArgumentParser(description=__doc__)
parser.add_argument('--profile',choices=['vanilla','basemetals'],default='vanilla')
parser.add_argument('--phase',choices=['contracts','reload'],default='contracts')
parser.add_argument('--check',choices=['thermometer','temperature-zones'],default='thermometer')
parser.add_argument('--output',type=Path,required=True)
args=parser.parse_args()
output=args.output.resolve(); output.mkdir(parents=True,exist_ok=True)
version=re.search(r'^mod_version=(.+)$',(project/'gradle.properties').read_text(),re.M).group(1)
main=project/('build/libs/SkysRealisticWeather-'+version+'.jar')
probe=project/'build/verification/probe/skyweather-probe.jar'
providers=project/'build/verification/thermometer/providers'
extra=[] if args.profile=='vanilla' else [providers/'base-metals-240967-9280029.jar',providers/'mmd-orespawn-245586-9518063.jar']
digest=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
build=json.loads((project/'build/verification/probe/probe-build.json').read_text())
assert digest(main)==build['main_sha256'] and digest(probe)==build['probe_sha256'],'Rebuild candidate/probe first'
for name,expected in build['source_sha256'].items():assert digest(project/name)==expected,'Unbuilt source: '+name
logpath=output/(args.phase+'.log')
if logpath.exists():raise FileExistsError('Keeping '+str(logpath))
for path in output.glob('*.json'):
    old=json.loads(path.read_text())
    assert old['main_sha256']==digest(main) and old['probe_sha256']==digest(probe) and old['profile']==args.profile and old.get('check','thermometer')==args.check,'Candidate/profile changed'
if args.phase=='reload':assert json.loads((output/'contracts.json').read_text())['passed'],'Reload requires passing contracts'
directory=output/'server'; (directory/'mods').mkdir(parents=True,exist_ok=True)
fixture=project/'build/verification/worldgen-performance/whole-chunk-v1/1-vanilla'
forge=next(fixture.glob('forge-*-universal.jar')).name
for name in [forge,'minecraft_server.1.10.2.jar','eula.txt','ops.json','whitelist.json','banned-ips.json','banned-players.json']:
    if not (directory/name).exists():shutil.copy2(fixture/name,directory/name)
if not (directory/'libraries').exists():
    quote=lambda p:"'"+str(p).replace("'","''")+"'"
    subprocess.run(['powershell','-NoProfile','-Command','New-Item -ItemType Junction -Path '+quote(directory/'libraries')+' -Target '+quote((fixture/'libraries').resolve())+' | Out-Null'],check=True)
properties=(fixture/'server.properties').read_text()
for key,value in {'level-type':'FLAT','level-name':'thermometer-checks','server-port':'0','max-tick-time':'-1','view-distance':'3','spawn-npcs':'false','generate-structures':'false'}.items():
    properties=re.sub(r'^'+key+r'=.*',key+'='+value,properties,flags=re.M)
(directory/'server.properties').write_text(properties)
for path in [main,probe,*extra]:shutil.copy2(path,directory/'mods'/path.name)
originals={path.name:digest(path) for path in extra}
java=Path.home()/'.gradle/jdks/temurin-8-amd64-windows.2/bin/java.exe'
command=[str(java),'-Xms128m','-Xmx1024m','-XX:+UseSerialGC','-XX:ActiveProcessorCount=1','-XX:CICompilerCount=2',
    '-Dskyweather.probe='+args.check,'-Dthermometer.reload='+str(args.phase=='reload').lower(),'-Dzones.reload='+str(args.phase=='reload').lower(),
    '-Dthermometer.provider='+str(args.profile=='basemetals').lower(),'-jar',forge,'nogui']
kernel=ctypes.WinDLL('kernel32',use_last_error=True)
kernel.SetProcessAffinityMask.argtypes=[wintypes.HANDLE,ctypes.c_size_t];kernel.SetProcessAffinityMask.restype=wintypes.BOOL
cpu=min(20,(os.cpu_count() or 1)-1);start=time.monotonic()
with logpath.open('wb') as log:
    process=subprocess.Popen(command,cwd=directory,stdout=log,stderr=subprocess.STDOUT,
        creationflags=subprocess.CREATE_NO_WINDOW|subprocess.BELOW_NORMAL_PRIORITY_CLASS)
    if not kernel.SetProcessAffinityMask(int(process._handle),1<<cpu):process.terminate();raise ctypes.WinError(ctypes.get_last_error())
    print(args.profile,args.phase,'PID',process.pid,'BelowNormal CPU',cpu,flush=True)
    timed_out=False
    try:process.wait(timeout=300)
    except subprocess.TimeoutExpired:timed_out=True;process.terminate();process.wait()
text=logpath.read_text(encoding='utf-8',errors='replace')
unchanged=all(digest(path)==originals[path.name]==digest(directory/'mods'/path.name) for path in extra)
marker='ZONES' if args.check=='temperature-zones' else 'THERMOMETER'
passed=not timed_out and process.returncode==0 and marker+'_PASS' in text and marker+'_FAILED' not in text and unchanged
result=dict(passed=passed,profile=args.profile,phase=args.phase,check=args.check,exit_code=process.returncode,seconds=round(time.monotonic()-start,2),
    command=command,cpu=cpu,priority='BELOW_NORMAL',main_sha256=digest(main),probe_sha256=digest(probe),additional_mods=originals,original_mods_unchanged=unchanged)
(output/(args.phase+'.json')).write_text(json.dumps(result,indent=2),encoding='utf-8')
for line in text.splitlines():
    if marker+'_' in line:print(line,flush=True)
if not passed:raise RuntimeError('Failed; retained '+str(logpath))
