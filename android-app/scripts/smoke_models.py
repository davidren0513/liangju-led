#!/usr/bin/env python3
"""Run the same C++ generation engine on a host and sherpa's pinned speech engine.
Usage: python scripts/smoke_models.py MODEL_DIR HOST_BINARY CHINESE_16K_WAV
Requires sherpa-onnx==2.0.1 and soundfile. Does not download or upload audio.
"""
import json, pathlib, re, subprocess, sys, tempfile, time
import sherpa_onnx
import soundfile as sf
root=pathlib.Path(__file__).resolve().parents[1]
models,binary,fixture=map(pathlib.Path,sys.argv[1:4])
source=(root/'app/src/main/java/com/davidren/liangju/AiEngine.kt').read_text()
prompt=source.split('return """')[1].split('"""')[0]
cases=['麻烦前面的朋友让一下，我想过去，谢谢。','师傅，我在马路对面，穿白色衣服，你等我一下。','别在东门等，来西门找我。','下午3:30在2号门接我。','师傅我在对面，传白衣服，你等我一下。','我想让你帮我弄个牌子，上面写前面施工，请绕道。']
report={'ai':[]}
for raw in cases:
    with tempfile.NamedTemporaryFile(mode='w',suffix='.txt',encoding='utf-8') as f:
        f.write(prompt.replace('$safe',raw)); f.flush(); start=time.monotonic()
        result=subprocess.run([str(binary.resolve()),str((models/'qwen2.5-0.5b-instruct-q4_k_m.gguf').resolve()),f.name],text=True,capture_output=True,check=True)
        parsed=json.loads(result.stdout)
        assert isinstance(parsed['text'],str) and 0<len(parsed['text'])<=80
        report['ai'].append({'input':raw,'output':parsed['text'],'seconds':round(time.monotonic()-start,2)})
start=time.monotonic()
recognizer=sherpa_onnx.OfflineRecognizer.from_sense_voice(model=str(models/'sensevoice.int8.onnx'),tokens=str(models/'tokens.txt'),num_threads=4,language='zh',use_itn=True,debug=False)
audio,rate=sf.read(fixture,dtype='float32'); assert rate==16000 and audio.ndim==1
stream=recognizer.create_stream();stream.accept_waveform(rate,audio);recognizer.decode_stream(stream)
assert re.search('[\u4e00-\u9fff]',stream.result.text)
report['speech']={'text':stream.result.text,'audioSeconds':round(len(audio)/rate,2),'seconds':round(time.monotonic()-start,2)}
print(json.dumps(report,ensure_ascii=False,indent=2))
