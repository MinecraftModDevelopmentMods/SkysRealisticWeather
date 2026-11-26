"""Accelerated packaged Forge weather checks; one low-priority CPU, no client window.

Build with prepareRuntimeProbe first. Default: 20 years x 3 seeds per preset,
5 years at each extreme strength, six years in every native climate setting
(one warm-up and five scored), then process reload.
"""
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
parser.add_argument('--years', type=int, default=20)
parser.add_argument('--seeds', type=int, nargs='+', default=[123948, 70289, 91173])
parser.add_argument('--native-days', type=int, default=365*6)
parser.add_argument('--native-climates', nargs='+', default=['all'])
parser.add_argument('--warmup-days', type=int, default=365)
parser.add_argument('--sample-step', type=int, choices=[20,40,100,200], default=100)
parser.add_argument('--sweep-climates', nargs='+', default=['all'])
parser.add_argument('--extreme-years', type=int, default=5)
parser.add_argument('--phase', choices=['all', 'sweep', 'runtime', 'reload'], default='all')
parser.add_argument('--output', type=Path)
parser.add_argument('--extra-mod', type=Path, action='append', default=[])
version = re.search(r'^mod_version=(.+)$', (project/'gradle.properties').read_text(), re.M).group(1)
default_jar = project/('build/libs/SkysRealisticWeather-'+version+'.jar')
parser.add_argument('--jar', type=Path, default=default_jar)
args = parser.parse_args()
if args.years < 1 or args.native_days < 1 or args.extreme_years < 0 or args.warmup_days < 0:
    parser.error('Durations must be positive; warmup and extreme years may be zero')
if args.phase in ['all','runtime'] and args.native_days <= args.warmup_days:
    parser.error('Native duration must include scored days after warmup')
output = args.output or project/'build/verification/weather-endurance'/datetime.datetime.now().strftime('%Y%m%d-%H%M%S')
output = output.resolve()
output.mkdir(parents=True, exist_ok=True)
fixture = project/'build/verification/worldgen-performance/whole-chunk-v1/1-vanilla'
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
    for name in [forge, 'minecraft_server.1.10.2.jar', 'eula.txt', 'ops.json',
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
    for key, value in {'level-type':'FLAT', 'level-name':'weather-endurance',
                       'server-port':'0', 'max-tick-time':'-1', 'view-distance':'3',
                       'spawn-npcs':'false', 'generate-structures':'false'}.items():
        properties = re.sub(r'^' + key + r'=.*', key + '=' + value, properties, flags=re.M)
    properties = re.sub(r'^level-seed=.*', 'level-seed='+str(args.seeds[0]), properties, flags=re.M)
    (directory/'server.properties').write_text(properties)
    for artifact in [args.jar, probe, *args.extra_mod]:
        shutil.copy2(artifact, directory/'mods'/artifact.name)

def run(phase):
    build_receipt = json.loads((project/'build/verification/probe/probe-build.json').read_text())
    if build_receipt['probe_sha256'] != digest(probe):
        raise ValueError('Probe does not match its successful build receipt; run prepareRuntimeProbe')
    if args.jar.resolve() == default_jar.resolve() and build_receipt['main_sha256'] != digest(args.jar):
        raise ValueError('Main jar does not match the probe build receipt; rebuild the candidate')
    for name, expected in build_receipt['source_sha256'].items():
        if digest(project/name) != expected:
            raise ValueError('Unbuilt source changes in '+name+'; run prepareRuntimeProbe before testing')
    directory = output/('runtime' if phase == 'reload' else phase)
    logpath = output/(phase+'.log')
    if logpath.exists():
        raise FileExistsError(f'Keeping existing receipt {logpath}; select a new output directory')
    for receipt in output.glob('*.json'):
        previous = json.loads(receipt.read_text())
        if 'main_sha256' in previous and (previous['main_sha256'] != digest(args.jar) or previous['probe_sha256'] != digest(probe)):
            raise ValueError('Candidate changed between stages; use a fresh evidence directory')
    if phase == 'reload' and not json.loads((output/'runtime.json').read_text())['passed']:
        raise ValueError('Reload requires a passing runtime stage')
    with zipfile.ZipFile(args.jar) as packaged:
        for name in ['presets.json','weather_weights.json','weather_timing.json']:
            snapshot = output/name
            data = packaged.read('assets/skysrealisticweather/'+name)
            if snapshot.exists() and snapshot.read_bytes() != data:
                raise ValueError('Packaged inputs changed; use a fresh evidence directory')
            if not snapshot.exists(): snapshot.write_bytes(data)
    objectives = output/'objectives.json'
    if not objectives.exists(): shutil.copy2(project/'scripts/climate-objectives.json',objectives)
    prepare(directory)
    flags = ['-Dendurance.reload=true'] if phase == 'reload' else ['-Dendurance.phase='+phase]
    command = [str(java), '-Xms128m', '-Xmx1024m', '-XX:+UseSerialGC',
               '-XX:ActiveProcessorCount=1', '-XX:CICompilerCount=2',
               '-Dskyweather.probe=endurance', '-Dendurance.years='+str(args.years),
               '-Dendurance.seeds='+','.join(map(str,args.seeds)),
               '-Dendurance.sampleStep='+str(args.sample_step),
               '-Dendurance.sweepClimates='+','.join(args.sweep_climates),
               '-Dendurance.extremeYears='+str(args.extreme_years),
               '-Dendurance.nativeClimates='+','.join(args.native_climates),
               '-Dendurance.warmupDays='+str(args.warmup_days),
               '-Dendurance.nativeDays='+str(args.native_days), *flags, '-jar', forge, 'nogui']
    started = time.monotonic()
    with logpath.open('wb') as log:
        process = subprocess.Popen(command, cwd=directory, stdout=log, stderr=subprocess.STDOUT,
            creationflags=subprocess.CREATE_NO_WINDOW | subprocess.BELOW_NORMAL_PRIORITY_CLASS)
        if not kernel.SetProcessAffinityMask(int(process._handle), 1 << cpu):
            process.terminate()
            raise ctypes.WinError(ctypes.get_last_error())
        print(f'{phase}: PID {process.pid}, one logical CPU ({cpu}), below normal priority', flush=True)
        memory = Memory()
        memory.cb = ctypes.sizeof(memory)
        peak = 0
        while process.poll() is None:
            if psapi.GetProcessMemoryInfo(int(process._handle), ctypes.byref(memory), memory.cb):
                peak = max(peak, memory.PeakWorkingSetSize)
            if time.monotonic() - started > 7200:
                process.terminate()
                process.wait()
                raise TimeoutError(f'{phase} exceeded two hours; retained {logpath}')
            time.sleep(1)
        creation, exit_time, system, user = [wintypes.FILETIME() for _ in range(4)]
        kernel.GetProcessTimes(int(process._handle), ctypes.byref(creation), ctypes.byref(exit_time),
                               ctypes.byref(system), ctypes.byref(user))
        cpu_seconds = sum((v.dwHighDateTime << 32) | v.dwLowDateTime for v in [system, user]) / 1e7
    content = logpath.read_text(errors='replace')
    marker = 'ENDURANCE_RELOAD PASS' if phase == 'reload' else 'ENDURANCE_PASS'
    passed = process.returncode == 0 and marker in content and 'ENDURANCE_FAILED' not in content
    result = dict(phase=phase, passed=passed, exit_code=process.returncode, seconds=round(time.monotonic()-started, 2),
                  cpu_seconds=round(cpu_seconds, 2), peak_working_set_bytes=peak, cpu=cpu,
                  priority='BELOW_NORMAL', command=command, main_sha256=digest(directory/'mods'/args.jar.name),
                  probe_sha256=digest(directory/'mods'/probe.name))
    result['additional_mods'] = {p.name: digest(directory/'mods'/p.name) for p in args.extra_mod}
    result['objectives_sha256'] = digest(objectives)
    (output/(phase+'.json')).write_text(json.dumps(result, indent=2))
    for line in content.splitlines():
        if 'ENDURANCE_' in line and 'ENDURANCE_NATIVE' not in line:
            print(line, flush=True)
    print(json.dumps(result), flush=True)
    if not passed:
        raise RuntimeError(f'{phase} failed; inspect {logpath}')

print('Evidence:', output, flush=True)
for phase in (['sweep', 'runtime', 'reload'] if args.phase == 'all' else [args.phase]):
    run(phase)
