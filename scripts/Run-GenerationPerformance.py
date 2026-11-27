"""Compare whole native chunk generation in fresh, single-CPU packaged servers."""
from pathlib import Path
import argparse, csv, ctypes, hashlib, io, json, os, re, shutil, socket, subprocess, time
from ctypes import wintypes

root = Path(__file__).resolve().parents[1]
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--output', type=Path, required=True)
parser.add_argument('--rounds', type=int, default=3)
parser.add_argument('--variant',choices=['vanilla','on'],help='A single-profile calibration, not an on/off comparison')
parser.add_argument('--size', type=int, default=28)
parser.add_argument('--warmup', type=int, default=24)
parser.add_argument('--climate-mode', choices=['PER_BIOME','UNIVERSAL'], default='PER_BIOME')
parser.add_argument('--fixed-biome', type=int, help='Controlled vanilla-only biome registry ID')
parser.add_argument('--cover-diagnostic',type=Path,help='Validated cold-cover isolation receipt')
parser.add_argument('--veto-deposits',action='store_true',help='Diagnostic only: cancel seasonal surface placements')
parser.add_argument('--bop-calibration',type=Path,help='Verified native baseline variability receipt')
parser.add_argument('--terrain', nargs='+', choices=['vanilla', 'bop', 'bundle', 'lost'], default=['vanilla', 'bop', 'bundle', 'lost'])
args = parser.parse_args()
assert args.rounds >= 1 and args.size >= 8 and args.warmup >= 8
assert args.fixed_biome is None or args.terrain == ['vanilla'] and args.fixed_biome not in [8,9]
output = args.output.resolve()
assert output.is_relative_to(root/'build/verification')
output.mkdir(parents=True, exist_ok=False)
fixture = root/'build/verification/runtime-fixture'
build_receipt = json.loads((root/'build/verification/probe/probe-build.json').read_text())
main = next(p for p in (root/'build/libs').glob('*.jar') if hashlib.sha256(p.read_bytes()).hexdigest() == build_receipt['main_sha256'])
probe = root/'build/verification/probe/weather-performance-probe.jar'
forge = 'forge-1.11.2-13.20.1.2588-universal.jar'
java = Path.home()/'.gradle/jdks/temurin-8-amd64-windows.2/bin/java.exe'
digest = lambda p: hashlib.sha256(p.read_bytes()).hexdigest()
cover_diagnostic=None
if args.cover_diagnostic:
    cover_diagnostic=json.loads(args.cover_diagnostic.read_text())
    assert cover_diagnostic['passed'] and cover_diagnostic['main_sha256']==digest(main) and cover_diagnostic['probe_sha256']==digest(probe)
    assert args.terrain==['vanilla'] and args.climate_mode=='UNIVERSAL' and args.fixed_biome==1 and not args.veto_deposits
    assert args.size==cover_diagnostic['size'] and args.warmup==cover_diagnostic['warmup']
    for name,expected_hash in cover_diagnostic['receipt_sha256'].items():assert digest(root/name)==expected_hash
bop_calibration = None
if args.bop_calibration:
    bop_calibration=json.loads(args.bop_calibration.read_text())
    assert bop_calibration['passed'] and bop_calibration['probe_sha256']==digest(probe)
    assert bop_calibration['bop_sha256']==digest(root/'build/compatibility-mods/published/BoP.jar')
    for name,expected_hash in bop_calibration['receipt_sha256'].items():assert digest(root/name)==expected_hash
cpu = min(20, (os.cpu_count() or 1)-1)
kernel = ctypes.WinDLL('kernel32', use_last_error=True)
kernel.SetProcessAffinityMask.argtypes = [wintypes.HANDLE, ctypes.c_size_t]
kernel.SetProcessAffinityMask.restype = wintypes.BOOL
profiles = dict(vanilla=('DEFAULT', '', []), bop=('BIOMESOP', 'biomesoplenty', ['BoP']),
                bundle=('OTG', 'openterraingenerator', ['OTG', 'BiomeBundle']),
                lost=('lostcities', 'lostcities', ['LostCities', 'CompatLayer']))
fields = 'round terrain profile phase requests loaded populated wall_ms cpu_ms gc_count gc_ms request_p50_ms request_p95_ms request_p99_ms group16_p95_ms ice_p50_ms ice_p95_ms snow_columns ice_columns cover_fingerprint'.split()
manifest = dict(main_sha256=digest(main), probe_sha256=digest(probe), forge_sha256=digest(fixture/forge),
                seed=123948, size=args.size, warmup=args.warmup, cpu=cpu, priority='BelowNormal',
                heap='1536m', rounds=args.rounds, variant=args.variant,veto_deposits=args.veto_deposits, config=args.climate_mode, fixed_biome=args.fixed_biome,
                method='one JVM at a time; counterbalanced on/off and seasonal order; server-thread CPU and wall timings')
manifest['cover_diagnostic_sha256']=digest(args.cover_diagnostic) if args.cover_diagnostic else None
manifest['bop_calibration_sha256']=digest(args.bop_calibration) if args.bop_calibration else None
inputs = output/'inputs'; inputs.mkdir()
for path in [Path(__file__), *list((root/'src/integrationTest/java/zone/moddev/mc/skysrealisticweather/verify/performance').glob('*.java')), root/'build/verification/probe/probe-build.json']:
    shutil.copy2(path, inputs/path.name)
if args.bop_calibration:shutil.copy2(args.bop_calibration,inputs/'bop-baseline-calibration.json')
if args.cover_diagnostic:shutil.copy2(args.cover_diagnostic,inputs/'cold-cover-diagnostic.json')
manifest['inputs_sha256'] = {p.name:digest(p) for p in inputs.iterdir()}
(output/'manifest.json').write_text(json.dumps(manifest, indent=2))
all_rows = []
receipts = []
parity_checks=[]
complete = False
try:
    for round_number in range(1, args.rounds+1):
        for terrain_index, terrain in enumerate(args.terrain):
            world_type, expected, mods = profiles[terrain]
            if args.fixed_biome is not None:world_type='CUSTOMIZED'
            variants = [args.variant] if args.variant else (['vanilla', 'on'] if (round_number+terrain_index)%2 else ['on', 'vanilla'])
            for variant in variants:
                directory = output/f'{terrain}-{round_number}-{variant}'
                (directory/'mods').mkdir(parents=True)
                for name in [forge, 'minecraft_server.1.11.2.jar', 'eula.txt', 'ops.json', 'banned-ips.json', 'banned-players.json', 'whitelist.json']:
                    shutil.copy2(fixture/name, directory/name)
                quoted = lambda p: "'"+str(p).replace("'", "''")+"'"
                subprocess.run(['powershell', '-NoProfile', '-Command', 'New-Item -ItemType Junction -Path '+quoted(directory/'libraries')+' -Target '+quoted((fixture/'libraries').resolve())+' | Out-Null'], check=True, creationflags=subprocess.CREATE_NO_WINDOW)
                with socket.socket() as sock:
                    sock.bind(('127.0.0.1', 0)); port = sock.getsockname()[1]
                level = 'Biome Bundle' if terrain == 'bundle' else 'world'
                (directory/'server.properties').write_text(f'level-name={level}\nlevel-type={world_type}\nlevel-seed=123948\nserver-ip=127.0.0.1\nserver-port={port}\nonline-mode=false\nmax-tick-time=-1\nview-distance=3\nspawn-npcs=false\nspawn-monsters=false\nspawn-animals=false\ndifficulty=0\nenable-rcon=false\nsnooper-enabled=false\n')
                if args.fixed_biome is not None:
                    selection=args.fixed_biome-2 if args.fixed_biome>=10 else args.fixed_biome
                    with (directory/'server.properties').open('a') as props:props.write('generator-settings='+json.dumps(dict(fixedBiome=selection))+'\n')
                if variant=='on' and args.climate_mode=='UNIVERSAL':
                    (directory/'config').mkdir()
                    (directory/'config/skysrealisticweather.cfg').write_text('general {\n S:climateMode=UNIVERSAL\n S:activePreset=burlington\n}\n')
                artifacts = [probe] + ([main] if variant == 'on' else []) + [root/'build/compatibility-mods/published'/(name+'.jar') for name in mods]
                hashes = {p.name:digest(p) for p in artifacts}
                assert digest(main) == manifest['main_sha256'] and digest(probe) == manifest['probe_sha256']
                for path in artifacts:
                    shutil.copy2(path, directory/'mods'/path.name)
                command = [str(java), '-Xms128m', '-Xmx1536m', '-XX:+UseSerialGC', '-XX:ActiveProcessorCount=1', '-XX:CICompilerCount=2',
                           '-Dfile.encoding=UTF-8', '-Dperf.profile='+variant, '-Dperf.terrain='+terrain,
                           '-Dperf.worldType='+world_type, '-Dperf.expectedMod='+expected, '-Dperf.round='+str(round_number),
                           '-Dperf.reverse='+str(round_number%2 == 0).lower(), '-Dperf.fourBit=true',
                           '-Dperf.vetoDeposits='+str(args.veto_deposits).lower(),'-Dperf.size='+str(args.size), '-Dperf.warmup='+str(args.warmup), '-jar', forge, 'nogui']
                start = time.monotonic(); error = None
                with (directory/'server.log').open('wb') as log:
                    process = subprocess.Popen(command, cwd=directory, stdin=subprocess.PIPE, stdout=log, stderr=subprocess.STDOUT,
                                               creationflags=subprocess.CREATE_NO_WINDOW|subprocess.BELOW_NORMAL_PRIORITY_CLASS)
                    assert kernel.SetProcessAffinityMask(int(process._handle), 1<<cpu)
                    print(directory.name, 'PID', process.pid, flush=True)
                    try:
                        process.wait(timeout=900)
                    except BaseException as exc:
                        error = repr(exc)
                        process.terminate(); process.wait()
                text = (directory/'server.log').read_text(encoding='utf8', errors='replace')
                rows = [dict(zip(fields, next(csv.reader([line.split('WORLDGEN_PERF_RESULT ', 1)[1]])))) for line in text.splitlines() if 'WORLDGEN_PERF_RESULT ' in line]
                base_parity = dict(re.findall(r'BASE_TERRAIN_PARITY \S+ (winter|summer) ([0-9a-f]{64})', text))
                parity = dict(re.findall(r'TERRAIN_PARITY \S+ (winter|summer) ([0-9a-f]{64})', text))
                passed = process.returncode == 0 and 'WORLDGEN_PERF PASS' in text and len(rows) == 2 and len(parity) == 2 and len(base_parity) == 2 and error is None
                passed = passed and all(digest(directory/'mods'/name) == value for name, value in hashes.items())
                receipt = dict(passed=passed, error=error, exit_code=process.returncode, seconds=round(time.monotonic()-start, 2),
                               terrain=terrain, round=round_number, profile=variant, pid=process.pid,
                               main_sha256=manifest['main_sha256'], artifacts=hashes, rows=rows, terrain_parity=parity, base_terrain_parity=base_parity,vetoed_deposits=max([0]+[int(n) for n in re.findall(r'VETOED_SURFACE_CHANGES (\d+)',text)]), command=command)
                (directory/'result.json').write_text(json.dumps(receipt, indent=2))
                receipts.append(receipt); all_rows.extend(rows)
                print(directory.name, 'PASS' if passed else 'FAIL', f"{receipt['seconds']}s", flush=True)
                for row in rows:
                    print(row['phase'], 'CPU', row['cpu_ms'], 'wall', row['wall_ms'], 'snow', row['snow_columns'], 'ice', row['ice_columns'], flush=True)
                assert passed, 'Retained failed run: '+str(directory)
            if not args.variant:
                pair = receipts[-2:]
                base_equal=pair[0]['base_terrain_parity']==pair[1]['base_terrain_parity']
                full_equal=pair[0]['terrain_parity']==pair[1]['terrain_parity']
                native_variation=terrain=='bop' and bop_calibration is not None and not full_equal
                cover_feedback=cover_diagnostic is not None and not full_equal
                if cover_feedback:
                    assert pair[0]['terrain_parity']['summer']==pair[1]['terrain_parity']['summer'],'Summer has no cover feedback allowance'
                    cold=next(row for run in pair if run['profile']=='on' for row in run['rows'] if row['phase']=='winter')
                    assert int(cold['snow_columns'])>0 and int(cold['ice_columns'])>0,'Cold stress must exercise both deposits'
                parity_checks.append(dict(terrain=terrain,round=round_number,biomes_and_heightmaps_equal=base_equal,
                                          normalized_blocks_equal=full_equal,documented_native_decoration_variation=native_variation,isolated_winter_cover_feedback=cover_feedback))
                assert base_equal, 'Base terrain parity mismatch: '+terrain
                if native_variation:print('NATIVE_BOP_DECORATION_VARIATION: baseline calibration applies; base biomes and height maps match',flush=True)
                elif cover_feedback:print('ISOLATED_WINTER_COVER_FEEDBACK: veto diagnostic restores full parity; base and summer parity match',flush=True)
                else:assert full_equal, 'Terrain parity mismatch: '+terrain
    complete = True
finally:
    with (output/'measurements.csv').open('w', newline='') as file:
        writer = csv.DictWriter(file, fieldnames=fields); writer.writeheader(); writer.writerows(all_rows)
    (output/'results.json').write_text(json.dumps(dict(manifest=manifest, runs=receipts, parity_checks=parity_checks,passed=complete), indent=2))
