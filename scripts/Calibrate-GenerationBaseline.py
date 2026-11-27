"""Record decoration variability only after two identical weather-free BOP replays prove it."""
from pathlib import Path
import argparse,hashlib,json

root=Path(__file__).resolve().parents[1]
parser=argparse.ArgumentParser(description=__doc__)
parser.add_argument('baseline_a',type=Path,nargs='?',help='First bop-1-vanilla/result.json')
parser.add_argument('baseline_b',type=Path,nargs='?',help='Independent repeat with identical arguments')
parser.add_argument('--cover-veto',type=Path,help='Completed fixed-Plains universal-climate veto campaign')
parser.add_argument('--output',type=Path,required=True)
args=parser.parse_args()
digest=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
if args.cover_veto:
    path=(args.cover_veto/'results.json').resolve();data=json.loads(path.read_text());m=data['manifest']
    assert path.is_relative_to(root/'build/verification') and data['passed'] and m['veto_deposits']
    assert m['config']=='UNIVERSAL' and m['fixed_biome']==1 and m['rounds']==1 and len(data['runs'])==2
    a,b=data['runs'];assert a['profile']=='vanilla' and b['profile']=='on' and b['vetoed_deposits']>0
    assert a['terrain']==b['terrain']=='vanilla' and a['terrain_parity']==b['terrain_parity']
    assert a['base_terrain_parity']==b['base_terrain_parity']
    receipt=dict(passed=True,main_sha256=m['main_sha256'],probe_sha256=m['probe_sha256'],terrain='vanilla',
                 climate_mode='UNIVERSAL',fixed_biome=1,seed=m['seed'],size=m['size'],warmup=m['warmup'],
                 vetoed_deposits=b['vetoed_deposits'],receipt_sha256={str(path.relative_to(root)):digest(path)},
                 explanation='Cold climate queries preserve exact normalized block parity when seasonal surface placement is vetoed. '
                             'Physical winter cover can affect subsequent neighbouring decoration; base and summer parity remain mandatory.')
    output=args.output.resolve();assert output.is_relative_to(root/'build/verification') and not output.exists()
    output.parent.mkdir(parents=True,exist_ok=True);output.write_text(json.dumps(receipt,indent=2));print(output)
    raise SystemExit(0)
assert args.baseline_a and args.baseline_b,'Supply two native BOP controls or a --cover-veto campaign'
paths=[args.baseline_a.resolve(),args.baseline_b.resolve()]
a,b=[json.loads(p.read_text()) for p in paths]
assert all(p.is_relative_to(root/'build/verification') for p in paths)
assert a['passed'] and b['passed'] and a['profile']==b['profile']=='vanilla'
assert a['terrain']==b['terrain']=='bop' and a['artifacts']==b['artifacts']
assert a['command']==b['command'],'Calibration requires identical JVM and workload arguments'
assert not any(n.startswith('SkysRealisticWeather-') for n in a['artifacts'])
assert a['base_terrain_parity']==b['base_terrain_parity'],'Base biomes and height maps differ'
assert a['terrain_parity']!=b['terrain_parity'],'Native decoration variability was not demonstrated'
assert a['artifacts']['BoP.jar']==digest(root/'build/compatibility-mods/published/BoP.jar')
assert a['artifacts']['weather-performance-probe.jar']==digest(root/'build/verification/probe/weather-performance-probe.jar')
receipt=dict(passed=True,probe_sha256=a['artifacts']['weather-performance-probe.jar'],bop_sha256=a['artifacts']['BoP.jar'],
             receipt_sha256={str(p.relative_to(root)):digest(p) for p in paths},
             base_biomes_and_heightmaps_equal=True,normalized_blocks_equal=False,
             phase_equal={phase:a['terrain_parity'][phase]==b['terrain_parity'][phase] for phase in ['winter','summer']},
             explanation='Identical weather-free JVMs reproduce base biomes and height maps but differ after population. '
                         'The pinned BOP weighted generator uses an object-keyed HashMap; decoration equality is not a cross-JVM invariant.')
output=args.output.resolve()
assert output.is_relative_to(root/'build/verification') and not output.exists()
output.parent.mkdir(parents=True,exist_ok=True)
output.write_text(json.dumps(receipt,indent=2))
print(output)
