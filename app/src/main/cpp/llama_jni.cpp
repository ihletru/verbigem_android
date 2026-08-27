#include <jni.h>
#include <string>
#include <vector>
#include <memory>
#include <mutex>
#include <android/log.h>

#include "llama.h"

#define TAG "VerbigemLlamaNative"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

struct LlamaModelContext {
    llama_model * model = nullptr;
    llama_context * ctx = nullptr;
    int n_threads = 4;
    bool is_loaded = false;
};

static std::mutex g_mutex;

extern "C" {

JNIEXPORT jboolean JNICALL
Java_com_verbigem_app_jni_LlamaNativeBridge_isNativeSupported(JNIEnv *env, jobject thiz) {
#if defined(__aarch64__) || defined(__arm__) || defined(__x86_64__)
    return JNI_TRUE;
#else
    return JNI_FALSE;
#endif
}

JNIEXPORT jlong JNICALL
Java_com_verbigem_app_jni_LlamaNativeBridge_loadModelNative(
        JNIEnv *env,
        jobject thiz,
        jstring model_path_jstr,
        jint n_threads,
        jint n_gpu_layers) {

    const char *path = env->GetStringUTFChars(model_path_jstr, nullptr);
    if (!path) {
        LOGE("Failed to get model path string");
        return 0;
    }

    LOGI("Loading native Hy-MT2 GGUF model from: %s (threads: %d, gpu_layers: %d)", path, n_threads, n_gpu_layers);

    llama_backend_init();

    auto ctx = new LlamaModelContext();
    ctx->n_threads = n_threads > 0 ? n_threads : 4;

    llama_model_params mparams = llama_model_default_params();
    mparams.n_gpu_layers = n_gpu_layers;

    ctx->model = llama_model_load_from_file(path, mparams);
    if (!ctx->model) {
        LOGE("Failed to load model from: %s", path);
        delete ctx;
        env->ReleaseStringUTFChars(model_path_jstr, path);
        return 0;
    }

    llama_context_params cparams = llama_context_default_params();
    cparams.n_ctx = 4096;
    cparams.n_threads = ctx->n_threads;

    ctx->ctx = llama_init_from_model(ctx->model, cparams);
    if (!ctx->ctx) {
        LOGE("Failed to create context");
        llama_model_free(ctx->model);
        delete ctx;
        env->ReleaseStringUTFChars(model_path_jstr, path);
        return 0;
    }

    ctx->is_loaded = true;
    LOGI("Model loaded successfully");

    env->ReleaseStringUTFChars(model_path_jstr, path);
    return reinterpret_cast<jlong>(ctx);
}

JNIEXPORT jstring JNICALL
Java_com_verbigem_app_jni_LlamaNativeBridge_generateNative(
        JNIEnv *env,
        jobject thiz,
        jlong handle,
        jstring prompt_jstr,
        jint max_tokens) {

    if (handle == 0) {
        LOGE("Invalid native handle (0)");
        return env->NewStringUTF("");
    }

    auto ctx = reinterpret_cast<LlamaModelContext *>(handle);
    if (!ctx || !ctx->is_loaded || !ctx->model || !ctx->ctx) {
        LOGE("Model context is null or not loaded");
        return env->NewStringUTF("");
    }

    const char *prompt_cstr = env->GetStringUTFChars(prompt_jstr, nullptr);
    std::string prompt = prompt_cstr ? prompt_cstr : "";
    if (prompt_cstr) env->ReleaseStringUTFChars(prompt_jstr, prompt_cstr);

    LOGI("Generating translation (prompt len: %zu)", prompt.length());

    const llama_vocab * vocab = llama_model_get_vocab(ctx->model);

    // Tokenize the prompt (with BOS)
    std::vector<llama_token> tokens;
    int n_tokens = llama_tokenize(vocab, prompt.c_str(), (int)prompt.size(), nullptr, 0, true, false);
    if (n_tokens <= 0) {
        LOGE("Failed to tokenize prompt (size %d)", n_tokens);
        return env->NewStringUTF("");
    }
    tokens.resize(n_tokens);
    n_tokens = llama_tokenize(vocab, prompt.c_str(), (int)prompt.size(), tokens.data(), (int)tokens.size(), true, false);
    if (n_tokens <= 0) {
        LOGE("Failed to tokenize prompt (final %d)", n_tokens);
        return env->NewStringUTF("");
    }
    tokens.resize(n_tokens);

    // Evaluate the prompt
    if (llama_decode(ctx->ctx, llama_batch_get_one(tokens.data(), (int)tokens.size()))) {
        LOGE("Failed to eval prompt");
        return env->NewStringUTF("");
    }

    const int n_predict = max_tokens > 0 ? max_tokens : 512;

    llama_sampler * smpl = llama_sampler_init_greedy();

    std::string result;
    llama_token new_token;
    int n_decoded = 0;

    for (int i = 0; i < n_predict; i++) {
        new_token = llama_sampler_sample(smpl, ctx->ctx, -1);

        if (llama_vocab_is_eog(vocab, new_token)) {
            break;
        }

        char buf[256];
        int len = llama_token_to_piece(vocab, new_token, buf, sizeof(buf), 0, false);
        if (len > 0) {
            result.append(buf, len);
        }

        if (llama_decode(ctx->ctx, llama_batch_get_one(&new_token, 1))) {
            LOGE("Failed to eval token");
            break;
        }
        n_decoded++;
    }

    llama_sampler_free(smpl);

    LOGI("Generated %d tokens", n_decoded);

    return env->NewStringUTF(result.c_str());
}

JNIEXPORT void JNICALL
Java_com_verbigem_app_jni_LlamaNativeBridge_freeModelNative(
        JNIEnv *env,
        jobject thiz,
        jlong handle) {

    if (handle != 0) {
        std::lock_guard<std::mutex> lock(g_mutex);
        auto ctx = reinterpret_cast<LlamaModelContext *>(handle);
        if (ctx->ctx) llama_free(ctx->ctx);
        if (ctx->model) llama_model_free(ctx->model);
        ctx->is_loaded = false;
        delete ctx;
        LOGI("Native Hy-MT2 model context freed successfully");
    }
}

} // extern "C"
