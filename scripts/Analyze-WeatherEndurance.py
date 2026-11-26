"""Audit observable climate behavior against explicit realism-inspired gameplay objectives."""
import argparse
import csv
import hashlib
import json
import math
from pathlib import Path

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('evidence', type=Path)
stages = parser.add_mutually_exclusive_group()
stages.add_argument('--sweep-only', action='store_true')
stages.add_argument('--native-only', action='store_true')
args = parser.parse_args()
base = args.evidence.resolve()
project = Path(__file__).resolve().parents[1]
profiles = json.loads((base/'presets.json' if (base/'presets.json').exists() else project/'src/main/resources/assets/skysrealisticweather/presets.json').read_text())
profiles = {p['id']:p for p in profiles}
all_profiles = profiles
policy_path = base/'objectives.json' if (base/'objectives.json').exists() else project/'scripts/climate-objectives.json'
policy = json.loads(policy_path.read_text())
phase_names = ['sweep'] if args.sweep_only else ['runtime','reload'] if args.native_only else ['sweep','runtime','reload']
receipts = [json.loads((base/(p+'.json')).read_text()) for p in phase_names]

def option(receipt, name, fallback):
    return next((a.split('=',1)[1] for a in receipt['command'] if a.startswith('-Dendurance.'+name+'=')), fallback)

def read(path):
    with path.open(newline='') as stream:
        rows = list(csv.DictReader(stream))
    if any(None in row or any(v is None for v in row.values()) for row in rows):
        raise ValueError('Malformed CSV columns: '+str(path))
    return rows

checks = []
def check(name, passed, actual=None, expected=None):
    value = dict(check=name,passed=bool(passed))
    if actual is not None:value['actual']=actual
    if expected is not None:value['expected']=expected
    checks.append(value)

def envelope(climate, metric, value, bounds):
    check(climate+' '+metric, bounds[0] <= value <= bounds[1], round(value,4), bounds)

check('all requested packaged stages passed',all(r['passed'] for r in receipts))
check('same main candidate in all stages',len({r['main_sha256'] for r in receipts})==1)
check('same packaged probe in all stages',len({r['probe_sha256'] for r in receipts})==1)
check('same optional mods in all stages',len({json.dumps(r.get('additional_mods',{}),sort_keys=True) for r in receipts})==1)
check('objectives cover every packaged starter climate',set(policy['climates'])==set(profiles))
for r in receipts:
    if 'objectives_sha256' in r:
        check(r['phase']+' objective snapshot binding',hashlib.sha256(policy_path.read_bytes()).hexdigest()==r['objectives_sha256'])
summary = {}
if not args.native_only:
    sweep = receipts[0]
    years = int(option(sweep,'years','20'))
    seeds = option(sweep,'seeds','123948,70289,91173').split(',')
    selected = option(sweep,'sweepClimates','all').split(',')
    if selected!=['all']: profiles={k:v for k,v in all_profiles.items() if k in selected}
    extremes = 2 if int(option(sweep,'extremeYears','5')) > 0 else 0
    rows = read(base/'sweep/endurance/climates.csv')
    normal = [r for r in rows if float(r['strength'])==1]
    check('all packaged climates, seeds and strength series present',len(rows)==len(profiles)*(len(seeds)+extremes)*4 and {r['climate'] for r in rows}==set(profiles))
    day_metrics = ['wet_days','snow_days','high_snow_days','thunder_days','fog_days','dust_days','cold_days','heat_days','blowing_snow_days','blizzard_days','strong_days','wet_days_02mm','wet_days_1mm']
    for climate,preset in profiles.items():
        group = [r for r in normal if r['climate']==climate]
        total_days = sum(int(r['days']) for r in group)
        total_samples = sum(int(r['samples']) for r in group)
        result = {k:sum(float(r[k]) for r in group)*365/total_days for k in day_metrics}
        result['liquid_mm'] = sum(float(r['liquid_mm']) for r in group)*365/total_days
        result['wet_pct'] = sum(float(r['wet_pct'])*int(r['samples']) for r in group)/total_samples
        result['fog_pct'] = sum(float(r['fog_pct'])*int(r['samples']) for r in group)/total_samples
        result['minimum'] = min(float(r['min_temp']) for r in group)
        result['maximum'] = max(float(r['max_temp']) for r in group)
        result['phases'] = []
        for phase in range(4):
            part = [r for r in group if int(r['phase'])==phase]
            count = sum(int(r['days']) for r in part)
            result['phases'].append({key:sum(float(r[key])*int(r['days']) for r in part)/count for key in ['wet_pct','snow_pct','thunder_pct','fog_pct','mean_temp']})
        result['max_dry_days'] = max(int(r['max_dry_days']) for r in group)
        result['max_wet_days'] = max(int(r['max_wet_days']) for r in group)
        summary[climate] = result
        for metric,bounds in policy['climates'][climate].items():
            if metric=='cover_days':continue
            envelope(climate,metric,result[metric],bounds)
        cap = policy['max_fantasy_wet_pct'] if preset['group']=='fantasy' else policy['max_natural_wet_pct']
        check(climate+' clear-weather time budget',result['wet_pct']<=cap,round(result['wet_pct'],2),'<='+str(cap))
    for climate in ['tropical_monsoon','tropical_savanna']:
        if climate not in summary:continue
        phases=summary[climate]['phases']
        check(climate+' clear wet/dry seasonal contrast',phases[1]['wet_pct']>phases[3]['wet_pct']*3)
    if 'mediterranean' in summary:
        med=summary['mediterranean']['phases']
        check('Mediterranean summer remains dry relative to cool seasons',med[1]['wet_pct']*3<min(med[0]['wet_pct'],med[2]['wet_pct'],med[3]['wet_pct']))
    if 'oxford' in summary:
        check('British Isles high ground has more snowfall',summary['oxford']['high_snow_days']>summary['oxford']['snow_days']*1.2)
        check('British Isles no regular summer snowfall',summary['oxford']['phases'][1]['snow_pct']<0.01)
    if 'ice_cap' in summary:check('ice cap never reaches thaw temperature',summary['ice_cap']['maximum']<.15)
    if 'two_winters' in summary:
        winter=summary['two_winters']['phases']
        check('two winters separated by warmer, mostly snow-free phases',min(winter[0]['snow_pct'],winter[2]['snow_pct'])>max(winter[1]['snow_pct'],winter[3]['snow_pct'])*3)
    events=read(base/'sweep/endurance/events.csv')
    if selected==['all']:check('all eighteen weather kinds represented',len({r['kind'] for r in events if int(r['events'])>0})==18)
    onsets=read(base/'sweep/endurance/onsets.csv')
    for kind,hours in [('CONVECTIVE_SHOWER',range(11,18)),('RADIATION_FOG',range(3,6))]:
        observed=[r for r in onsets if r['kind']==kind and int(r['events'])>0]
        check(kind+' appropriate onset hours',bool(observed) and all(int(r['clock_hour']) in hours for r in observed))
    encounters=read(base/'sweep/endurance/encounters.csv')
    for climate in profiles:
        for kind in ['THUNDERSTORM','SEVERE_THUNDERSTORM','MONSOON_BURST','TROPICAL_STORM','WINTER_STORM','BLIZZARD','DUST_STORM']:
            group=[r for r in encounters if r['climate']==climate and r['kind']==kind and float(r['strength'])==1]
            segments=sum(int(r['segments']) for r in group)
            if segments>=20:
                mean=sum(float(r['visible_seconds']) for r in group)/segments
                check(climate+' meaningful '+kind+' encounters',mean>=policy['min_strong_encounter_seconds'],round(mean,2),'>='+str(policy['min_strong_encounter_seconds']))
    yearly_path=base/'sweep/endurance/years.csv'
    if yearly_path.exists():
        annual=read(yearly_path)
        for climate in profiles:
            data=[r for r in annual if r['climate']==climate and float(r['strength'])==1]
            length=sum(p[0] for p in profiles[climate]['periods'])
            snow=sorted(float(r['snow_days'])*365/length for r in data)
            summary[climate]['snow_p10']=snow[int((len(snow)-1)*.1)]
            summary[climate]['snow_p90']=snow[int((len(snow)-1)*.9)]
            if policy['climates'][climate].get('snow_days',[0])[0]>=18:
                good=sum(v>=policy['climates'][climate]['snow_days'][0]/2 for v in snow)/len(snow)
                check(climate+' snowfall is not confined to rare exceptional years',good>=.9,round(good,3),'>=0.9')
    (base/'climate-summary.json').write_text(json.dumps(summary,indent=2))
    lines=['| Climate | Wet days | Snow days low / high | Thunder days | Fog days | Dust days | Cold / heat days | Wet time |', '| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |']
    for climate,v in summary.items():
        lines.append(f"| {profiles[climate]['name']} | {v['wet_days']:.1f} | {v['snow_days']:.1f} / {v['high_snow_days']:.1f} | {v['thunder_days']:.1f} | {v['fog_days']:.1f} | {v['dust_days']:.1f} | {v['cold_days']:.1f} / {v['heat_days']:.1f} | {v['wet_pct']:.1f}% |")
    (base/'climate-table.md').write_text('\n'.join(lines)+'\n')
    print('Calendar measurement days:',sum(int(r['days']) for r in rows))
    print('Calendar samples:',sum(int(r['samples']) for r in rows))

native_summary={}
if not args.sweep_only:
    receipt=next(r for r in receipts if r['phase']=='runtime')
    requested=option(receipt,'nativeClimates','all').split(',')
    expected={'per_biome',*all_profiles} if requested==['all'] else set(requested)
    files=list((base/'runtime/endurance').glob('native-*.csv'))
    check('requested native climate matrix complete',{f.stem[7:] for f in files}==expected)
    native_days=int(option(receipt,'nativeDays','730'))
    for path in files:
        climate=path.stem[7:]
        rows=read(path)
        sites={r['site'] for r in rows}
        check(climate+' native sites and days complete',sites==set(policy['native_mappings']) and len(rows)==native_days*len(policy['native_mappings']))
        for r in rows:
            if not (.0<=float(r['cover_fraction'])<=1 and math.isfinite(float(r['min_temp'])) and math.isfinite(float(r['max_temp']))):
                check(climate+' finite native readings',False);break
        data=[r for r in rows if r['scored']=='true' and r['site']=='reference']
        check(climate+' post-warmup scored days present',bool(data))
        if data:
            cover=sum(r['cover_at_0900']=='true' for r in data)*365/len(data)
            native_summary[climate]=dict(cover_days=cover,scored_days=len(data))
            bounds=policy['climates'].get(climate,{}).get('cover_days')
            if bounds:envelope(climate,'09:00 majority ground cover',cover,bounds)
        if climate=='per_biome':
            mappings=policy['native_mappings']
            for site,preset in mappings.items():
                data=[r for r in rows if r['site']==site]
                check('per-biome '+site+' identity',{r['climate'] for r in data}=={preset})
                if site in ['jungle','desert']:
                    check(site+' warm climate has no seasonal deposits',all(r['ice']=='false' and r['snow']=='false' for r in data))
                if site=='arctic':
                    check('Arctic native cover survives every season',all(r['ice']=='true' and r['snow']=='true' and float(r['max_temp'])<.15 for r in data))
                if site=='cold_taiga':
                    for year in range((native_days+364)//365):
                        summer=[r for r in data if year*365+60<=int(r['day'])<min(native_days,year*365+115)]
                        if summer:check('Cold Taiga summer thaw '+str(year+1),any(r['ice']=='false' and r['snow']=='false' and float(r['max_temp'])>.15 for r in summer))
    (base/'native-summary.json').write_text(json.dumps(native_summary,indent=2))

(base/'checks.json').write_text(json.dumps(checks,indent=2))
print(f"{sum(c['passed'] for c in checks)}/{len(checks)} checks passed")
for c in checks:
    if not c['passed']:print('FAILED:',c['check'],c.get('actual',''),c.get('expected',''))
print('Stage host seconds:',round(sum(r['seconds'] for r in receipts),2))
print('Peak server working set MiB:',round(max(r['peak_working_set_bytes'] for r in receipts)/1024**2,1))
raise SystemExit(0 if all(c['passed'] for c in checks) else 1)
