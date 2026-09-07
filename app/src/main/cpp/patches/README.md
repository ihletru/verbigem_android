# Patche na vendored llama.cpp

`app/src/main/cpp/llama.cpp/` jest w `.gitignore` (linia 52) i **nie jest śledzone**.
Wszystko, co nałożymy na to drzewo ręcznie, znika po `scripts/fetch_llama.sh`
(który robi `rm -rf` + świeży `git clone`). Dlatego każda zmiana w llama.cpp
**musi** być tu zapisana jako patch, inaczej przestanie istnieć.

## `stq1_0.patch` — kernel STQ1_0 (1.25-bit) + hacki Vulkan

| | |
|---|---|
| **Bazowy commit** | `f5e85d43a048f3d5adefb4c5e29867d8077fba62` (branch `master`, 2026-08-28) |
| **Upstream PR** | [ggml-org/llama.cpp#22836](https://github.com/ggml-org/llama.cpp/pull/22836) — „ggml-cpu : add STQ1_0 ternary quantization with ARM NEON vec_dot kernel" |
| **Rozmiar** | 20 plików, +406 linii |
| **Po co** | Bez tego kernela `Hy-MT2-1.8B-1.25Bit-GGUF` (silnik ⚡ **Szybki**, 440 MB) się nie załaduje. |

### Co dokładnie dodaje

**Część 1 — STQ1_0 (właściwy kernel, CPU-only):**

- nowy typ kwantyzacji `GGML_TYPE_STQ1_0 = 43` (`ggml/include/ggml.h`),
- `block_stq1_0` + codebook (`ggml/src/ggml-common.h`),
- kernel `ggml_vec_dot_stq1_0_q8_K` z ARM NEON (`vdotq_s32` + `vqtbl2q_u8`) w
  `ggml/src/ggml-cpu/arch/arm/quants.c`, wpis do `arch-fallback.h`,
- rejestracja typu w `ggml.c`, `gguf.cpp`, `ggml-quants.c`, `llama.h`,
  `llama-model-loader.cpp`, `llama-quantize.cpp`, `quantize.cpp`,
  `gguf-py/gguf/constants.py`, `test-quantize-fns.cpp`.

**Część 2 — hacki pod Vulkan (nie STQ, ale w tym samym patchu):**

- `ggml/src/ggml-vulkan/CMakeLists.txt` — pozwala, żeby `Vulkan::Vulkan`
  był zdefiniowany przez projekt nadrzędny (zamiast `find_package(... REQUIRED)`),
- `ggml/src/ggml-vulkan/cmake/host-toolchain.cmake.in` — propaguje
  `CMAKE_MAKE_PROGRAM` do sub-bulidu `ExternalProject` (NDK cmake tego nie robi).

Bez części 2 `GGML_VULKAN=ON` nie zbuduje się na tej maszynie bez Vulkan SDK.

### Ograniczenia

- **`STQ1_0` to kernel CPU-only.** `gpuLayers` jest przy nim wymuszane na 0 —
  kwant 1.25-bit nie ma ścieżki GPU. Dotyczy to też ewentualnego 7B w 1.25-bit.
- Patch jest **niezależny od brancha `STQ_0`** z PR-a — to wersja nałożona ręcznie
  na `master`. Numeracja typów GGML przesuwa się w upstreamie, więc po zmianie
  bazowego commitu patch może wymagać rebase'u (`GGML_TYPE_COUNT`).

### Jak nałożyć ręcznie

```bash
cd app/src/main/cpp/llama.cpp
git apply -p1 ../../patches/stq1_0.patch      # jeśli .git jest obecny
# albo, gdy katalog nie jest repozytorium (fetch_llama.sh usuwa .git):
patch -p1 --forward < ../../patches/stq1_0.patch
```

`scripts/fetch_llama.sh` robi to automatycznie (krok „STQ1_0 patch").

### Jak odtworzyć po update llama.cpp

1. `cd app/src/main/cpp/llama.cpp && git checkout .` (cofną stary patch)
2. `git fetch && git checkout <nowy-commit>`
3. `git apply -p1 ../../patches/stq1_0.patch` — jeśli się sypie, popraw ręcznie
   konflikt wokół `GGML_TYPE_COUNT` / `GGML_TYPE_STQ1_0`,
4. `git diff > ../../patches/stq1_0.patch` (**nadpisz patch**),
5. zaktualizuj „Bazowy commit" w tym pliku,
6. zbuduj i sprawdź logcat: silnik Szybki musi się załadować.
