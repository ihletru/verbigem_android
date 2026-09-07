#!/usr/bin/env bash
# Fetch llama.cpp sources and apply Android NDK 26 (Clang 17) fp16 patch.
# llama.cpp is NOT committed to the repo (vendored dependency).
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
LLAMA_DIR="$ROOT/app/src/main/cpp/llama.cpp"
SRC_FILE="$LLAMA_DIR/ggml/src/ggml-cpu/llamafile/sgemm.cpp"

LLAMA_REPO="${LLAMA_REPO:-https://github.com/ggml-org/llama.cpp.git}"
# PINNED, not "master": patches/stq1_0.patch is generated against this exact
# commit. Upstream moves GGML_TYPE_* numbering, so a bare "master" clone will
# silently fail to apply the STQ kernel -> the 1.25-bit "Szybki" engine dies.
# To upgrade: apply the patch by hand, resolve, re-export it, bump this pin.
LLAMA_REF="${LLAMA_REF:-f5e85d43a048f3d5adefb4c5e29867d8077fba62}"
STQ_PATCH="$ROOT/app/src/main/cpp/patches/stq1_0.patch"

echo "==> Cloning llama.cpp ($LLAMA_REF) into $LLAMA_DIR"
rm -rf "$LLAMA_DIR"
git clone "$LLAMA_REPO" "$LLAMA_DIR"
git -C "$LLAMA_DIR" checkout "$LLAMA_REF"

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

# ── STQ1_0 kernel (1.25-bit "Szybki" engine) ───────────────────────────────
# llama.cpp/ is gitignored, so this patch is the ONLY copy of the kernel that
# survives a clean checkout. Without it Hy-MT2-1.8B-1.25Bit-GGUF won't load.
# See app/src/main/cpp/patches/README.md for base commit + how to re-export.
if [ ! -f "$STQ_PATCH" ]; then
    echo "ERROR: missing $STQ_PATCH" >&2
    exit 1
fi

if grep -q "GGML_TYPE_STQ1_0" "$LLAMA_DIR/ggml/include/ggml.h"; then
    echo "==> STQ1_0 patch already applied - skipping"
else
    echo "==> Applying STQ1_0 patch (1.25-bit kernel + Vulkan build hacks)"
    if git -C "$LLAMA_DIR" apply -p1 --check "$STQ_PATCH" 2>/dev/null; then
        git -C "$LLAMA_DIR" apply -p1 "$STQ_PATCH"
    elif command -v patch >/dev/null 2>&1; then
        ( cd "$LLAMA_DIR" && patch -p1 --forward < "$STQ_PATCH" )
    else
        echo "ERROR: cannot apply STQ patch (no git apply, no patch)" >&2
        exit 1
    fi
    grep -q "GGML_TYPE_STQ1_0" "$LLAMA_DIR/ggml/include/ggml.h" \
        || { echo "ERROR: STQ1_0 patch did not take effect" >&2; exit 1; }
fi

# Remove nested git only now - the patch step above needs git apply to work.
rm -rf "$LLAMA_DIR/.git"

echo "==> Done. llama.cpp is ready."
