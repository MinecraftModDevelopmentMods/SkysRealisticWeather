"""Summarize completed matched runs without treating timing noise as a speed guarantee."""
from pathlib import Path
import argparse,json,statistics

parser=argparse.ArgumentParser(description=__doc__)
parser.add_argument('results',type=Path,help='Campaign directory containing results.json')
parser.add_argument('--output',type=Path,required=True)
args=parser.parse_args()
data=json.loads((args.results/'results.json').read_text())
assert data['passed'] and data['manifest']['variant'] is None,'A complete on/off campaign is required'
assert all(r['passed'] for r in data['runs'])
summary=[]
for terrain in sorted({r['terrain'] for r in data['runs']}):
    for phase in ['winter','summer']:
        pairs=[]
        for number in range(1,data['manifest']['rounds']+1):
            runs={r['profile']:r for r in data['runs'] if r['terrain']==terrain and r['round']==number}
            assert set(runs)=={'vanilla','on'}
            a,b=[next(row for row in runs[p]['rows'] if row['phase']==phase) for p in ['vanilla','on']]
            assert a['requests']==b['requests']
            n=int(a['requests']);off=float(a['cpu_ms'])/n;on=float(b['cpu_ms'])/n
            wall_off=float(a['wall_ms'])/n;wall_on=float(b['wall_ms'])/n
            pairs.append(dict(round=number,baseline_cpu_ms_per_request=off,weather_cpu_ms_per_request=on,
                              change_percent=100*(on-off)/off,change_ms_per_request=on-off,
                              baseline_wall_ms_per_request=wall_off,weather_wall_ms_per_request=wall_on,
                              wall_change_percent=100*(wall_on-wall_off)/wall_off,
                              baseline_p95_ms=float(a['request_p95_ms']),weather_p95_ms=float(b['request_p95_ms']),
                              weather_snow_columns=int(b['snow_columns']),weather_ice_columns=int(b['ice_columns'])))
        median=lambda key:statistics.median(r[key] for r in pairs)
        summary.append(dict(terrain=terrain,phase=phase,rounds=len(pairs),
                            baseline_cpu_ms_per_request=median('baseline_cpu_ms_per_request'),weather_cpu_ms_per_request=median('weather_cpu_ms_per_request'),
                            median_paired_change_percent=median('change_percent'),median_paired_change_ms=median('change_ms_per_request'),
                            median_paired_wall_change_percent=median('wall_change_percent'),
                            paired_change_range_percent=[min(r['change_percent'] for r in pairs),max(r['change_percent'] for r in pairs)],
                            baseline_wall_ms_per_request=median('baseline_wall_ms_per_request'),weather_wall_ms_per_request=median('weather_wall_ms_per_request'),
                            baseline_request_p95_ms=median('baseline_p95_ms'),weather_request_p95_ms=median('weather_p95_ms'),
                            median_snow_columns=median('weather_snow_columns'),median_ice_columns=median('weather_ice_columns'),pairs=pairs))
output=args.output.resolve()
assert not output.exists(),'Preserve earlier analysis'
output.parent.mkdir(parents=True,exist_ok=True)
output.write_text(json.dumps(dict(manifest=data['manifest'],summary=summary,parity_checks=data['parity_checks']),indent=2))
print('| Terrain | Season | Baseline CPU ms/chunk | Weather CPU ms/chunk | Paired change | Pair range |')
print('| --- | --- | ---: | ---: | ---: | ---: |')
for r in summary:
    low,high=r['paired_change_range_percent']
    print(f"| {r['terrain']} | {r['phase']} | {r['baseline_cpu_ms_per_request']:.3f} | {r['weather_cpu_ms_per_request']:.3f} | {r['median_paired_change_percent']:+.2f}% | {low:+.2f}% to {high:+.2f}% |")
