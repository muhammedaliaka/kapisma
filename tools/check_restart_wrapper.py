#!/usr/bin/env python3
"""Exercise the exact candidate Windows loop with mocked Java exits in temporary directories."""
import os, subprocess, sys, tempfile, zipfile
from pathlib import Path
root=Path(__file__).resolve().parents[1]
pkg=root/'release/kapisma-sunucu-2026-11-25-restart-treasure-aday.zip'
with zipfile.ZipFile(pkg) as z:
    bat=z.read('kapisma-sunucu/start.bat').decode('utf-8').replace('\r\n','\n')
    sh=z.read('kapisma-sunucu/start.sh').decode('utf-8')
loop=bat[bat.index(':loop\n'):bat.index('\nrem ====================== alt programlar')]
assert loop.count('"!JAVA!" !FLAGS! -jar fabric-server-launch.jar nogui')==1
loop=loop.replace('"!JAVA!" !FLAGS! -jar fabric-server-launch.jar nogui','call "%~dp0mock_java.bat"')
for kind,expected_exit,expected_calls in [('failure',7,1),('normal',0,1),('restart',0,2)]:
    with tempfile.TemporaryDirectory(prefix='kapisma-wrapper-') as d:
        d=Path(d)
        (d/'launch.bat').write_text('@echo off\nsetlocal EnableDelayedExpansion\n'+loop,encoding='ascii')
        if kind=='failure':body='>calls.txt echo 1\nexit /b 7\n';(d/'kapisma_next.json').write_text('{}')
        elif kind=='normal':body='>calls.txt echo 1\nexit /b 0\n'
        else:body='if exist calls.txt goto second\n>calls.txt echo 1\n>kapisma_next.json echo {}\nexit /b 0\n:second\n>calls.txt echo 2\ndel kapisma_next.json\nexit /b 0\n'
        (d/'mock_java.bat').write_text('@echo off\n'+body,encoding='ascii')
        r=subprocess.run(['cmd.exe','/d','/c',str(d/'launch.bat')],cwd=d,input='\n\n',text=True,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,timeout=15)
        assert r.returncode==expected_exit and int((d/'calls.txt').read_text().strip())==expected_calls,(kind,r.returncode,r.stdout)
        print('PASS Windows',kind,'exit',r.returncode,'calls',expected_calls)
assert 'RUN_EXIT=$?' in sh and 'exit "$RUN_EXIT"' in sh
print('Linux wrapper static check PASS; actual shell execution tested separately if available')
