"""Prepare the owner's Fabric client for a Minecraft version: mods from Modrinth (hash-checked), its own game folder with
copies of the current settings, the Fabric version JSON and a launcher profile. Usage: clientprofile.py MCVER LOADER
[slug=gameversion ...] where slug=gameversion picks a mod's build for another game version (e.g. facebar=26.1)."""
import json, os, sys, hashlib, shutil, urllib.request, urllib.parse
mc_ver, loader = sys.argv[1], sys.argv[2]
override = dict(a.split('=') for a in sys.argv[3:])
UA = {'User-Agent': 'b4b-client-updater/1.0'}
def get(u): return json.load(urllib.request.urlopen(urllib.request.Request(u, headers=UA)))
MC = os.path.join(os.environ['APPDATA'], '.minecraft')
inst = os.path.join(MC, 'instances', mc_ver)
mods = os.path.join(inst, 'mods')
os.makedirs(mods, exist_ok=True)
SLUGS = ['fabric-api', 'distanthorizons', 'sodium', 'betterf3', 'modmenu', 'cloth-config', 'freecam', 'autorun',
         'litematica', 'malilib', 'facebar', 'borderless-fullscreen']
for slug in SLUGS:
    gv = override.get(slug, mc_ver)
    q = urllib.parse.quote(json.dumps([gv])); l = urllib.parse.quote(json.dumps(['fabric']))
    vs = get(f'https://api.modrinth.com/v2/project/{slug}/version?game_versions={q}&loaders={l}')
    rel = [v for v in vs if v['version_type'] == 'release'] or vs
    if not rel: print('NONE', slug, gv); continue
    v = rel[0]; f = ([x for x in v['files'] if x['primary']] or v['files'])[0]
    data = urllib.request.urlopen(urllib.request.Request(f['url'], headers=UA)).read()
    assert hashlib.sha512(data).hexdigest() == f['hashes']['sha512'], slug
    open(os.path.join(mods, f['filename']), 'wb').write(data)
    print('ok', slug, v['version_number'], f['filename'])
# Settings copied from the previous version's profile folder (SETTINGS_FROM), else the main .minecraft.
base = os.environ.get('SETTINGS_FROM') or MC
for name in ('options.txt', 'servers.dat'):
    src = os.path.join(base, name)
    if os.path.exists(src) and not os.path.exists(os.path.join(inst, name)): shutil.copy2(src, inst)
if not os.path.exists(os.path.join(inst, 'config')): shutil.copytree(os.path.join(base, 'config'), os.path.join(inst, 'config'))
vid = f'fabric-loader-{loader}-{mc_ver}'
vdir = os.path.join(MC, 'versions', vid); os.makedirs(vdir, exist_ok=True)
vj = get(f'https://meta.fabricmc.net/v2/versions/loader/{mc_ver}/{loader}/profile/json')
json.dump(vj, open(os.path.join(vdir, vid + '.json'), 'w', encoding='utf8'), indent=2)
p = os.path.join(MC, 'launcher_profiles.json'); d = json.load(open(p, encoding='utf8'))
src = [v for v in d['profiles'].values() if v.get('lastVersionId') == 'fabric-loader-0.17.3-1.21.10'][0]
new = dict(src); new.update({'name': f'fabric-loader-{mc_ver}', 'lastVersionId': vid, 'gameDir': inst,
                             'created': '2026-11-26T12:40:00.000Z', 'lastUsed': '2026-11-26T12:40:00.000Z'})
d['profiles'][f'fabric-loader-{mc_ver}'] = new
json.dump(d, open(p, 'w', encoding='utf8'), indent=2)
print('profile', new['name'], vid, inst)
