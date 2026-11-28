#!/usr/bin/env python3
"""Actual two JVM Manhunt restart, isolated fixture, no production writes."""
import glob, hashlib, json, os, queue, re, shutil, socket, subprocess, sys, threading, time
from pathlib import Path
root=Path(__file__).resolve().parents[1]
fixture=root/'server_test_restart'
source=root/'server_test_proof'
assert not fixture.exists(), "Preserve earlier fixture; choose a new test directory if repeating."
with socket.socket() as sock:
    sock.bind(('127.0.0.1',25573))
fixture.mkdir()
for name in ('libraries','versions','.fabric','mods'):
    shutil.copytree(source/name,fixture/name)
for name in ('fabric-server-launch.jar','eula.txt'):
    shutil.copy2(source/name,fixture/name)
shutil.copy2(root/'mod/kapisma/build/libs/kapisma-0.1.0.jar',fixture/'mods/kapisma-0.1.0.jar')
for jar in (root/'tools/test-mods').glob('*.jar'):
    shutil.copy2(jar,fixture/'mods'/jar.name)
props=dict(line.split('=',1) for line in (source/'server.properties').read_text(encoding='iso-8859-1').splitlines() if '=' in line and not line.startswith('#'))
props.update({'server-ip':'127.0.0.1','server-port':'25573','level-name':'world','level-seed':'123456789','level-type':r'minecraft\:normal','generator-settings':'{}','generate-structures':'true','white-list':'false','enforce-whitelist':'false','enable-rcon':'false','enable-query':'false','online-mode':'false','difficulty':'peaceful'})
(fixture/'server.properties').write_text('\n'.join(f'{k}={v}' for k,v in props.items())+'\n',encoding='iso-8859-1')
cfg=fixture/'config/kapisma';cfg.mkdir(parents=True)
pool=json.loads((source/'config/kapisma/seeds.json').read_text(encoding='utf-8-sig'))
target=next(e for e in pool['classes']['orman'] if str(e['seed'])=='9161362629027940354')
pool['classes']={'orman':[target]}
(cfg/'seeds.json').write_text(json.dumps(pool,ensure_ascii=False),encoding='utf-8')
voice=fixture/'config/voicechat';voice.mkdir()
v=(source/'config/voicechat/voicechat-server.properties').read_text(encoding='utf-8')
v=re.sub(r'(?m)^port=.*$','port=24458',v);v=re.sub(r'(?m)^bind_address=.*$','bind_address=127.0.0.1',v)
(voice/'voicechat-server.properties').write_text(v,encoding='utf-8')
java=glob.glob(os.path.expandvars(r"%APPDATA%\.minecraft\runtime\java-runtime-delta\*\java-runtime-delta\bin\java.exe"))[0]
print("FIXTURE",fixture,"JAR",hashlib.sha256((fixture/'mods/kapisma-0.1.0.jar').read_bytes()).hexdigest(),flush=True)

class Run:
    def __init__(self,index):
        self.p=subprocess.Popen([java,'-Xmx2G','-Dfile.encoding=UTF-8','-Dstdout.encoding=UTF-8','-jar','fabric-server-launch.jar','nogui'],cwd=fixture,stdin=subprocess.PIPE,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,text=True,encoding='utf-8',errors='replace')
        self.q=queue.Queue()
        self.lines=[]
        self.out=(root/f'tools/last_restart_jvm_{index}.log').open('x',encoding='utf-8')
        self.reader=threading.Thread(target=self.read,daemon=True);self.reader.start()
    def read(self):
        for line in self.p.stdout:
            self.lines.append(line)
            self.out.write(line);self.out.flush()
            self.q.put(line)
    def wait(self,needle,after=0,seconds=120):
        deadline=time.monotonic()+seconds
        while time.monotonic()<deadline:
            if any(needle in line for line in self.lines[after:]): return
            if self.p.poll() is not None: break
            try: self.q.get(timeout=.2)
            except queue.Empty: pass
        raise AssertionError(f"missing {needle}; tail: {''.join(self.lines[-10:])}")
    def cmd(self,command):
        i=len(self.lines)
        print(">>",command,flush=True)
        self.p.stdin.write(command+'\n');self.p.stdin.flush()
        return i
    def close(self):
        if self.p.poll() is None:
            try:self.cmd('stop');self.p.wait(60)
            except Exception:self.p.kill();self.p.wait()
        self.reader.join(5);self.out.close()
    def spawn(self):
        self.wait('Done (')
        self.wait('Kapısma hazir' if False else 'Kapisma hazir')
        for name in ('Bot1','Bot2'):
            i=self.cmd('player '+name+' spawn');self.wait(name+' joined the game',i)
        time.sleep(1.5)

first=Run(1)
try:
    first.spawn()
    marker=fixture/'world/restart-test-marker.txt'
    marker.write_text('old world sentinel',encoding='utf-8')
    i=first.cmd('kapisma game MANHUNT');first.wait('Oyun modu: MANHUNT',i)
    i=first.cmd('kapisma origins off');first.wait('ırk modu: OFF',i)
    i=first.cmd('kapisma seedclass orman');first.wait('Seed sınıfı: orman',i)
    i=first.cmd('kapisma start');first.wait('Sunucu 8 sn içinde yeniden başlayacak',i)
    i=first.cmd('kapisma start');first.wait('Yeni dünya için yeniden başlatma bekleniyor.',i)
    i=first.cmd('kapisma newworld');first.wait('Yeniden başlatma zaten planlandı.',i)
    assert first.p.wait(60)==0
    req=json.loads((fixture/'kapisma_next.json').read_text(encoding='utf-8'))
    assert str(req['seed'])==str(target['seed']) and req['class']=='orman',req
    assert marker.read_text(encoding='utf-8')=='old world sentinel'
    print("PASS real shutdown and exact pending seed",req,flush=True)
finally:first.close()

second=Run(2)
try:
    second.wait('Yeni dunya: seed='+str(target['seed']))
    second.spawn()
    assert not (fixture/'kapisma_next.json').exists()
    backups=list(fixture.glob('kapisma-world-backup-*'))
    assert len(backups)==1 and (backups[0]/'restart-test-marker.txt').read_text(encoding='utf-8')=='old world sentinel'
    assert (backups[0]/'level.dat').is_file()
    assert not (fixture/'world/restart-test-marker.txt').exists()
    i=second.cmd('seed');second.wait(str(target['seed']),i)
    i=second.cmd('kapisma start');second.wait('Manhunt ana dunyada: seed='+str(target['seed']),i)
    second.wait('Manhunt başladı!',i)
    i=second.cmd('kapisma stop');second.wait('MANHUNT kapandi',i)
    assert not (fixture/'kapisma_next.json').exists()
    print('PASS actual new world seed, old world archived, no restart loop, Manhunt started',flush=True)
finally:second.close()
assert second.p.returncode==0
print('RESTART END TO END PASS',flush=True)
