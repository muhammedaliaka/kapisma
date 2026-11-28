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
for name in ('libraries','versions','config','mods'):
 if not (root/name).exists(): shutil.copytree(rts/name,root/name)
for name in ('eula.txt','fabric-server-launch.jar'):
 shutil.copy2(rts/name,root/name)
datapacks=root/'world'/'datapacks'
datapacks.mkdir(parents=True,exist_ok=True)
shutil.copy2(rts/'world/datapacks/devletler-rts.zip',datapacks)
props=(rts/'server.properties').read_text()
(root/'server.properties').write_text(props.replace('server-port=25576',f'server-port={LAB_PORT}').replace('online-mode=true','online-mode=false')+'enforce-secure-profile=false\n',encoding='ascii')
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
server_log=lab/'server.out'
with server_log.open('wb') as out:
 server=subprocess.Popen([java,'-Xmx2G','-Dfile.encoding=UTF-8','-jar','fabric-server-launch.jar','nogui'],cwd=root,stdin=subprocess.PIPE,stdout=out,stderr=subprocess.STDOUT)
clients=[]
def command(cmd):
 server.stdin.write((cmd+'\n').encode('utf-8'))
 server.stdin.flush()
try:
 for _ in range(150):
  if 'Done (' in server_log.read_text(encoding='utf-8',errors='replace'): break
  if server.poll() is not None: raise RuntimeError('server failed')
  time.sleep(1)
 else: raise RuntimeError('server boot timeout')
 command('function krts:reset')
 for name in ('RtsQA1','RtsQA2'):
  game=lab/name
  (game/'mods').mkdir(parents=True,exist_ok=True)
  for path in (rts/'mods').glob('*.jar'):
   if path.name.startswith(('fabric-api','HundredYearsWar')): shutil.copy2(path,game/'mods')
  (game/'options.txt').write_text('pauseOnLostFocus:false\nrenderDistance:4\nsimulationDistance:4\nguiScale:2\nkey_key.hyw.freecamToggle:key.keyboard.v\n',encoding='ascii')
  account=uuid.UUID(bytes=hashlib.md5(('OfflinePlayer:'+name).encode()).digest(),version=3)
  args=[java,'-Xmx2G','-Dfile.encoding=UTF-8','-Djava.library.path='+str(version_dir/'natives'),'-cp',classpath,'net.fabricmc.loader.impl.launch.knot.KnotClient',
        '--username',name,'--version','RTS-QA','--gameDir',str(game),'--assetsDir',str(mc/'assets'),'--assetIndex','17','--uuid',str(account).replace('-',''),'--accessToken','0','--userType','legacy','--versionType','release','--quickPlayPath',str(game/'quickplay.json'),'--quickPlayMultiplayer',LAB_ADDRESS,'--width','960','--height','640']
  with (lab/(name+'.out')).open('wb') as out:
   clients.append(subprocess.Popen(args,cwd=game,stdout=out,stderr=subprocess.STDOUT,creationflags=subprocess.CREATE_NO_WINDOW))
  time.sleep(3)
 print('LOCAL_CLIENTS_STARTED', flush=True)
 inbox=lab/'server.commands'
 inbox.write_text('',encoding='utf-8')
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
  if child.poll() is None: child.terminate()
 if server.poll() is None:
  command('stop')
  try: server.wait(30)
  except subprocess.TimeoutExpired: server.terminate()
