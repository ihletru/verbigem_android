cmake_minimum_required(VERSION 3.22.1)

project("verbigem_llamacpp")

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
