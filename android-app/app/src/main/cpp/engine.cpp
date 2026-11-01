#include "engine.h"
#include "llama.h"
#include <atomic>
#include <chrono>
#include <memory>
#include <mutex>
#include <stdexcept>
#include <thread>
#include <vector>
#include <algorithm>

namespace {
std::atomic<bool> cancelled{false};
std::mutex generation_mutex;
using Clock = std::chrono::steady_clock;
struct Deadline { Clock::time_point end; };
bool abort_run(void * data) {
    return cancelled.load() || Clock::now() > static_cast<Deadline *>(data)->end;
}
bool load_progress(float, void * data) { return !abort_run(data); }
void check(Deadline & d) { if (abort_run(&d)) throw std::runtime_error("AI processing cancelled or timed out"); }
}
void reset_cancel() { cancelled.store(false); }
void cancel_generation() { cancelled.store(true); }
std::string generate(const std::string & path, const std::string & prompt) {
    std::lock_guard<std::mutex> lock(generation_mutex);
    static std::once_flag init;
    std::call_once(init, [] { llama_backend_init(); });
    Deadline deadline{Clock::now() + std::chrono::seconds(45)};
    check(deadline);
    auto mp = llama_model_default_params();
    mp.n_gpu_layers = 0;
    mp.use_mmap = true;
    mp.progress_callback = load_progress;
    mp.progress_callback_user_data = &deadline;
    std::unique_ptr<llama_model, decltype(&llama_model_free)> model(llama_model_load_from_file(path.c_str(), mp), llama_model_free);
    if (!model) throw std::runtime_error("Unable to load AI model");
    check(deadline);
    auto cp = llama_context_default_params();
    cp.n_ctx = 1024; cp.n_batch = 512; cp.n_ubatch = 128;
    cp.n_threads = std::min(4u, std::max(1u, std::thread::hardware_concurrency()));
    cp.n_threads_batch = cp.n_threads;
    cp.abort_callback = abort_run; cp.abort_callback_data = &deadline;
    std::unique_ptr<llama_context, decltype(&llama_free)> ctx(llama_init_from_model(model.get(), cp), llama_free);
    if (!ctx) throw std::runtime_error("Unable to allocate AI context");
    const auto * vocab = llama_model_get_vocab(model.get());
    int n = llama_tokenize(vocab, prompt.data(), prompt.size(), nullptr, 0, false, true);
    if (n >= 0 || -n > 850) throw std::runtime_error("Input is too long");
    std::vector<llama_token> tokens(-n);
    n = llama_tokenize(vocab, prompt.data(), prompt.size(), tokens.data(), tokens.size(), false, true);
    if (n <= 0) throw std::runtime_error("Tokenization failed");
    for (int offset = 0; offset < n; offset += 128) {
        check(deadline);
        auto batch = llama_batch_get_one(tokens.data() + offset, std::min(128, n - offset));
        if (llama_decode(ctx.get(), batch) != 0) throw std::runtime_error("AI prompt processing failed");
    }
    std::unique_ptr<llama_sampler, decltype(&llama_sampler_free)> sampler(llama_sampler_chain_init(llama_sampler_chain_default_params()), llama_sampler_free);
    const char * grammar = R"(root ::= "{" ws "\"text\"" ws ":" ws string ws "}"
string ::= "\"" char{1,80} "\""
char ::= [^"\\\x00-\x1F] | "\\" (["\\/bfnrt] | "u" [0-9a-fA-F]{4})
ws ::= [ \t\n\r]*
)";
    auto * constrained = llama_sampler_init_grammar(vocab, grammar, "root");
    if (!constrained) throw std::runtime_error("Invalid output grammar");
    llama_sampler_chain_add(sampler.get(), constrained);
    llama_sampler_chain_add(sampler.get(), llama_sampler_init_greedy());
    std::string result;
    for (int i = 0; i < 128; ++i) {
        check(deadline);
        llama_token token = llama_sampler_sample(sampler.get(), ctx.get(), -1);
        if (llama_vocab_is_eog(vocab, token)) return result;
        char piece[512];
        int len = llama_token_to_piece(vocab, token, piece, sizeof(piece), 0, false);
        if (len < 0) throw std::runtime_error("Invalid output token");
        result.append(piece, len);
        auto batch = llama_batch_get_one(&token, 1);
        if (llama_decode(ctx.get(), batch) != 0) throw std::runtime_error("AI generation failed");
    }
    throw std::runtime_error("AI output exceeded limit");
}
