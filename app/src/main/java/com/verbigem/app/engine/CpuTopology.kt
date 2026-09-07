package com.verbigem.app.engine

import android.util.Log

/**
 * How many threads to hand to llama.cpp.
 *
 * ⚠️ This file used to recommend "number of big cores only". That rule was
 * plausible, wrong, and has been replaced by measurement. Do not restore it.
 *
 * ---------------------------------------------------------------------------
 * MEASURED on Redmi Note 13 (Snapdragon 685: 4x A73 @2.8 GHz + 4x A53 @1.9 GHz,
 * Android 15), llama-bench, `-p 64 -n 64 -r 3`, CPU-only build, same pinned
 * llama.cpp commit the app ships (f5e85d4):
 *
 *   1.8B @ 1.25-bit (436 MB)         pp512     tg64
 *     t=4                            10.34     7.99
 *     t=6                            11.91     7.75
 *     t=8                            13.13     7.17
 *
 *   7B @ UD-Q2_K_XL (2.91 GB)        pp512     tg64
 *     t=4                             1.77     1.58
 *     t=6                             2.12     1.62
 *     t=8                             2.32     1.64
 *
 * Reading of those numbers:
 *
 * 1. **Prompt processing always wants more threads** (+27% from 4->8 on the
 *    1.8B, +31% on the 7B). Barriers cost nothing here because every core has
 *    64 tokens of real work to chew on.
 *
 * 2. **Decode is nearly flat, and only the small model regresses** (-10% from
 *    4->8 on the 1.8B; the 7B actually gains +4%). Decode is bandwidth-bound,
 *    so the barrier theory only bites when the per-thread slice is tiny.
 *
 * 3. Net effect for a real translation — and this is the part that killed the
 *    big-core rule — is a wash or slightly *favouring* more threads, because
 *    translations in this app are **prompt-heavy**: chat template + source text
 *    (30-100 tokens) in, a similar number out. For a typical 30-in/10-out
 *    phrase on the 1.8B: t=4 -> 4.15 s, t=6 -> 3.81 s, t=8 -> 3.68 s.
 *    Restricting to big cores was an ~11% regression on the most common case.
 *
 * So: use every core, capped. The cap exists purely for thermals — past 8 there
 * is nothing left to gain on any phone we support, and a long translation on a
 * passively cooled slab will throttle.
 * ---------------------------------------------------------------------------
 */
internal object CpuTopology {

    private const val TAG = "CpuTopology"

    private const val MIN_THREADS = 4
    private const val MAX_THREADS = 8

    /**
     * Thread count for inference. Cached — this sits on the model-load path.
     */
    @Volatile
    private var cached: Int? = null

    fun inferenceThreads(): Int {
        cached?.let { return it }
        val available = Runtime.getRuntime().availableProcessors()
        val threads = available.coerceIn(MIN_THREADS, MAX_THREADS)
        cached = threads
        Log.i(TAG, "inferenceThreads=$threads (availableProcessors=$available)")
        return threads
    }
}
