cmake_minimum_required(VERSION 3.22.1)

project("verbigem_llamacpp")

# Disable FP16 in ggml-cpu to avoid vld1q_f16 errors on older NDK Clang (17.x).
# GGML will fall back to FP32 (slightly slower, fully compatible).
add_compile_definitions(GGML_NO_FP16)

# Disable host-native CPU optimisations – cross-compiling for Android ARM64.
set(GGML_NATIVE OFF CACHE BOOL "" FORCE)

# ── GPU backend ────────────────────────────────────────────────────────────
# Vulkan requires a native Windows compiler (MSVC/LLVM) to build the
# vulkan-shaders-gen host tool. That compiler is not available on this
# machine without Vulkan SDK + Build Tools installed.
# Re-enable by setting GGML_VULKAN=ON after:
#   1. winget install KhronosGroup.VulkanSDK  (and accept UAC prompt)
#   2. winget install Microsoft.VisualStudio.2022.BuildTools
set(GGML_VULKAN OFF CACHE BOOL "" FORCE)
# ───────────────────────────────────────────────────────────────────────────

# llama.cpp as a subdirectory (provides llama library)
add_subdirectory(llama.cpp)

add_library(
    verbigem_llama
    SHARED
    llama_jni.cpp
)

find_library(
    log-lib
    log
)

target_include_directories(
    verbigem_llama
    PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}
    ${CMAKE_CURRENT_SOURCE_DIR}/llama.cpp/include
)

target_link_libraries(
    verbigem_llama
    llama
    ${log-lib}
    android
)

# 16 KB page size alignment — required for Android 15+ devices
target_link_options(
    verbigem_llama
    PRIVATE
    "-Wl,-z,max-page-size=16384"
)
