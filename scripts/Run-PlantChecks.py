"""Packaged natural plant-growth checks, using one low-priority CPU without a client window."""
from pathlib import Path
import argparse
import ctypes
from ctypes import wintypes
import datetime
import hashlib
import json
import os
import re
import shutil
import subprocess
import time
import zipfile

project = Path(__file__).resolve().parents[1]
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--profile', choices=['vanilla','harvestcraft','mystical','forestry','combined'], default='vanilla')
parser.add_argument('--phase', choices=['contracts','reload','endurance','performance'], default='contracts')
parser.add_argument('--years', type=int, default=5)
parser.add_argument('--seeds', type=int, nargs='+', default=[123948,70289,91173])
parser.add_argument('--climates', nargs='+', default=['all'])
parser.add_argument('--output', type=Path, required=True)
args = parser.parse_args()
version = re.search(r'^mod_version=(.+)$', (project/'gradle.properties').read_text(), re.M).group(1)
args.jar = project/('build/libs/SkysRealisticWeather-'+version+'.jar')
output = args.output.resolve()
output.mkdir(parents=True, exist_ok=True)
review = project/'build/compatibility-mods/published'
profiles = dict(harvestcraft=["HarvestCraft.jar"],
    mystical=['MysticalAgriculture.jar'], forestry=['Forestry.jar'], vanilla=[])
profiles['combined'] = sum([profiles[k] for k in ['harvestcraft','mystical','forestry']], [])
extra_mods = [review/name for name in profiles[args.profile]]
fixture = project/'build/verification/runtime-fixture'
probe = project/'build/verification/probe/skyweather-probe.jar'
java = Path.home()/'.gradle/jdks/temurin-8-amd64-windows.2/bin/java.exe'
forge = next(fixture.glob('forge-*-universal.jar')).name
kernel = ctypes.WinDLL('kernel32', use_last_error=True)
kernel.SetProcessAffinityMask.argtypes = [wintypes.HANDLE, ctypes.c_size_t]
kernel.SetProcessAffinityMask.restype = wintypes.BOOL
kernel.GetProcessTimes.argtypes = [wintypes.HANDLE] + [ctypes.POINTER(wintypes.FILETIME)] * 4
kernel.GetProcessTimes.restype = wintypes.BOOL
cpu = min(20, (os.cpu_count() or 1) - 1)

class Memory(ctypes.Structure):
    _fields_ = [('cb', wintypes.DWORD), ('PageFaultCount', wintypes.DWORD)] + [
        (name, ctypes.c_size_t) for name in ['PeakWorkingSetSize', 'WorkingSetSize',
        'QuotaPeakPagedPoolUsage', 'QuotaPagedPoolUsage', 'QuotaPeakNonPagedPoolUsage',
        'QuotaNonPagedPoolUsage', 'PagefileUsage', 'PeakPagefileUsage']]

psapi = ctypes.WinDLL('psapi', use_last_error=True)
psapi.GetProcessMemoryInfo.argtypes = [wintypes.HANDLE, ctypes.POINTER(Memory), wintypes.DWORD]
psapi.GetProcessMemoryInfo.restype = wintypes.BOOL

def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()

def prepare(directory):
    (directory/'mods').mkdir(parents=True, exist_ok=True)
    for name in [forge, 'minecraft_server.1.11.2.jar', 'eula.txt', 'ops.json',
                 'whitelist.json', 'banned-ips.json', 'banned-players.json']:
        if not (directory/name).exists():
            shutil.copy2(fixture/name, directory/name)
    if not (directory/'libraries').exists():
        # Junctions need no symlink privilege. Paths are passed as literal arguments.
        quoted = ["'" + str(path).replace("'", "''") + "'" for path in
                  [directory/'libraries', (fixture/'libraries').resolve()]]
        subprocess.run(['powershell', '-NoProfile', '-Command',
            'New-Item -ItemType Junction -Path ' + quoted[0] + ' -Target ' + quoted[1] + ' | Out-Null'], check=True)
    properties = (fixture/'server.properties').read_text()
    for key, value in {'level-type':'FLAT', 'level-name':'plant-checks',
                       'server-port':'0', 'max-tick-time':'-1', 'view-distance':'3',
                       'spawn-npcs':'false', 'generate-structures':'false'}.items():
        properties = re.sub(r'^' + key + r'=.*', key + '=' + value, properties, flags=re.M)
    properties = re.sub(r'^level-seed=.*', 'level-seed='+str(args.seeds[0]), properties, flags=re.M)
    (directory/'server.properties').write_text(properties)
    for artifact in [args.jar, probe, *extra_mods]:
        shutil.copy2(artifact, directory/'mods'/artifact.name)

build_receipt = json.loads((project/'build/verification/probe/probe-build.json').read_text())
if build_receipt['probe_sha256'] != digest(probe) or build_receipt['main_sha256'] != digest(args.jar):
    raise ValueError('Candidate does not match successful build receipt; run prepareRuntimeProbe')
for name, expected in build_receipt['source_sha256'].items():
    if digest(project/name) != expected: raise ValueError('Unbuilt source changes: '+name)
phase=args.phase
directory=output/('contracts' if phase=='reload' else phase)
logpath=output/(phase+'.log')
if logpath.exists(): raise FileExistsError('Keeping existing receipt '+str(logpath))
for receipt in output.glob('*.json'):
    previous=json.loads(receipt.read_text())
    if previous.get('main_sha256',digest(args.jar)) != digest(args.jar) or previous.get('probe_sha256',digest(probe)) != digest(probe):
        raise ValueError('Candidate changed between stages; select a fresh output directory')
if phase=='reload' and not json.loads((output/'contracts.json').read_text())['passed']:
    raise ValueError('Reload requires passing contracts')
prepare(directory)
originals={path.name:digest(path) for path in extra_mods}
command=[str(java),'-Xms128m','-Xmx1024m','-XX:+UseSerialGC','-XX:ActiveProcessorCount=1','-XX:CICompilerCount=2',
    '-Dskyweather.probe=plants','-Dplants.phase='+phase,'-Dplants.years='+str(args.years),
    '-Dplants.seeds='+','.join(map(str,args.seeds)),'-Dplants.climates='+','.join(args.climates),'-jar',forge,'nogui']
started=time.monotonic()
with logpath.open('wb') as log:
    process=subprocess.Popen(command,cwd=directory,stdout=log,stderr=subprocess.STDOUT,
        creationflags=subprocess.CREATE_NO_WINDOW|subprocess.BELOW_NORMAL_PRIORITY_CLASS)
    if not kernel.SetProcessAffinityMask(int(process._handle),1<<cpu):
        process.terminate(); raise ctypes.WinError(ctypes.get_last_error())
    print(f'{args.profile} {phase}: PID {process.pid}, low priority CPU {cpu}',flush=True)
    memory=Memory(); memory.cb=ctypes.sizeof(memory); peak=0
    while process.poll() is None:
        if psapi.GetProcessMemoryInfo(int(process._handle),ctypes.byref(memory),memory.cb): peak=max(peak,memory.PeakWorkingSetSize)
        if time.monotonic()-started>7200:
            process.terminate(); process.wait(); raise TimeoutError('Retained '+str(logpath))
        time.sleep(1)
    times=[wintypes.FILETIME() for _ in range(4)]
    kernel.GetProcessTimes(int(process._handle),*[ctypes.byref(t) for t in times])
    cpu_seconds=sum((t.dwHighDateTime<<32)|t.dwLowDateTime for t in times[2:])/1e7
content=logpath.read_text(encoding='utf-8',errors='replace')
unchanged=originals=={path.name:digest(path) for path in extra_mods}
passed=process.returncode==0 and 'PLANT_PASS' in content and 'PLANT_FAILED' not in content and unchanged
result=dict(profile=args.profile,phase=phase,passed=passed,exit_code=process.returncode,seconds=round(time.monotonic()-started,2),
    cpu_seconds=round(cpu_seconds,2),peak_working_set_bytes=peak,cpu=cpu,priority='BELOW_NORMAL',command=command,
    main_sha256=digest(directory/'mods'/args.jar.name),probe_sha256=digest(directory/'mods'/probe.name),
    original_mods_sha256=originals,original_mods_unchanged=unchanged)
(output/(phase+'.json')).write_text(json.dumps(result,indent=2),encoding='utf-8')
for line in content.splitlines():
    if 'PLANT_' in line: print(line,flush=True)
print(json.dumps(result),flush=True)
if not passed: raise RuntimeError('Failed; inspect '+str(logpath))
