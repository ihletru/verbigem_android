#include <jni.h>
#include <string>
#include <vector>
#include <memory>
#include <mutex>
#include <cctype>
#include <android/log.h>
#include <sys/stat.h>

#include "llama.h"

#define TAG "VerbigemLlamaNative"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

// Capture llama.cpp internal logs (GGML) so load failures are visible in logcat.
static void llama_log_callback(enum ggml_log_level level, const char * text, void * user_data) {
    (void)user_data;
    switch (level) {
        case GGML_LOG_LEVEL_ERROR:   __android_log_print(ANDROID_LOG_ERROR,   TAG, "[llama] %s", text); break;
        case GGML_LOG_LEVEL_WARN:    __android_log_print(ANDROID_LOG_WARN,    TAG, "[llama] %s", text); break;
        case GGML_LOG_LEVEL_INFO:    __android_log_print(ANDROID_LOG_INFO,    TAG, "[llama] %s", text); break;
        default:                     __android_log_print(ANDROID_LOG_DEBUG,   TAG, "[llama] %s", text); break;
    }
}

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

    // Capture llama.cpp internal logs so load failures show in logcat.
    llama_log_set(llama_log_callback, nullptr);

    // Diagnostics: verify file exists and size before attempting load.
    {
        struct stat st;
        if (stat(path, &st) != 0) {
            LOGE("Model file does NOT exist at: %s", path);
        } else {
            LOGI("Model file exists, size: %lld bytes", (long long)st.st_size);
        }
    }

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
    // Smaller context for translation (short sentences) -> less RAM, faster start.
    // 1024 is plenty for sentence/paragraph translation (per AngelSlim tuning guidance).
    cparams.n_ctx = 1024;
    cparams.n_threads = ctx->n_threads;
    // Batched prompt processing speeds up prefill.
    cparams.n_batch = 512;
    cparams.n_ubatch = 512;

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

    // Hy-MT2 is a Hunyuan-dense model: it REQUIRES the chat template with its
    // special tokens (<|hy_begin_of_sentence|>, <|hy_User|>, <|hy_Assistant|>,
    // <|hy_placeholder_no_2|>). A raw prompt without these tokens produces
    // garbage translations. Apply the built-in "hunyuan-dense" template.
    std::vector<llama_chat_message> chat_msgs(1);
    chat_msgs[0] = { "user", prompt.c_str() };

    int tpl_len = llama_chat_apply_template(
        "hunyuan-dense", chat_msgs.data(), chat_msgs.size(),
        true,  // add_generation_prompt -> appends <|hy_Assistant|>
        nullptr, 0);
    if (tpl_len < 0) {
        LOGE("Failed to apply hunyuan-dense chat template (size %d)", tpl_len);
        return env->NewStringUTF("");
    }
    std::string formatted;
    formatted.resize((size_t)tpl_len);
    int tpl_len2 = llama_chat_apply_template(
        "hunyuan-dense", chat_msgs.data(), chat_msgs.size(),
        true, formatted.data(), (int32_t)formatted.size());
    if (tpl_len2 < 0) {
        LOGE("Failed to render hunyuan-dense chat template (size %d)", tpl_len2);
        return env->NewStringUTF("");
    }
    formatted.resize((size_t)tpl_len2);
    LOGI("Applied hunyuan-dense template, formatted prompt len: %zu", formatted.size());

    // Tokenize the formatted prompt.
    // llama_tokenize with NULL buffer returns negative required size (not an error).
    // Negate to get the required size, per examples/simple-chat/simple-chat.cpp.
    std::vector<llama_token> tokens;
    int n_tokens = -llama_tokenize(vocab, formatted.c_str(), (int)formatted.size(), nullptr, 0, true, true);
    if (n_tokens <= 0) {
        LOGE("Failed to tokenize prompt (size %d)", n_tokens);
        return env->NewStringUTF("");
    }
    tokens.resize(n_tokens);
    n_tokens = llama_tokenize(vocab, formatted.c_str(), (int)formatted.size(), tokens.data(), (int)tokens.size(), true, true);
    if (n_tokens <= 0) {
        LOGE("Failed to tokenize prompt (final %d)", n_tokens);
        return env->NewStringUTF("");
    }
    tokens.resize(n_tokens);

    // Clear KV cache before each generation. Without this, the context retains
    // tokens from the previous translate() call, so the model "sees" the old
    // text appended to the new prompt and re-translates the old input.
    llama_memory_clear(llama_get_memory(ctx->ctx), false);

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

// Streaming variant: calls the Kotlin lambda (String) -> Unit after each decoded
// piece so the UI can render partial translation incrementally.
JNIEXPORT void JNICALL
Java_com_verbigem_app_jni_LlamaNativeBridge_generateNativeStreaming(
        JNIEnv *env,
        jobject thiz,
        jlong handle,
        jstring prompt_jstr,
        jint max_tokens,
        jobject callback) {

    if (handle == 0) {
        LOGE("Invalid native handle (0)");
        return;
    }

    auto ctx = reinterpret_cast<LlamaModelContext *>(handle);
    if (!ctx || !ctx->is_loaded || !ctx->model || !ctx->ctx) {
        LOGE("Model context is null or not loaded");
        return;
    }

    // Resolve the TokenStreamCallback.onToken(String) method.
    // We use a named interface (not a Kotlin lambda) because Kotlin inlines lambdas
    // into synthetic classes without a stable invoke method, which would make
    // GetMethodID fail. The interface class always exposes onToken(Ljava/lang/String;)V.
    jclass callbackClass = env->GetObjectClass(callback);
    jmethodID invokeMethod = env->GetMethodID(callbackClass, "onToken", "(Ljava/lang/String;)V");
    if (!invokeMethod) {
        LOGE("Failed to resolve TokenStreamCallback.onToken(String) method");
        env->DeleteLocalRef(callbackClass);
        return;
    }

    const char *prompt_cstr = env->GetStringUTFChars(prompt_jstr, nullptr);
    std::string prompt = prompt_cstr ? prompt_cstr : "";
    if (prompt_cstr) env->ReleaseStringUTFChars(prompt_jstr, prompt_cstr);

    LOGI("Generating translation (streaming, prompt len: %zu)", prompt.length());

    const llama_vocab * vocab = llama_model_get_vocab(ctx->model);

    std::vector<llama_chat_message> chat_msgs(1);
    chat_msgs[0] = { "user", prompt.c_str() };

    int tpl_len = llama_chat_apply_template(
        "hunyuan-dense", chat_msgs.data(), chat_msgs.size(),
        true, nullptr, 0);
    if (tpl_len < 0) {
        LOGE("Failed to apply hunyuan-dense chat template (size %d)", tpl_len);
        env->DeleteLocalRef(callbackClass);
        return;
    }
    std::string formatted;
    formatted.resize((size_t)tpl_len);
    int tpl_len2 = llama_chat_apply_template(
        "hunyuan-dense", chat_msgs.data(), chat_msgs.size(),
        true, formatted.data(), (int32_t)formatted.size());
    if (tpl_len2 < 0) {
        LOGE("Failed to render hunyuan-dense chat template (size %d)", tpl_len2);
        env->DeleteLocalRef(callbackClass);
        return;
    }
    formatted.resize((size_t)tpl_len2);

    std::vector<llama_token> tokens;
    int n_tokens = -llama_tokenize(vocab, formatted.c_str(), (int)formatted.size(), nullptr, 0, true, true);
    if (n_tokens <= 0) {
        LOGE("Failed to tokenize prompt (size %d)", n_tokens);
        env->DeleteLocalRef(callbackClass);
        return;
    }
    tokens.resize(n_tokens);
    n_tokens = llama_tokenize(vocab, formatted.c_str(), (int)formatted.size(), tokens.data(), (int)tokens.size(), true, true);
    if (n_tokens <= 0) {
        LOGE("Failed to tokenize prompt (final %d)", n_tokens);
        env->DeleteLocalRef(callbackClass);
        return;
    }
    tokens.resize(n_tokens);

    llama_memory_clear(llama_get_memory(ctx->ctx), false);

    if (llama_decode(ctx->ctx, llama_batch_get_one(tokens.data(), (int)tokens.size()))) {
        LOGE("Failed to eval prompt");
        env->DeleteLocalRef(callbackClass);
        return;
    }

    const int n_predict = max_tokens > 0 ? max_tokens : 512;

    llama_sampler * smpl = llama_sampler_init_greedy();

    std::string result;
    std::string stream_buf;  // buffered text sent to UI word-by-word (not char-by-char)
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
            // Hunyuan tokens usually START with a leading space (e.g. " translate").
            // Flush the previously buffered word when the new token begins a new word,
            // so the UI receives complete words instead of subword fragments or whole
            // sentences at once.
            bool starts_word = (len > 0 && std::isspace((unsigned char)buf[0]));
            if (starts_word && !stream_buf.empty()) {
                jstring piece = env->NewStringUTF(stream_buf.c_str());
                env->CallVoidMethod(callback, invokeMethod, piece);
                env->DeleteLocalRef(piece);
                stream_buf.clear();
            }
            stream_buf.append(buf, len);
        }

        if (llama_decode(ctx->ctx, llama_batch_get_one(&new_token, 1))) {
            LOGE("Failed to eval token");
            break;
        }
        n_decoded++;
    }

    // Emit any trailing word left in the buffer (text that didn't end with whitespace).
    if (!stream_buf.empty()) {
        jstring piece = env->NewStringUTF(stream_buf.c_str());
        env->CallVoidMethod(callback, invokeMethod, piece);
        env->DeleteLocalRef(piece);
        stream_buf.clear();
    }

    llama_sampler_free(smpl);

    LOGI("Generated (streaming) %d tokens", n_decoded);

    env->DeleteLocalRef(callbackClass);
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
