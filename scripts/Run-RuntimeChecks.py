"""Run a packaged weather probe in a disposable, hidden, single-CPU Forge server."""
from pathlib import Path
import argparse, ctypes, hashlib, json, os, shutil, subprocess, time
from ctypes import wintypes

project = Path(__file__).resolve().parents[1]
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--mode', required=True)
parser.add_argument('--expect', required=True, help='Required success marker in the server log')
parser.add_argument('--mod', action='append', default=[], help='Published integration name, e.g. BoP')
parser.add_argument('--option', action='append', default=[], help='Additional Java system property key=value')
parser.add_argument('--world-type', default='FLAT')
parser.add_argument('--fixed-biome', type=int, help='Actual vanilla biome registry id, not the Customized GUI index')
parser.add_argument('--level-name', default='world')
parser.add_argument('--universal', action='store_true', help='Use the Burlington climate for controlled generator tests')
parser.add_argument('--output', type=Path, required=True)
parser.add_argument('--stage', default='initial')
args = parser.parse_args()
output = args.output.resolve()
assert output.is_relative_to((project/'build/verification').resolve()), 'Use disposable verification output'
assert args.stage.isidentifier(), 'Use a simple stage name'
output.mkdir(parents=True, exist_ok=True)
logpath = output/(args.stage+'.log')
assert not logpath.exists(), 'Preserve previous evidence'
digest = lambda path: hashlib.sha256(path.read_bytes()).hexdigest()
receipt = json.loads((project/'build/verification/probe/probe-build.json').read_text())
for name, expected in receipt['source_sha256'].items():
    assert digest(project/name) == expected, 'Unbuilt source: '+name
main = next(path for path in (project/'build/libs').glob('*.jar') if digest(path) == receipt['main_sha256'])
probe = project/'build/verification/probe/skyweather-probe.jar'
assert digest(probe) == receipt['probe_sha256'], 'Rebuild probe'
directory = output/'server'
fixture = project/'build/verification/runtime-fixture'
forge = 'forge-1.11.2-13.20.1.2588-universal.jar'
if not directory.exists():
    directory.mkdir(); (directory/'mods').mkdir()
    for name in [forge, 'minecraft_server.1.11.2.jar', 'eula.txt', 'ops.json', 'whitelist.json', 'banned-ips.json', 'banned-players.json']:
        shutil.copy2(fixture/name, directory/name)
    quoted = lambda path: "'"+str(path).replace("'", "''")+"'"
    subprocess.run(['powershell', '-NoProfile', '-Command',
        'New-Item -ItemType Junction -Path '+quoted(directory/'libraries')+' -Target '+quoted(fixture/'libraries')+' | Out-Null'], check=True)
    props = (fixture/'server.properties').read_text().replace('level-type=DEFAULT', 'level-type='+args.world_type)
    props = props.replace('level-name=world', 'level-name='+args.level_name)
    if args.fixed_biome is not None:
        assert args.world_type.upper() == 'CUSTOMIZED' and args.fixed_biome not in [8, 9]
        # Customized's persisted selection omits the Nether and End entries.
        selection = args.fixed_biome-2 if args.fixed_biome >= 10 else args.fixed_biome
        props += '\ngenerator-settings='+json.dumps(dict(fixedBiome=selection))+'\n'
    (directory/'server.properties').write_text(props)
    if args.universal:
        (directory/'config').mkdir()
        # A partial current-schema file would mean an intentionally empty preset library.
        # Leave it unversioned so the loader seeds the complete default catalog.
        (directory/'config/skysrealisticweather.cfg').write_text('general {\n S:climateMode=UNIVERSAL\n S:activePreset=burlington\n}\n')
else:
    previous = json.loads((output/'initial.json').read_text())
    assert previous['passed'] and previous['main_sha256'] == digest(main) and previous['probe_sha256'] == digest(probe), 'Reload requires the same passing candidate'
for path in [main, probe]:
    shutil.copy2(path, directory/'mods'/path.name)
originals = {}
for name in args.mod:
    assert name.isidentifier(), 'Use a pinned integration name'
    source = project/'build/compatibility-mods/published'/(name+'.jar')
    shutil.copy2(source, directory/'mods'/source.name)
    originals[source.name] = digest(source)
java = Path.home()/'.gradle/jdks/temurin-8-amd64-windows.2/bin/java.exe'
command = [str(java), '-Xms128m', '-Xmx1g', '-XX:+UseSerialGC', '-XX:ActiveProcessorCount=1', '-XX:CICompilerCount=2',
    '-Dskyweather.probe='+args.mode, *['-D'+value for value in args.option], '-jar', forge, 'nogui']
kernel = ctypes.WinDLL('kernel32', use_last_error=True)
kernel.SetProcessAffinityMask.argtypes = [wintypes.HANDLE, ctypes.c_size_t]
kernel.SetProcessAffinityMask.restype = wintypes.BOOL
start = time.monotonic()
with logpath.open('wb') as log:
    process = subprocess.Popen(command, cwd=directory, stdout=log, stderr=subprocess.STDOUT,
        creationflags=subprocess.CREATE_NO_WINDOW|subprocess.BELOW_NORMAL_PRIORITY_CLASS)
    assert kernel.SetProcessAffinityMask(int(process._handle), 1<<min(20, (os.cpu_count() or 1)-1))
    print(args.mode, args.stage, 'PID', process.pid, flush=True)
    try:
        process.wait(timeout=900)
    except subprocess.TimeoutExpired:
        process.terminate(); process.wait()
text = logpath.read_text(encoding='utf-8', errors='replace')
unchanged = all(digest(directory/'mods'/name) == expected for name, expected in originals.items())
passed = process.returncode == 0 and args.expect in text and unchanged
result = dict(passed=passed, mode=args.mode, stage=args.stage, main_sha256=digest(main), probe_sha256=digest(probe),
    additional_mods=originals, original_mods_unchanged=unchanged, seconds=round(time.monotonic()-start, 2), command=command, exit_code=process.returncode)
(output/(args.stage+'.json')).write_text(json.dumps(result, indent=2))
for line in text.splitlines():
    if args.expect in line or 'FAILED' in line or 'SKY_WEATHER_CHECK' in line:
        print(line, flush=True)
assert passed, 'Failed; retained '+str(logpath)
