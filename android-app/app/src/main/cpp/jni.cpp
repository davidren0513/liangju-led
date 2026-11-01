#include <jni.h>
#include "engine.h"
#include <exception>
extern "C" JNIEXPORT void JNICALL Java_com_davidren_liangju_NativeAi_reset(JNIEnv *, jobject) { reset_cancel(); }
extern "C" JNIEXPORT void JNICALL Java_com_davidren_liangju_NativeAi_cancel(JNIEnv *, jobject) { cancel_generation(); }
extern "C" JNIEXPORT jbyteArray JNICALL Java_com_davidren_liangju_NativeAi_generate(JNIEnv * env, jobject, jstring path, jbyteArray input) {
    const char * raw_path = env->GetStringUTFChars(path, nullptr);
    if (!raw_path) return nullptr;
    std::string model_path(raw_path);
    env->ReleaseStringUTFChars(path, raw_path);
    std::string prompt(env->GetArrayLength(input), '\0');
    env->GetByteArrayRegion(input, 0, prompt.size(), reinterpret_cast<jbyte *>(prompt.data()));
    if (env->ExceptionCheck()) return nullptr;
    try {
        auto result = generate(model_path, prompt);
        jbyteArray output = env->NewByteArray(result.size());
        if (output) env->SetByteArrayRegion(output, 0, result.size(), reinterpret_cast<const jbyte *>(result.data()));
        return output;
    } catch (const std::exception & error) {
        env->ThrowNew(env->FindClass("java/io/IOException"), error.what());
        return nullptr;
    }
}
