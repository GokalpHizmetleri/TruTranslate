#include <jni.h>
#include <string>
#include <vector>
#include <cstring>
#include <mutex>
#include "llama.h"
#include "android/log.h"

static llama_model * g_model = nullptr;
static llama_context * g_ctx = nullptr;
static const llama_vocab * g_vocab = nullptr;
static std::string g_last_error;
static std::string g_log_tail;
static std::mutex g_log_mutex;

static void log_capture(ggml_log_level level, const char * text, void *) {
    std::lock_guard<std::mutex> lk(g_log_mutex);
    g_log_tail.append(text);
    if (g_log_tail.size() > 6000) g_log_tail.erase(0, g_log_tail.size() - 6000);
    if (level >= GGML_LOG_LEVEL_ERROR) {
        g_last_error.append(text);
        if (g_last_error.size() > 4000) g_last_error.erase(0, g_last_error.size() - 4000);
    }
    __android_log_write(ANDROID_LOG_INFO, "TruTranslate", text);
}

static void set_error(const std::string & s) { g_last_error = s; }

extern "C" JNIEXPORT jstring JNICALL
Java_com_trutranslate_app_LlamaBridge_getLastError(JNIEnv * env, jobject) {
    std::lock_guard<std::mutex> lk(g_log_mutex);
    std::string s = g_last_error;
    if (s.empty()) s = g_log_tail;
    return env->NewStringUTF(s.c_str());
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_trutranslate_app_LlamaBridge_loadModel(JNIEnv * env, jobject, jstring jpath, jint nCtx, jint nThreads) {
    if (g_model && g_ctx) return JNI_TRUE;
    try {
        const char * path = env->GetStringUTFChars(jpath, nullptr);
        llama_log_set(log_capture, nullptr);
        g_last_error.clear();
        llama_backend_init();
        llama_model_params mparams = llama_model_default_params();
        mparams.n_gpu_layers = 0;
        mparams.use_mmap = true;
        mparams.use_mlock = false;
        g_model = llama_model_load_from_file(path, mparams);
        env->ReleaseStringUTFChars(jpath, path);
        if (!g_model) { set_error("Model dosyasi yuklenemedi (llama_model_load_from_file)."); return JNI_FALSE; }
        llama_context_params cparams = llama_context_default_params();
        cparams.n_ctx = (uint32_t) nCtx;
        cparams.n_batch = 256;
        cparams.n_ubatch = 256;
        cparams.n_threads = nThreads;
        cparams.n_threads_batch = nThreads;
        g_ctx = llama_init_from_model(g_model, cparams);
        if (!g_ctx) { llama_model_free(g_model); g_model = nullptr; set_error("Baglam (context) olusturulamadi - yetersiz bellek olabilir."); return JNI_FALSE; }
        g_vocab = llama_model_get_vocab(g_model);
        return JNI_TRUE;
    } catch (const std::exception & e) {
        set_error(std::string("Native hata: ") + e.what());
        if (g_ctx) { llama_free(g_ctx); g_ctx = nullptr; }
        if (g_model) { llama_model_free(g_model); g_model = nullptr; }
        return JNI_FALSE;
    } catch (...) {
        set_error("Bilinmeyen native hata (model yukleme).");
        if (g_ctx) { llama_free(g_ctx); g_ctx = nullptr; }
        if (g_model) { llama_model_free(g_model); g_model = nullptr; }
        return JNI_FALSE;
    }
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_trutranslate_app_LlamaBridge_isLoaded(JNIEnv *, jobject) {
    return (g_model && g_ctx) ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT void JNICALL
Java_com_trutranslate_app_LlamaBridge_freeModel(JNIEnv *, jobject) {
    if (g_ctx) { llama_free(g_ctx); g_ctx = nullptr; }
    if (g_model) { llama_model_free(g_model); g_model = nullptr; }
    llama_backend_free();
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_trutranslate_app_LlamaBridge_translate(JNIEnv * env, jobject, jstring jtext, jstring jlang) {
    if (!g_model || !g_ctx) return env->NewStringUTF("Model yuklu degil");
    try {
        const char * text = env->GetStringUTFChars(jtext, nullptr);
        const char * lang = env->GetStringUTFChars(jlang, nullptr);

        std::string user = "Translate the following segment into ";
        user += lang;
        user += ", without additional explanation：";
        user += text;
        env->ReleaseStringUTFChars(jtext, text);
        env->ReleaseStringUTFChars(jlang, lang);

        std::string prompt;
        const char * tmpl = llama_model_chat_template(g_model, nullptr);
        if (tmpl) {
            llama_chat_message msgs[1];
            msgs[0].role = "user";
            msgs[0].content = user.c_str();
            int32_t need = llama_chat_apply_template(tmpl, msgs, 1, true, nullptr, 0);
            if (need > 0) {
                std::vector<char> buf(need + 1);
                int32_t wrote = llama_chat_apply_template(tmpl, msgs, 1, true, buf.data(), (int32_t) buf.size());
                if (wrote > 0) prompt.assign(buf.data(), wrote);
            }
        }
        if (prompt.empty()) prompt = user;

        llama_memory_clear(llama_get_memory(g_ctx), true);

        std::vector<llama_token> toks(1040);
        int32_t n_toks = llama_tokenize(g_vocab, prompt.c_str(), (int32_t) prompt.size(), toks.data(), (int32_t) toks.size(), true, true);
        if (n_toks < 0) {
            toks.resize(-n_toks + 16);
            n_toks = llama_tokenize(g_vocab, prompt.c_str(), (int32_t) prompt.size(), toks.data(), (int32_t) toks.size(), true, true);
        }
        if (n_toks <= 0) return env->NewStringUTF("Tokenize hatasi");

        llama_batch batch = llama_batch_get_one(toks.data(), n_toks);
        if (llama_decode(g_ctx, batch) != 0) return env->NewStringUTF("Decode hatasi");

        llama_sampler * smpl = llama_sampler_chain_init(llama_sampler_chain_default_params());
        llama_sampler_chain_add(smpl, llama_sampler_init_penalties(llama_vocab_n_tokens(g_vocab), 64, 1.05f, 0.0f, 0.0f));
        llama_sampler_chain_add(smpl, llama_sampler_init_top_k(20));
        llama_sampler_chain_add(smpl, llama_sampler_init_top_p(0.6f, 1));
        llama_sampler_chain_add(smpl, llama_sampler_init_temp(0.7f));
        llama_sampler_chain_add(smpl, llama_sampler_init_dist(1234));

        std::string out;
        char piece[256];
        for (int i = 0; i < 1024; i++) {
            llama_token id = llama_sampler_sample(smpl, g_ctx, -1);
            if (llama_vocab_is_eog(g_vocab, id)) break;
            int32_t n = llama_token_to_piece(g_vocab, id, piece, sizeof(piece), 0, true);
            if (n > 0) out.append(piece, n);
            llama_sampler_accept(smpl, id);
            llama_token next[1] = { id };
            llama_batch nb = llama_batch_get_one(next, 1);
            if (llama_decode(g_ctx, nb) != 0) break;
        }
        llama_sampler_free(smpl);

        size_t s = out.find_first_not_of(" \t\r\n");
        size_t e = out.find_last_not_of(" \t\r\n");
        std::string trimmed = (s == std::string::npos) ? "" : out.substr(s, e - s + 1);
        return env->NewStringUTF(trimmed.c_str());
    } catch (const std::exception & e) {
        return env->NewStringUTF((std::string("Hata: ") + e.what()).c_str());
    } catch (...) {
        return env->NewStringUTF("Hata: bilinmeyen native hata (ceviri).");
    }
}
