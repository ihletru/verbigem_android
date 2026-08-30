package com.verbigem.app.jni

import android.util.Log

object LlamaNativeBridge {
    private const val TAG = "LlamaNativeBridge"
    private var isLibraryLoaded = false

    init {
        try {
            System.loadLibrary("verbigem_llama")
            isLibraryLoaded = true
            Log.i(TAG, "Native library libverbigem_llama.so loaded successfully")
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "Failed to load native library libverbigem_llama.so", e)
            isLibraryLoaded = false
        }
    }

    fun isLoaded(): Boolean = isLibraryLoaded

    external fun isNativeSupported(): Boolean
    external fun loadModelNative(modelPath: String, nThreads: Int, nGpuLayers: Int): Long
    external fun generateNative(handle: Long, prompt: String, maxTokens: Int): String

    // Streaming generation: invokes TokenStreamCallback.onToken(String) after each decoded
    // piece so the UI can show partial translation live instead of blocking until the whole
    // output is ready.
    //
    // IMPORTANT: pass a class instance (object : TokenStreamCallback), NOT a Kotlin lambda.
    // Kotlin inlines lambdas into synthetic classes that do not implement Function1, so the
    // JNI GetMethodID("invoke", ...) would fail with NoSuchMethodError. A named interface
    // class is never inlined and always exposes onToken(Ljava/lang/String;)V.
    interface TokenStreamCallback {
        fun onToken(text: String)
    }

    external fun generateNativeStreaming(handle: Long, prompt: String, maxTokens: Int, callback: TokenStreamCallback)

    external fun freeModelNative(handle: Long)
}
