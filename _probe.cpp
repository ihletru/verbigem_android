// Faithful replica of Verbigem's JNI generation path, so A/B results transfer to the app.
//   llama_chat_apply_template("hunyuan-dense", [{"user", prompt}], add_ass=true)
//   -> tokenize(parse_special=true) -> decode -> sample
//
// usage: probe <model> <prompt> <temp> <top_k> <top_p> <repeat_penalty> <n_predict> <threads>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <string>
#include <vector>
#include "llama.h"

int main(int argc, char ** argv) {
    if (argc < 9) { fprintf(stderr, "usage: probe model prompt temp topk topp repen npred threads\n"); return 2; }
    const char * model_path = argv[1];
    std::string  prompt     = argv[2];
    float temp   = atof(argv[3]);
    int   top_k  = atoi(argv[4]);
    float top_p  = atof(argv[5]);
    float repen  = atof(argv[6]);
    int   npred  = atoi(argv[7]);
    int   nthread= atoi(argv[8]);

    llama_backend_init();

    llama_model_params mparams = llama_model_default_params();
    llama_model * model = llama_model_load_from_file(model_path, mparams);
    if (!model) { fprintf(stderr, "load failed\n"); return 1; }

    llama_context_params cparams = llama_context_default_params();
    cparams.n_ctx    = 1024;
    cparams.n_threads = nthread;
    cparams.n_batch  = 512;
    cparams.n_ubatch = 512;
    llama_context * ctx = llama_init_from_model(model, cparams);
    if (!ctx) { fprintf(stderr, "ctx failed\n"); return 1; }

    const llama_vocab * vocab = llama_model_get_vocab(model);

    std::vector<llama_chat_message> msgs(1);
    msgs[0] = { "user", prompt.c_str() };
    int n = llama_chat_apply_template("hunyuan-dense", msgs.data(), 1, true, nullptr, 0);
    if (n < 0) { fprintf(stderr, "template failed\n"); return 1; }
    std::string formatted; formatted.resize((size_t)n);
    n = llama_chat_apply_template("hunyuan-dense", msgs.data(), 1, true, formatted.data(), (int32_t)formatted.size());
    formatted.resize((size_t)n);

    std::vector<llama_token> tokens;
    int nt = -llama_tokenize(vocab, formatted.c_str(), (int)formatted.size(), nullptr, 0, true, true);
    if (nt <= 0) { fprintf(stderr, "tokenize failed\n"); return 1; }
    tokens.resize(nt);
    nt = llama_tokenize(vocab, formatted.c_str(), (int)formatted.size(), tokens.data(), (int)tokens.size(), true, true);
    tokens.resize(nt);

    if (getenv("PROBE_DUMP")) {
        fprintf(stderr, "[tokens]");
        for (size_t i = 0; i < tokens.size() && i < 40; i++) {
            char buf[128]; llama_token_to_piece(vocab, tokens[i], buf, sizeof(buf), 0, true);
            fprintf(stderr, " %d:'%s'", tokens[i], buf);
        }
        fprintf(stderr, "\n");
    }

    llama_memory_clear(llama_get_memory(ctx), false);
    if (llama_decode(ctx, llama_batch_get_one(tokens.data(), (int)tokens.size()))) {
        fprintf(stderr, "decode failed\n"); return 1;
    }

    llama_sampler * smpl = nullptr;
    if (temp <= 0.0f) {
        smpl = llama_sampler_init_greedy();
    } else {
        auto cp = llama_sampler_chain_default_params(); cp.no_perf = true;
        smpl = llama_sampler_chain_init(cp);
        llama_sampler_chain_add(smpl, llama_sampler_init_penalties(
            llama_vocab_n_tokens(vocab), 64, repen, 0.0f, 0.0f));
        llama_sampler_chain_add(smpl, llama_sampler_init_top_k(top_k));
        llama_sampler_chain_add(smpl, llama_sampler_init_top_p(top_p, 1));
        llama_sampler_chain_add(smpl, llama_sampler_init_temp(temp));
        llama_sampler_chain_add(smpl, llama_sampler_init_dist(LLAMA_DEFAULT_SEED));
    }

    std::string out;
    for (int i = 0; i < npred; i++) {
        llama_token t = llama_sampler_sample(smpl, ctx, -1);
        if (llama_vocab_is_eog(vocab, t)) break;
        char buf[256];
        int len = llama_token_to_piece(vocab, t, buf, sizeof(buf), 0, false);
        if (len > 0) out.append(buf, len);
        if (llama_decode(ctx, llama_batch_get_one(&t, 1))) break;
    }
    printf("%s\n", out.c_str());
    fflush(stdout);

    llama_sampler_free(smpl);
    llama_free(ctx);
    llama_model_free(model);
    llama_backend_free();
    return 0;
}
