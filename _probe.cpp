// Faithful replica of Verbigem's JNI generation path, so A/B results transfer to the app.
//   llama_chat_apply_template("hunyuan-dense", [{"user", prompt}], add_ass=true)
//   -> tokenize(parse_special=true) -> decode -> sample
// Context matches llama_jni.cpp: n_ctx=1024, n_batch=512, n_ubatch=512.
//
// WHY THIS EXISTS: `llama-completion -p` puts the text in the SYSTEM slot
// (<bos>{prompt}<|hy_User|>), the app uses the USER role
// (<|hy_User|>{prompt}<|hy_Assistant|>). Results differ wildly — llama-completion
// once produced "Razorowowowow..." from a healthy model. Never benchmark the app
// with llama-completion.
//
// usage (single): probe -s <model> <prompt> <temp> <top_k> <top_p> <repen> <npred> <threads>
// usage (batch) : probe -b <file> <model> <temp> <top_k> <top_p> <repen> <npred> <threads> [reps]
//   batch file = prompts separated by a line containing exactly ###PROMPT###
//   batch output = one result per line, reps consecutive lines per prompt
//   temp <= 0 -> greedy (as the app does today)
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <string>
#include <vector>
#include <fstream>
#include <sstream>
#include "llama.h"

static std::vector<std::string> readBatch(const char * path) {
    std::vector<std::string> out;
    std::ifstream in(path);
    std::string line, cur;
    const char * SEP = "###PROMPT###";
    while (std::getline(in, line)) {
        if (!line.empty() && line.back() == '\r') line.pop_back();
        if (line == SEP) { if (!cur.empty()) out.push_back(cur); cur.clear(); }
        else { cur += line; cur += '\n'; }
    }
    if (!cur.empty()) out.push_back(cur);
    while (!out.empty() && out.back().find_first_not_of('\n') == std::string::npos) out.pop_back();
    return out;
}

static llama_sampler * makeSampler(const llama_vocab * vocab, float temp, int top_k, float top_p, float repen) {
    if (temp <= 0.0f) return llama_sampler_init_greedy();
    auto cp = llama_sampler_chain_default_params(); cp.no_perf = true;
    llama_sampler * s = llama_sampler_chain_init(cp);
    // Order matters: penalties rewrite logits, then truncation, then temp, then draw.
    llama_sampler_chain_add(s, llama_sampler_init_penalties(llama_vocab_n_tokens(vocab), 64, repen, 0.0f, 0.0f));
    llama_sampler_chain_add(s, llama_sampler_init_top_k(top_k));
    llama_sampler_chain_add(s, llama_sampler_init_top_p(top_p, 1));
    llama_sampler_chain_add(s, llama_sampler_init_temp(temp));
    llama_sampler_chain_add(s, llama_sampler_init_dist(LLAMA_DEFAULT_SEED));
    return s;
}

int main(int argc, char ** argv) {
    if (argc < 3) { fprintf(stderr, "usage: probe -s|-b ...\n"); return 2; }
    std::string mode = argv[1];

    const char * model_path;
    std::vector<std::string> prompts;
    float temp; int top_k; float top_p, repen; int npred, nthread, reps = 1;

    if (mode == "-s") {
        if (argc < 10) { fprintf(stderr, "usage: probe -s model prompt temp topk topp repen npred threads\n"); return 2; }
        model_path = argv[2]; prompts.push_back(argv[3]);
        temp = atof(argv[4]); top_k = atoi(argv[5]); top_p = atof(argv[6]);
        repen = atof(argv[7]); npred = atoi(argv[8]); nthread = atoi(argv[9]);
    } else if (mode == "-b") {
        if (argc < 10) { fprintf(stderr, "usage: probe -b file model temp topk topp repen npred threads [reps]\n"); return 2; }
        prompts = readBatch(argv[2]);
        model_path = argv[3];
        temp = atof(argv[4]); top_k = atoi(argv[5]); top_p = atof(argv[6]);
        repen = atof(argv[7]); npred = atoi(argv[8]); nthread = atoi(argv[9]);
        if (argc > 10) reps = atoi(argv[10]);
    } else { fprintf(stderr, "unknown mode %s\n", mode.c_str()); return 2; }

    llama_backend_init();
    llama_model_params mparams = llama_model_default_params();
    llama_model * model = llama_model_load_from_file(model_path, mparams);
    if (!model) { fprintf(stderr, "load failed\n"); return 1; }

    llama_context_params cparams = llama_context_default_params();
    cparams.n_ctx     = 1024;
    cparams.n_threads = nthread;
    cparams.n_batch   = 512;
    cparams.n_ubatch  = 512;
    llama_context * ctx = llama_init_from_model(model, cparams);
    if (!ctx) { fprintf(stderr, "ctx failed\n"); return 1; }
    const llama_vocab * vocab = llama_model_get_vocab(model);

    for (size_t pi = 0; pi < prompts.size(); pi++) {
        std::vector<llama_chat_message> msgs(1);
        msgs[0] = { "user", prompts[pi].c_str() };
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

        for (int r = 0; r < reps; r++) {
            llama_memory_clear(llama_get_memory(ctx), false);
            if (llama_decode(ctx, llama_batch_get_one(tokens.data(), (int)tokens.size()))) {
                fprintf(stderr, "decode failed\n"); return 1;
            }
            llama_sampler * smpl = makeSampler(vocab, temp, top_k, top_p, repen);
            std::string out;
            for (int i = 0; i < npred; i++) {
                llama_token t = llama_sampler_sample(smpl, ctx, -1);
                if (llama_vocab_is_eog(vocab, t)) break;
                char buf[256];
                int len = llama_token_to_piece(vocab, t, buf, sizeof(buf), 0, false);
                if (len > 0) out.append(buf, len);
                if (llama_decode(ctx, llama_batch_get_one(&t, 1))) break;
            }
            for (size_t k = 0; k < out.size(); k++) if (out[k] == '\n') out[k] = ' ';
            printf("%s\n", out.c_str());
            fflush(stdout);
            llama_sampler_free(smpl);
        }
    }

    llama_free(ctx);
    llama_model_free(model);
    llama_backend_free();
    return 0;
}
