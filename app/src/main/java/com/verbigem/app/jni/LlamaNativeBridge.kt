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
    external fun freeModelNative(handle: Long)
}
