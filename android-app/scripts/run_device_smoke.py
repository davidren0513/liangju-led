#!/usr/bin/env python3
"""Provision verified models and run Android integration tests without Gradle uninstalling app data.
Usage: python scripts/run_device_smoke.py MODEL_DIR ZH_PCM [ADB_PATH] [SERIAL]
Run assembleDebug and assembleDebugAndroidTest first. Use a dedicated test device/emulator.
"""
import hashlib,json,pathlib,subprocess,sys
root=pathlib.Path(__file__).resolve().parents[1]
models,pcm=map(pathlib.Path,sys.argv[1:3]);adb=sys.argv[3] if len(sys.argv)>3 else 'adb';serial=sys.argv[4] if len(sys.argv)>4 else 'emulator-5554'
command=[adb,'-s',serial]
def run(*args,**kwargs):return subprocess.run(command+list(args),check=True,**kwargs)
run('install','-r',str(root/'app/build/outputs/apk/debug/app-debug.apk'))
run('install','-r',str(root/'app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk'))
run('shell','run-as','com.davidren.liangju','mkdir','-p','files/models')
manifest=json.loads((root/'app/src/main/assets/models.json').read_text())
for group in manifest.values():
 for spec in group:
  f=models/spec['file']
  with f.open('rb') as stream:
   assert hashlib.file_digest(stream,'sha256').hexdigest()==spec['sha256'],f
  with f.open('rb') as stream:
   run('exec-in','run-as','com.davidren.liangju','dd','of=files/models/'+f.name,'bs=65536',stdin=stream,stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)
  run('exec-in','run-as','com.davidren.liangju','dd','of=files/models/'+f.name+'.verified','bs=64',input=spec['sha256'].encode(),stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)
  print('Installed verified model',f.name,flush=True)
with pcm.open('rb') as stream:run('exec-in','run-as','com.davidren.liangju','dd','of=files/zh.pcm','bs=65536',stdin=stream,stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)
result=run('shell','am','instrument','-w','-r','com.davidren.liangju.test/androidx.test.runner.AndroidJUnitRunner',capture_output=True,text=True)
print(result.stdout,flush=True)
assert 'FAILURES!!!' not in result.stdout and 'INSTRUMENTATION_FAILED' not in result.stdout
assert 'INSTRUMENTATION_STATUS_CODE: -3' not in result.stdout,'Model test was skipped'
assert 'OK (' in result.stdout and 'INSTRUMENTATION_CODE: -1' in result.stdout,'Test run did not complete'
(root/'design').mkdir(exist_ok=True)
with (root/'design/native-home.png').open('wb') as f:run('exec-out','run-as','com.davidren.liangju','cat','files/qa-home.png',stdout=f)
