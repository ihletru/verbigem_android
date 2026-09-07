package com.verbigem.app.data.model

/**
 * Which GGUF weights the local Hy-MT2 engine should load.
 *
 * Replaces the old `isAccurate: Boolean` switch, which could only ever express
 * two models. The boolean API is kept as a delegating overload so existing
 * call sites keep working unchanged.
 */
enum class ModelTier(
    val id: String,
    val fileName: String,
    /** Approximate size in bytes. Used only for the pre-download storage check. */
    val approxBytes: Long,
    /**
     * Minimum device RAM required to load this tier at all. Below this the
     * process gets killed by the low-memory killer mid-load, which surfaces to
     * the user as a silent crash — so gate the engine before offering it.
     */
    val minRamBytes: Long,
    /**
     * When true this tier is only offered on devices with a working GGML GPU
     * backend ([GpuAcceleration]). See PRO_7B for why — it is a measured
     * decision, not a guess.
     */
    val requiresGpu: Boolean = false
) {
    /** 1.8B @ 1.25-bit (STQ1_0 kernel). Fastest; CPU-only by design. */
    FAST(
        id = "fast",
        fileName = "Hy-MT2-1.8B-1.25Bit.gguf",
        approxBytes = 440L * 1024 * 1024,
        minRamBytes = 2L * 1024 * 1024 * 1024
    ),

    /** 1.8B @ Q4_K_M. Slower but noticeably more accurate. */
    ACCURATE(
        id = "accurate",
        fileName = "Hy-MT2-1.8B-Q4_K_M.gguf",
        approxBytes = 1_100L * 1024 * 1024,
        minRamBytes = 3L * 1024 * 1024 * 1024
    ),

    /**
     * 7.5B @ UD-Q2_K_XL — the "bigger engine" for Pro.
     *
     * CPU decode is bandwidth-bound: time/token scales with the number of bytes
     * that have to be streamed per token, so the size ladder below IS the speed
     * ladder. Verified sizes (HuggingFace, 2026-09):
     *
     *   unsloth/Hy-MT2-7B-GGUF
     *     UD-IQ2_M      2.63 GB      UD-Q2_K_XL   2.91 GB   <-- chosen
     *     UD-IQ3_XXS    2.91 GB      UD-Q3_K_XL   3.69 GB
     *     IQ4_XS        3.88 GB      Q4_K_M       4.31 GB
     *   mradermacher/Hy-MT2-7B-i1-GGUF
     *     IQ1_S         1.72 GB      IQ2_XXS      2.07 GB
     *     Q2_K          2.80 GB      IQ3_XXS      2.84 GB
     *     Q3_K_S        3.20 GB      IQ4_XS       3.88 GB
     *   tencent/Hy-MT2-7B-GGUF
     *     Q4_K_M        4.62 GB      Q8_0         7.98 GB
     *
     * Why UD-Q2_K_XL and not Q4_K_M: 4.31-4.62 GB is ~4x the size of [ACCURATE],
     * i.e. ~4x slower per token, which is unusable on CPU and a 4.6 GB download
     * over mobile data. 2.91 GB is ~2.6x — slow, but survivable, and it already
     * buys the capacity jump from 1.8B to 7B parameters.
     *
     * Why a K-quant and not an i-quant of the same size: i-quants (IQ*) need
     * extra bit-fiddling at dequant time, so they are markedly slower per token
     * than K-quants on a CPU-only build, which is what we ship today.
     *
     * ⚠️ MEASURED, and the measurement changed the plan. On a CPU-only build
     * this tier decodes at **1.6 tok/s** on a Snapdragon 685 (Redmi Note 13),
     * i.e. ~37 s for a 60-token sentence. That is not shippable. See
     * docs/SILNIK_PRO_RESEARCH.md for the full table.
     *
     * Speculative decoding does not rescue it: that trick only pays when the
     * verifier is compute-bound and can verify N drafted tokens for roughly the
     * price of 1. This workload is bandwidth-bound — batched prompt processing
     * is only ~1.4x faster per token than single-token decode — so verification
     * costs nearly as much as plain generation.
     *
     * Therefore [requiresGpu] is ON: the engine appears by itself on devices
     * that can actually run it, and stays hidden on CPU-only phones instead of
     * selling someone a 2.9 GB download that answers at 1.6 tok/s.
     *
     * Swapping the quant is a two-line change here + [ModelDownloader.URL_HYMT2_PRO_7B].
     */
    PRO_7B(
        id = "pro7b",
        fileName = "Hy-MT2-7B-UD-Q2_K_XL.gguf",
        requiresGpu = true,
        // Exact byte size of unsloth/Hy-MT2-7B-GGUF @ UD-Q2_K_XL, verified with a
        // HEAD request (Content-Length: 3123810080). Keep in sync with
        // ModelDownloader.URL_HYMT2_PRO_7B.
        approxBytes = 3_123_810_080L,
        // `totalMem` reports *usable* physical RAM, which is always below the
        // marketing number — a "6 GB" phone typically reports ~5.6 GB. Gating at
        // 6 GB would therefore block exactly the 6 GB devices that can still run
        // this. 5 GB cleanly separates 6 GB phones (pass) from 4 GB ones (fail).
        minRamBytes = 5L * 1024 * 1024 * 1024
    );

    /** Przybliżony rozmiar do pokazania w UI, np. `~2.9 GB`. */
    val sizeLabel: String
        get() = "~${"%.1f".format(approxBytes / 1024.0 / 1024.0 / 1024.0)} GB"

    companion object {
        fun fromId(id: String): ModelTier = entries.find { it.id == id } ?: FAST

        /** Bridge for the legacy boolean API. */
        fun fromAccurate(isAccurate: Boolean): ModelTier =
            if (isAccurate) ACCURATE else FAST
    }
}
