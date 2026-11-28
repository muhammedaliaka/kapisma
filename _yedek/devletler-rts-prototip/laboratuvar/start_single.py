import hashlib,json,os,pathlib,shutil,subprocess,sys,time,uuid
from settings import LAB_ADDRESS, LAB_PORT
lab=pathlib.Path(__file__).resolve().parent
rts=lab.parent
project=rts.parents[1]
mc=project.parent
version_dir=mc/'versions'/'Fabric 1.21.1'
client=version_dir/'Fabric 1.21.1.jar'
meta=json.loads((version_dir/'Fabric 1.21.1.json').read_text(encoding='utf-8-sig'))
assert hashlib.sha1(client.read_bytes()).hexdigest()=='30c73b1c5da787909b2f73340419fdf13b9def88'
java=pathlib.Path(os.environ['JAVA_HOME'])/'bin'/'java.exe'
root=lab/'server'
root.mkdir(exist_ok=True)
natural_survey='--terrain' in sys.argv
for name in ('libraries','versions','config','mods'):
 if not (root/name).exists(): shutil.copytree(rts/name,root/name)
for name in ('eula.txt','fabric-server-launch.jar'):
 shutil.copy2(rts/name,root/name)
datapacks=root/('world-terrain-qa' if natural_survey else 'world')/'datapacks'
datapacks.mkdir(parents=True,exist_ok=True)
ruleset='diplomacy' if '--diplomacy' in sys.argv else 'classic'
survey_pack=os.environ.get('KRTS_SERVER_QA_PACK')
shutil.copy2(survey_pack or rts/f'preset-{ruleset}.zip',datapacks/'devletler-rts.zip')
shutil.copy2(next((project/'tools/test-mods').glob('*carpet*.jar')),root/'mods')
props=dict(line.split('=',1) for line in (rts/'server.properties').read_text(encoding='ascii').splitlines()
           if '=' in line and not line.startswith('#'))
props.update({'server-ip':'127.0.0.1','server-port':str(LAB_PORT),'online-mode':'false','enforce-secure-profile':'false'})
properties = root/'server.properties'
previous_properties=properties.read_bytes() if natural_survey else None
if natural_survey:
 props.update({'level-name':'world-terrain-qa','level-type':'minecraft\\:normal','generator-settings':'{}',
               'level-seed':'5743893257777525637','generate-structures':'false','view-distance':'8'})
paths=[]
for library in meta['libraries']:
 rules=library.get('rules',[])
 if rules and not any(r.get('action')=='allow' and r.get('os',{}).get('name','windows')=='windows' for r in rules): continue
 group,artifact,number,*classifier=library['name'].split(':')
 if group=='net.fabricmc' and artifact in ('fabric-loader','sponge-mixin'):
  number={'fabric-loader':'0.20.1','sponge-mixin':'0.17.5+mixin.0.8.7'}[artifact]
  paths.append(rts/'libraries'/pathlib.Path(group.replace('.','/'))/artifact/number/(artifact+'-'+number+'.jar'))
 else:
  entry=library.get('downloads',{}).get('artifact',{}).get('path','/'.join((group.replace('.','/'),artifact,number,artifact+'-'+number+('-'+classifier[0] if classifier else '')+'.jar')))
  if group=='org.ow2.asm':
   candidate=rts/'libraries'/pathlib.Path(group.replace('.','/'))/artifact/'9.9.1'/(artifact+'-9.9.1.jar')
   if candidate.is_file():
    paths.append(candidate)
    continue
  paths.append(mc/'libraries'/entry)
assert all(p.is_file() for p in paths)
classpath=';'.join(map(str,paths+[client]))
formation_qa=os.environ.get('KRTS_FORMATION_QA_JAR')
if natural_survey:
 log_suffix='-terrain-diplomacy' if ruleset=='diplomacy' else '-terrain'
 if '--wood' in sys.argv: log_suffix+='-wood'
 if '--manual' in sys.argv: log_suffix+='-manual'
 if '--outpost' in sys.argv: log_suffix+='-outpost'
 if '--district' in sys.argv: log_suffix+='-district'
 if '--iron' in sys.argv: log_suffix+='-iron'
 if '--field' in sys.argv: log_suffix+='-field'
 if '--economy' in sys.argv: log_suffix+='-economy'
 if '--bank' in sys.argv: log_suffix+='-bank'
 if '--alliances' in sys.argv: log_suffix+='-alliances'
 if '--specialization' in sys.argv: log_suffix+='-specialization'
 if '--bank-rejoin' in sys.argv: log_suffix+='-rejoin'
 if '--hall' in sys.argv: log_suffix+='-hall'
 elif formation_qa and '--manual' not in sys.argv: log_suffix+='-orders'
elif formation_qa:
 log_suffix='-formation-npc' if os.environ.get('KRTS_FORMATION_QA_NPC') == 'true' else '-formation'
else:
 log_suffix='-technology' if '--technology' in sys.argv else ''
server_adapter=os.environ.get('KRTS_SERVER_QA_ADAPTER',str(rts/'mods/kapisma-rts-rules.jar'))
server_log=lab/('server'+log_suffix+'.out')
server=None
clients=[]
def command(cmd):
 server.stdin.write((cmd+'\n').encode('utf-8'))
 server.stdin.flush()
try:
 properties.write_text(''.join(f'{key}={value}\n' for key,value in props.items()),encoding='ascii')
 with server_log.open('wb') as out:
  server=subprocess.Popen([java,'-Xmx2G','-Dfile.encoding=UTF-8',f'-Dfabric.addMods={server_adapter}','-jar','fabric-server-launch.jar','nogui'],cwd=root,stdin=subprocess.PIPE,stdout=out,stderr=subprocess.STDOUT)
 for _ in range(150):
  if 'Done (' in server_log.read_text(encoding='utf-8',errors='replace'): break
  if server.poll() is not None: raise RuntimeError('server failed')
  time.sleep(1)
 else: raise RuntimeError('server boot timeout')
 if not natural_survey: command('function krts:reset')
 for index, name in enumerate(('RtsQA1','RtsQA2') if '--two' in sys.argv else ('RtsQA1',)):
  game=lab/name
  (game/'natives').mkdir(exist_ok=True)
  (game/'mods').mkdir(parents=True,exist_ok=True)
  for path in (rts/'mods').glob('*.jar'):
   if path.name.startswith(('fabric-api','HundredYearsWar')): shutil.copy2(path,game/'mods')
  (game/'options.txt').write_text('version:3955\nonboardAccessibility:false\ntutorialStep:none\nrawMouseInput:false\npauseOnLostFocus:false\nrenderDistance:4\nsimulationDistance:5\nmaxFps:260\nenableVsync:true\noverrideWidth:900\noverrideHeight:630\nguiScale:2\nkey_key.hyw.freecamToggle:key.keyboard.u\n',encoding='ascii')
  account=uuid.UUID(bytes=hashlib.md5(('OfflinePlayer:'+name).encode()).digest(),version=3)
  args=[java,'-Xmx2G','-XX:TieredStopAtLevel=1','-Dfile.encoding=UTF-8','-Djava.library.path='+str(game/'natives'),'-cp',classpath,'net.fabricmc.loader.impl.launch.knot.KnotClient',
        '--username',name,'--version','RTS-QA','--gameDir',str(game),'--assetsDir',str(mc/'assets'),'--assetIndex','17','--uuid',str(account).replace('-',''),'--accessToken','0','--userType','legacy','--versionType','release','--quickPlayPath',str(game/'quickplay.json'),'--quickPlayMultiplayer',LAB_ADDRESS,'--width','960','--height','640']
  if formation_qa:
   args.insert(1,f'-Dfabric.addMods={formation_qa}')
  with (lab/(name+log_suffix+'.out')).open('wb') as out:
   clients.append(subprocess.Popen(args,cwd=game,stdout=out,stderr=subprocess.STDOUT,creationflags=subprocess.CREATE_NO_WINDOW))
  for _ in range(120):
   if f'{name} joined the game' in server_log.read_text(encoding='utf-8',errors='replace'):
    command(f'attribute {name} minecraft:generic.gravity base set 0.08')
    break
   if clients[-1].poll() is not None:
    if index == 0:
     raise RuntimeError(f'{name} stopped')
    print(f'{name} stopped before joining; spawning a local opponent', flush=True)
    command('player RtsDummy spawn')
    break
   time.sleep(1)
  else: raise RuntimeError(f'{name} did not join')
 if '--two' not in sys.argv:
  command('player RtsDummy spawn')
 inbox=lab/'server.commands'
 inbox.write_text('',encoding='utf-8')
 command('say RTS_QA_COMMAND_INBOX_READY')
 print('LOCAL_CLIENTS_STARTED', flush=True)
 cursor=0
 for _ in range(900):
  pending=inbox.read_text(encoding='utf-8-sig').splitlines()
  for line in pending[cursor:]:
   if line.strip(): command(line.strip())
  cursor=len(pending)
  if server.poll() is not None: break
  if all(p.poll() is not None for p in clients): break
  time.sleep(1)
finally:
 for child in clients:
  if child.poll() is None:
   child.terminate()
   child.wait(timeout=10)
 if server is not None and server.poll() is None:
  command('stop')
  try: server.wait(30)
  except subprocess.TimeoutExpired:
   server.terminate()
   server.wait(timeout=10)
 if previous_properties is not None:
  properties.write_bytes(previous_properties)
