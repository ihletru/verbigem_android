#!/usr/bin/env bash
# Fetch llama.cpp sources and apply Android NDK 26 (Clang 17) fp16 patch.
# llama.cpp is NOT committed to the repo (vendored dependency).
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
LLAMA_DIR="$ROOT/app/src/main/cpp/llama.cpp"
SRC_FILE="$LLAMA_DIR/ggml/src/ggml-cpu/llamafile/sgemm.cpp"

LLAMA_REPO="${LLAMA_REPO:-https://github.com/ggml-org/llama.cpp.git}"
LLAMA_REF="${LLAMA_REF:-master}"

echo "==> Cloning llama.cpp ($LLAMA_REF) into $LLAMA_DIR"
rm -rf "$LLAMA_DIR"
git clone --depth 1 --branch "$LLAMA_REF" "$LLAMA_REPO" "$LLAMA_DIR"
# Remove nested git so it stays a plain vendored dir inside our repo.
rm -rf "$LLAMA_DIR/.git"

echo "==> Patching sgemm.cpp for NDK 26 / Clang 17 (fp16 vector arithmetic)"
# Older NDK Clang does not define __ARM_FEATURE_FP16_VECTOR_ARITHMETIC, so the
# vld1q_f16 / vld1_f16 intrinsics are undeclared. Wrap them and use a scalar
# fp16->fp32 fallback instead.
python3 - "$SRC_FILE" <<'PY'
import sys, re
p = sys.argv[1]
s = open(p, encoding='utf-8').read()
old = '''#if !defined(_MSC_VER)
// FIXME: this should check for __ARM_FEATURE_FP16_VECTOR_ARITHMETIC
template <> inline float16x8_t load(const ggml_fp16_t *p) {
    return vld1q_f16((const float16_t *)p);
}
template <> inline float32x4_t load(const ggml_fp16_t *p) {
    return vcvt_f32_f16(vld1_f16((const float16_t *)p));
}
#endif // _MSC_VER'''
new = '''#if !defined(_MSC_VER)
#if defined(__ARM_FEATURE_FP16_VECTOR_ARITHMETIC)
template <> inline float16x8_t load(const ggml_fp16_t *p) {
    return vld1q_f16((const float16_t *)p);
}
#endif // __ARM_FEATURE_FP16_VECTOR_ARITHMETIC
template <> inline float32x4_t load(const ggml_fp16_t *p) {
    float32x4_t v;
    v[0] = GGML_CPU_FP16_TO_FP32(p[0]);
    v[1] = GGML_CPU_FP16_TO_FP32(p[1]);
    v[2] = GGML_CPU_FP16_TO_FP32(p[2]);
    v[3] = GGML_CPU_FP16_TO_FP32(p[3]);
    return v;
}
#endif // _MSC_VER'''
if old not in s:
    print("PATCH ALREADY APPLIED or pattern changed - skipping")
else:
    s = s.replace(old, new)
    open(p, 'w', encoding='utf-8').write(s)
    print("PATCH APPLIED")
PY

echo "==> Done. llama.cpp is ready."
