"""Prepare a clean 1.11.2 test fixture from an installed Forge server, without copying worlds."""
from pathlib import Path
import argparse, hashlib, json, shutil, subprocess

project = Path(__file__).resolve().parents[1]
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--engine-dir', type=Path, required=True)
args = parser.parse_args()
engine = args.engine_dir.resolve()
fixture = project/'build/verification/runtime-fixture'
forge = 'forge-1.11.2-13.20.1.2588-universal.jar'
assert hashlib.sha1((engine/forge).read_bytes()).hexdigest() == 'acd5d24c3f58590b6a9da6e0e2aa14bd6dcc5166', 'Unexpected Forge server'
assert (engine/'libraries').is_dir(), 'Install Forge server libraries first'
fixture.mkdir(parents=True, exist_ok=True)
for name in [forge, 'minecraft_server.1.11.2.jar']:
    destination = fixture/name
    if destination.exists():
        assert destination.read_bytes() == (engine/name).read_bytes(), 'Fixture engine changed'
    else:
        shutil.copy2(engine/name, destination)
if not (fixture/'libraries').exists():
    quoted = lambda path: "'"+str(path).replace("'", "''")+"'"
    subprocess.run(['powershell', '-NoProfile', '-Command',
        'New-Item -ItemType Junction -Path '+quoted(fixture/'libraries')+' -Target '+quoted(engine/'libraries')+' | Out-Null'], check=True)
(fixture/'eula.txt').write_text('eula=true\n')
for name in ['ops.json', 'whitelist.json', 'banned-ips.json', 'banned-players.json']:
    (fixture/name).write_text('[]\n')
(fixture/'server.properties').write_text('''level-name=world
level-type=DEFAULT
level-seed=123948
server-ip=127.0.0.1
server-port=0
online-mode=false
enable-rcon=false
enable-query=false
view-distance=3
max-tick-time=-1
snooper-enabled=false
spawn-npcs=false
generate-structures=false
''')
print(fixture)
