# Research: większy silnik lokalny dla wersji Pro (stan na 2026-09-07)

Dokument decyzyjny. **Warstwa danych (ModelTier / ModelDownloader / HyMt2NativeEngine)
jest już wdrożona i kompiluje się** — patrz §7.

---

## 1. Co Tencent naprawdę ma (i co już mamy)

Hipoteza: „Tencent opracował własną metodę akceleracji modelu na mobile".
Weryfikacja: Tencent ma **cztery** rzeczy, z których tylko jedna dotyczy mobile.

| Projekt | Czym jest | Zastosowanie u nas |
|---|---|---|
| **AngelSlim** (`Tencent/AngelSlim`) | toolkit kompresji: FP8 / INT4 / **2-bit / 1.25-bit** / trójstanowy, plus STQ kernel | **JUŻ UŻYWAMY** — `Hy-MT2-1.8B-1.25Bit-GGUF` (silnik ⚡ Szybki, 440 MB) |
| **STQ1_0 kernel** (llama.cpp PR #22836) | kernel ARM NEON dla kwantyzacji 1.25-bit | **JUŻ MAMY** — patch zapisany w `app/src/main/cpp/patches/stq1_0.patch` |
| **AngelSpec** (submoduł AngelSlim) | **speculative decoding**: DFly, Eagle3, DFlare — 2.40× średnio, do 5.52× | **NIE** — do wdrożenia (Faza 3) |
| **ncnn / TACO-LLM** | framework inferencji mobile (CPU+Vulkan) / silnik chmurowy | ncnn nie ma dojrzałego runtime'u LLM; TACO jest serwerowy |

**Wniosek 1:** „metoda Tencenta, która jest dużo szybsza" = **AngelSlim 1.25-bit + STQ kernel**.
To dokładnie to, co już mamy wbudowane jako silnik Szybki. Nie ma drugiego, nowszego,
tajnego runtime'u — Tencent sam odsyła do llama.cpp ze swoim kernelem STQ.

**Wniosek 2:** Tencent **nie opublikował** 7B ani 30B w 1.25-bit / 2-bit. Sprawdzono
wszystkie 112 repozytoriów organizacji `AngelSlim` na HuggingFace — kompresja poniżej
4 bitów istnieje tylko dla 1.8B. `AngelSlim/Hy-MT2-30B-A3B-GGUF` ma wyłącznie
Q4_K_M (16.98 GB) i Q8_0.

**Wniosek 3:** prawdziwą, nieużywaną dźwignią Tencenta jest **speculative decoding**
(AngelSpec). Do wzięcia w Fazie 3, bez wymiany runtime'u.

---

## 2. Dlaczego rozmiar wag to jedyna liczba, która się liczy

Decode na CPU jest **ograniczony przepustowością pamięci**, nie mocą obliczeniową.
Czas na token ≈ rozmiar wag / przepustowość RAM. Mniejszy plik = proporcjonalnie szybciej.

To oznacza, że **drabinka rozmiarów GGUF jest jednocześnie drabinką prędkości.**
Dlatego wybór kwantyzacji to de facto wybór „jak wolny ma być Pro".

---

## 3. Pełna drabinka 7B (zweryfikowane HEAD-em na HuggingFace, 2026-09-07)

| Źródło | Kwantyzacja | Rozmiar | × vs Dokładny (1.10 GB) |
|---|---|---|---|
| `unsloth/Hy-MT2-7B-GGUF` | UD-IQ2_M | 2.63 GB | 2.4× |
| **`unsloth/Hy-MT2-7B-GGUF`** | **UD-Q2_K_XL** | **2.91 GB** | **2.6× ← wybrane** |
| `unsloth/Hy-MT2-7B-GGUF` | UD-IQ3_XXS | 2.91 GB | 2.6× |
| `unsloth/Hy-MT2-7B-GGUF` | UD-Q3_K_XL | 3.69 GB | 3.4× |
| `unsloth/Hy-MT2-7B-GGUF` | IQ4_XS | 3.88 GB | 3.5× |
| `unsloth/Hy-MT2-7B-GGUF` | Q4_K_M | 4.31 GB | 3.9× |
| `mradermacher/Hy-MT2-7B-i1-GGUF` | IQ1_S | 1.72 GB | 1.6× |
| `mradermacher/Hy-MT2-7B-i1-GGUF` | IQ2_XXS | 2.07 GB | 1.9× |
| `mradermacher/Hy-MT2-7B-i1-GGUF` | Q2_K | 2.80 GB | 2.5× |
| `mradermacher/Hy-MT2-7B-i1-GGUF` | IQ3_XXS | 2.84 GB | 2.6× |
| `mradermacher/Hy-MT2-7B-i1-GGUF` | Q3_K_S | 3.20 GB | 2.9× |
| `mradermacher/Hy-MT2-7B-i1-GGUF` | IQ4_XS | 3.88 GB | 3.5× |
| `tencent/Hy-MT2-7B-GGUF` | Q4_K_M | 4.62 GB | 4.2× |
| `tencent/Hy-MT2-7B-GGUF` | Q8_0 | 7.98 GB | 7.3× |

**30B-A3B — odrzucony definitywnie.** MoE ma 3B aktywnych parametrów (więc decode
kosztuje tyle co 3B), ale plik i tak trzeba mieć cały na dysku: Q4_K_M = **16.98 GB**.
Niskobitowej wersji nie ma i nikt jej nie opublikował.

**Wybór: `unsloth/Hy-MT2-7B-GGUF` → `Hy-MT2-7B-UD-Q2_K_XL.gguf` (2.91 GB).**

- unsloth jest najbezpieczniejszym źródłem (6.7 tys. pobrań; mradermacher i1 — 661).
- UD = quant dynamiczny, ważne warstwy dostają wyższą precyzję.
- **K-quant, nie i-quant**: kwantyzacje `IQ*` wymagają dodatkowej manipulacji bitami
  przy dekwantyzacji i są wyraźnie wolniejsze na token przy tym samym rozmiarze.
  Na buildzie czysto CPU-owym (a taki dziś mamy) to strata.
- 4.31–4.62 GB (Q4_K_M) odpada: 4× wolniej niż Dokładny i 4.6 GB pobierania.

Zmiana wariantu = jedna linia w `ModelTier.PRO_7B` + `ModelDownloader.URL_HYMT2_PRO_7B`.

---

## 4. Urządzenie testowe: Redmi Note 13 → Snapdragon 685

To zmienia plan, bo **odpowiedź brzmi inaczej niż dla flagowca**.

| Cecha | Snapdragon 685 (SM6225-AD) | Co z tego wynika |
|---|---|---|
| CPU | 4× Cortex-A73 @2.8 GHz + 4× Cortex-A53 @1.9 GHz | A73 to rdzeń z 2016 r. **ARMv8.0** — brak `dotprod`, `i8mm`, `fp16` |
| GPU | **Adreno 610** | A6xx, ~0.25–0.3 TFLOPS. **Nie zweryfikowane w ggml-opencl** |
| RAM | LPDDR4x, 17.06 GB/s (teoretycznie) | ~10–12 GB/s realnie dla dużych rdzeni |
| NPU | brak sensownego | Hexagon 686 — nie dla LLM |

Trzy twarde konsekwencje:

**(a) Ścieżka GPU jest na tym telefonie martwa.** llama.cpp `ggml-opencl` jest
zweryfikowany na Adreno 750 / 810 / 830 / 840 (Snapdragon 8 Gen 3 i nowsze). Adreno
610 to A6xx — słabszy o rząd wielkości i z niepewnym sterownikiem OpenCL. Nawet gdyby
zadziałał, ~0.3 TFLOPS z dużym narzutem na dispatch kerneli nie pobiłoby CPU.
→ **Nie da się na tym telefonie przetestować Fazy GPU. I nie warto jej tu robić.**

**(b) KleidiAI nie pomoże.** Włączone `-DGGML_CPU_KLEIDIAI=ON` daje szybkie ścieżki
`i8mm` / `dotprod`, ale A73 nie ma żadnego z tych rozszerzeń → llama.cpp i tak spada
na generyczny NEON. `GGML_NO_FP16` w naszym CMakeLists jest tu bez znaczenia (A73 nie
ma fp16 tak czy owak).

**(c) Małe rdzenie psują równoległość.** llama.cpp robi barierę po każdej warstwie, więc
każdy wątek czeka na najwolniejszy. Wątki na czterech A53 (3–4× wolniejszych) **zwalniają**
dekodowanie zamiast je przyspieszać. Do 2026-09-07 braliśmy `availableProcessors() - 2`
= 6 wątków, czyli dokładnie zły wynik dla big.LITTLE.

**(c) jest w połowie błędne** — patrz §5, zmierzyliśmy to i wyszło odwrotnie, niż
zakładała teoria o barierach. Zostawiam (c) powyżej jako zapis, *dlaczego* tak
myśleliśmy, żeby nikt do tego nie wracał.

Szacunki z poprzedniej wersji tego dokumentu („~10–18 tok/s dla Szybkiego") były
zbyt optymistyczne. Prawdziwe liczby są w §5.

---

## 5. POMIARY (llama-bench na prawdziwym telefonie)

Metoda: cross-build `llama-bench` z **tego samego commitu llama.cpp, który
siedzi w aplikacji** (`f5e85d4`, ggml 0.22.0), NDK 27, Release, `GGML_NO_FP16`,
`-ngl 0` (czyste CPU), wypchnięte na Redmi Note 13 kablem. Komenda:

```
./llama-bench -m <model> -t 4,6,8 -ngl 0 -p 64 -n 64 -r 3 -o csv
```

`pp` = przetwarzanie promptu (wejście), `tg` = generowanie (wyjście). tok/s.

### 1.8B @ 1.25-bit — ⚡ Szybki (436 MB)

| Wątki | pp64 | tg64 |
|---|---|---|
| 4 | 10.34 | **7.99** |
| 6 | 11.91 | 7.75 |
| 8 | **13.13** | 7.17 |

### 1.8B @ Q4_K_M — 🎯 Dokładny (1.13 GB)

| Wątki | pp64 | tg64 |
|---|---|---|
| 4 | 10.21 | **6.93** |
| 6 | 11.83 | 6.43 |
| 8 | **13.10** | 5.76 |

### 7B @ UD-Q2_K_XL — 🧠 Pro (2.91 GB)

| Wątki | pp64 | tg64 |
|---|---|---|
| 4 | 1.77 | 1.58 |
| 6 | 2.12 | 1.62 |
| 8 | **2.32** | **1.64** |

### Kontrola: KV cache q8_0 zamiast f16 (1.25-bit, t=8)

| | pp64 | tg64 |
|---|---|---|
| f16 | 13.13 | **7.17** |
| q8_0 | 13.03 | 6.60 |

→ q8_0 jest **wolniejszy** (narzut na dekwantyzację przewyższa oszczędność
przepustowości przy krótkim kontekście). **Zostajemy przy f16.** Nie wracać
do tego pomysłu.

---

## 6. Co z pomiarów wynika

**(a) pp rośnie z wątkami zawsze, tg — zależy od rozmiaru modelu.**

- pp: 4→8 wątków daje **+27%** na 1.8B i **+31%** na 7B.
- tg na małym modelu *spada* (1.25-bit: 7.99 → 7.17, −10%), na dużym *rośnie*
  (7B: 1.58 → 1.64, +4%).

To obsadza teorię barier z §4(c): bariera kosztuje tyle samo niezależnie od
modelu, ale **porcja pracy na wątek rośnie z rozmiarem modelu**. Przy 1.8B
porcja jest za mała, żeby zamortyzować synchronizację; przy 7B już jest.

**(b) Tłumaczenie w tej aplikacji jest „prompt-ciężkie".**

Szablon czatu + tekst źródłowy to zwykle 30–100 tokenów wejścia, wyjście
podobnej długości. Policzony czas całkowity (t=8):

| | 30 in / 10 out (zdanie) | 40 in / 60 out (akapit) |
|---|---|---|
| ⚡ Szybki 1.25-bit | **3.7 s** | 11.4 s |
| 🎯 Dokładny Q4_K_M | 4.0 s | 13.5 s |
| 🧠 Pro 7B | **19.0 s** | **53.8 s** |

Dla porównania przy 4 wątkach zdanie na Szybkim wychodzi 4.15 s — czyli
ograniczenie się do dużych rdzeni było **~11% regresją** na najczęstszym
przypadku. Poprawione (`CpuTopology.inferenceThreads()` = wszystkie rdzenie,
z kapą 8).

**(c) 7B na CPU jest nie do przyjęcia. Kropka.**

1.64 tok/s → 60-tokenowe zdanie w ~37 s, akapit w ~54 s. To nie jest
„najwyższa jakość, wolno", to jest „aplikacja się zawiesiła". **Nie wypuszczamy
tego na telefony bez GPU.**

**(d) Mniejszy quant nie ratuje.**

`mradermacher` ma 7B IQ1_S @ 1.72 GB. Skalowanie liniowe dałoby ~2.9 tok/s, ale
i-quanty dokładają pracy przy dekwantyzacji, więc realnie ~2.3–2.5 tok/s.
Zdanie w ~24 s. Nadal nie.

**(e) Dekodowanie spekulatywne (AngelSpec) też nie ratuje — i to jest ważne.**

Spekulacja opłaca się wtedy, gdy weryfikator jest *compute-bound* i sprawdzenie
N tokenów w jednym batchu kosztuje tyle co wygenerowanie jednego. Tu jesteśmy
*bandwidth-bound*: batchowe pp jest tylko **1.4×** szybsze na token niż
pojedynczy krok dekodowania. Weryfikacja 5 tokenów kosztuje ~5×0.43 s,
podczas gdy 5 kroków dekodowania to 5×0.61 s — oszczędność ~30%, z której
trzeba jeszcze odjąć ~0.5 s na sam draft. Zysk rzędu kilkunastu procent, i to
przy optymistycznym założeniu 100% akceptacji.

**Wniosek: AngelSpec to technologia pod GPU, nie pod CPU mid-range.** Tam, gdzie
7B i tak leci na GPU, spekulacja daje 2–3×. Tu nie daje nic.

---

## 7. Faza GPU — teraz już nie „kiedyś", tylko warunek dla Pro

| Backend | Wymagania na tej maszynie | Stan |
|---|---|---|
| CPU (NEON) | — | **działa** |
| Adreno OpenCL | nagłówki `OpenCL-Headers` + `libOpenCL.so` arm64 | możliwe do zbudowania, **nieprzydatne na Adreno 610** |
| Adreno Vulkan | MSVC / Vulkan SDK do `vulkan-shaders-gen` | zablokowane (brak SDK i MSVC) |
| Hexagon NPU | Hexagon SDK + Docker | zablokowane |

### 7b. Zbudowaliśmy OpenCL i zmierzyliśmy go na Adreno 610

Nie skończyło się na teorii. `ggml-opencl` **skompilował się i uruchomił** —
wymagał tylko `OpenCL-Headers` (GitHub) i `libOpenCL.so` wyciągniętej z telefonu
jako stub do linkowania (`find_package(OpenCL)` nie zadziałałby z pudełka).
Wyniki:

**1. Domyślna konfiguracja twardo zabija proces.**

```
ggml_opencl: device: 'QUALCOMM Adreno(TM) (OpenCL 2.0 Adreno(TM) 610)'
clGetDeviceInfo(..., CL_DEVICE_OPENCL_C_ALL_VERSIONS, ...) error -30
ggml-opencl.cpp:212: GGML_ASSERT(0) failed   →   Aborted
```

Platforma Qualcomma raportuje **OpenCL 3.0**, urządzenie jest **OpenCL 2.0**.
ggml pyta o `CL_DEVICE_OPENCL_C_ALL_VERSIONS`, które istnieje dopiero w CL 3.0,
dostaje `-30` (CL_INVALID_VALUE) i **asertuje zamiast obsłużyć błąd**.

⚠️ **To jest pułapka na aplikację publiczną.** Taki mismatch (platform 3.0 +
device 2.0) jest typowy dla Snapdragonów z Adreno 6xx/7xx. Gdybyśmy po prostu
włączyli OpenCL, aplikacja **crashowałaby na starcie na milionach telefonów** —
zanim jakikolwiek kod Kotlin zdążyłby cokolwiek sprawdzić, bo backend
rejestruje się w natywnym `ggml_backend_registry`.

**2. Po obniżeniu do `GGML_OPENCL_TARGET_VERSION=200` backend wstaje...**

```
ggml_opencl: device: 'QUALCOMM Adreno(TM) (OpenCL 2.0 Adreno(TM) 610)'
Available devices:
  GPUOpenCL: QUALCOMM Adreno(TM) (3800 MiB, 2776 MiB free)
```

**3. ...ale jest 4× wolniejszy od CPU, a przy pełnym offloadzie segfaultuje.**

| | pp32 | tg8 |
|---|---|---|
| CPU (`-ngl 0`) | 7.01 | **5.17** |
| 1 warstwa na GPU (`-ngl 1`) | 5.94 | **1.22** |
| wszystko na GPU (`-ngl 99`) | — | **Segmentation fault** |

Jedna warstwa na GPU i decode leci z 5.17 do 1.22 tok/s. Transfer CPU↔GPU na
każdą warstwę kosztuje więcej niż samo policzenie jej na CPU. Przy `-ngl 99`
proces umiera (model 2.91 GB nie mieści się w 2776 MiB wolnego na GPU, a
ggml nie radzi sobie z tym łagodnie).

**Wniosek: Adreno 610 + OpenCL = szkodliwe.** Nie „powolne" — szkodliwe.
I co ważniejsze: **sama próba `dlopen("libOpenCL.so")` nic nie mówi** — na tym
telefonie ładuje się bezbłędnie.

Dlatego `GpuAcceleration.supportsOpenCL()` ma **białą listę SoC**
(`BuildConfig.GGML_OPENCL_ALLOWED_SOCS`, domyślnie pusta). Nieznany sprzęt →
CPU. To jedyna bezpieczna postawa przy aplikacji publicznej, gdy nie mamy
urządzenia, na którym moglibyśmy GPU zweryfikować.

**To jest decyzja projektowa, nie tymczasowe odłożenie:** 🧠 Pro 7B ma
`requiresGpu = true`. Silnik pojawia się sam, na urządzeniach, które naprawdę
go udźwigną, i znika na pozostałych. Dokładnie tak, jak chciałeś — wykrywanie
w runtime, nie hardcodowanie pod jeden telefon.

Aplikacja jest publiczna, więc to jedyne bezpieczne ustawienie: nie możemy
sprzedać komuś 2.9 GB pobierania, po którym dostanie 1.6 tok/s.

---

## 8. Ryzyka

- **7B czeka na GPU.** Mechanizm jest kompletny (ikona, 6 tekstów pomocy × 6
  języków, download, gating), ale `BuildConfig.GGML_BACKENDS = "CPU"` → Pro 7B
  jest dziś niewidoczny na każdym urządzeniu. Włącza się sam po przebudowaniu
  natywnej biblioteki z backendem GPU.
- **2.9 GB pobierania.** Wznawialne pobieranie (`Range`, 206/200/416) jest
  zaimplementowane; `.tmp` celowo nie jest usuwany po błędzie. Powyżej 1 GB
  pokazujemy ostrzeżenie „dane mobilne — zalecane Wi-Fi" **z opcją „pobierz
  mimo to"** (blokada na sztywno byłaby zła przy nielimitowanych taryfach).
- **Niewiadoma jakościowa.** Czy 2-bitowy 7B jest lepszy w tłumaczeniu niż
  4-bitowy 1.8B? Teoria mówi tak (pojemność > precyzja), ale nie rozstrzygniemy
  bez urządzenia z GPU. To pierwsza rzecz do sprawdzenia, gdy backend powstanie.
- **Gating RAM.** `totalMem` zwraca RAM *użyteczny*, niższy niż marketingowy
  („6 GB" → ~5.6 GB). Próg dla Pro 7B = 5 GB, żeby nie blokować 6-gigabajtowych.
- **Patch STQ1_0** zabezpieczony w `app/src/main/cpp/patches/` + przypięty
  commit `f5e85d43`. Każdy update llama.cpp = rebase patcha.

---

## 9. Co jest w kodzie

| Plik | Zmiana |
|---|---|
| `data/model/ModelTier.kt` | enum `FAST / ACCURATE / PRO_7B`; PRO_7B ma `requiresGpu = true` i dokładny rozmiar 3,123,810,080 B |
| `data/model/ModelTierBlockReason.kt` | `NONE / LOW_RAM / NO_SPACE / NO_GPU` |
| `engine/ModelDownloader.kt` | wznawialne pobieranie, gating RAM/dysk/**GPU**, ostrzeżenie o danych mobilnych z opcją „mimo to", poprawny URL unsloth |
| `engine/GpuAcceleration.kt` | **nowy** — sonda GPU w runtime: `BuildConfig.GGML_BACKENDS` (co wkompilowano) × co urządzenie potrafi. `gpuLayers()` = 99 albo 0 |
| `engine/CpuTopology.kt` | **nowy** — `inferenceThreads()` = wszystkie rdzenie, kapa 8. W komentarzu pełna tabela pomiarowa, żeby nikt nie wrócił do „tylko duże rdzenie" |
| `engine/HyMt2NativeEngine.kt` | przeciążenia po `ModelTier`, wątki z `CpuTopology`, warstwy GPU z `GpuAcceleration` |
| `ui/components/ModelDownloadDialog.kt` | `sizeLabel` (żeby nie kłamać „~440 MB" przy 2.9 GB) + ekran ostrzeżenia o danych mobilnych |
| `app/build.gradle.kts` | `buildConfigField("GGML_BACKENDS", "\"CPU\"")` — jedyne miejsce do zmiany po przebudowaniu natywnej biblioteki |

---

## 10. Czy greedy psuje jakość? (NIE — sprawdzone, hipoteza obalona)

`llama_jni.cpp` używa `llama_sampler_init_greedy()`, a karta modelu Tencent
zaleca **`temperature 0.7, top_p 0.6, top_k 20, repetition_penalty 1.05`**
(dla 1.8B i 7B identycznie). Wyglądało to na darmowy zysk jakości — greedy
jest deterministyczny, ale znany z powtarzalnych pętli i „płaskich" tłumaczeń.

**Zmierzyliśmy. Hipoteza w większości nie wytrzymała.**

### ⚠️ Pułapka narzędziowa (straciliśmy na to jeden pełny przebieg)

`llama-completion -p "..." **nie** wkłada tekstu do roli `user`. Robi to:

```
<｜hy_begin▁of▁sentence｜>{TWOJ_PROMPT}<｜hy_User｜>      ← prompt w slocie SYSTEMOWYM
```

czyli model dostaje instrukcję tłumaczenia jako system prompt, a potem pustą
turę użytkownika. Aplikacja robi **inaczej** — `llama_chat_apply_template(...
{"user", prompt} ...)`, czyli:

```
<｜hy_User｜>{TWOJ_PROMPT}<｜hy_Assistant｜>
```

Pierwsza tabela A/B (przez `llama-completion`) była więc **niewiarygodna** —
m.in. wygenerowała dla modelu 1.25-bit ciąg `Razorowowowowowow…`, co wzięliśmy
za awarię domyślnego silnika. Po poprawnym ułożeniu promptu ten sam model
oddaje `Rada sprawdziła raport.` — **aplikacja jest zdrowa, to był artefakt.**

Dlatego powstał `_probe.cpp` — miniaturowy replikant ścieżki JNI (ten sam
`hunyuan-dense`, rola `user`, `n_ctx=1024`, `n_batch=512`, te same parametry
samplera). Wszystko poniżej jest z `probe`.

### Wyniki (4 zdania × 2 samplery × 2 tiery, Redmi Note 13)

| | FAST 1.25-bit greedy | FAST + Tencent | Q4_K_M greedy | Q4_K_M + Tencent |
|---|---|---|---|---|
| *board reviewed… signed off on the terms* | „…podpisała umowy" | „…**zatwierdziła warunki**" ✓ | „…podpisała warunki" | „…przyjrzała się raportowi…" |
| *Please find attached the invoice…* | **„Prosimy o przesłanie faktury"** (= *proszę prześlij*, **znaczenie odwrotne**) | „Prosimy o znalezienie dołączonego rachunku" (bliżej) | **„Dołączono fakturę…"** ✓ | identycznie |
| *Could you please send me the file…* | **„Czy mógłbyś prosić o przesłanie mi tego pliku"** (złamana gramatyka) | „Czy moglibyście przysłać mi plik" ✓ | „Czy mógłbyś wysłać mi plik" ✓ | identycznie |
| DE *we will circle back once legal signs off* | **„einen Kreis umrunden"** (dosłownie) | identycznie | **„wieder Kontakt aufnehmen"** ✓ | identycznie |

### Wniosek

- **Na Q4_K_M: zero różnicy** (3/4 identyczne, jedno kosmetyczne).
  `top_k 20` + `top_p 0.6` to i tak prawie greedy. **Zostawiamy greedy** —
  jest deterministyczny, a to dla aplikacji tłumaczeniowej zaleta (to samo
  zdanie daje ten sam wynik po ponownym tłumaczeniu).
- **Na 1.25-bit: sampling naprawia 2 realne błędy na 4** (znaczenie S2,
  gramatykę S3). Próba jest mała, ale sygnał jest spójny: mocno skwantowany
  model ma płaski rozkład i argmax częściej trafia w śmieciowy token.
  Opcja do rozważenia — patrz pytanie 4 w §12.
- **Prawdziwy wniosek jest gdzie indziej:** to nie sampler tylko **tier**
  decyduje o jakości. 1.25-bit gubi idiomy (*circle back*) i odwraca znaczenie
  (*please find attached*), Q4_K_M robi to poprawnie. Warto kierować
  użytkowników na ACCURATE, a nie szukać cudów w samplerze.

---

## 11. Funkcje „Pro" bez GPU: terminologia TAK, styl NIE

Karta modelu dokumentuje 7 wariantów instrukcji. Sprawdziliśmy dwa, które
nie kosztują nic poza tokenami w prompcie (żaden nowy model, żaden GPU).

**Terminologia** (`Reference the following translations: X translates to Y`):

| | wynik dla *The board reviewed the report and signed off on the terms* |
|---|---|
| Q4_K_M bez glosariusza | Rada przeanalizowała raport i podpisała warunki. |
| Q4_K_M **z glosariuszem** | **Rada nadzorcza** przejrzała **sprawozdanie kwartalne** i podpisała **warunki umowy**. |
| 1.25-bit bez glosariusza | Rada sprawdziła raport i podpisała umowy. |
| 1.25-bit **z glosariuszem** | **Rada nadzorcza** przeanalizowała **sprawozdanie kwartalne** i podpisała **warunki umowy**. |

**Działa na obu tierach, wszystkie trzy terminy respektowane** — także na
domyślnym silniku 1.25-bit. To jedyna rzecz w tym całym researchu, która daje
wymiernie „więcej" bez żadnego kosztu w czasie dekodowania (tylko ok. 40
tokenów promptu więcej, a prompt przetwarza się ~2× szybciej niż dekodowanie).

**Styl / formalność** (`...translation style must strictly conform to [formal]`):

| | formal | informal |
|---|---|---|
| Q4_K_M | „Czy mógłbyś w miarę czasu wysłać mi ten plik?" | „Czy mógłbyś wysłać mi plik, gdy będziesz mógł?" |
| 1.25-bit | „Czy mógłbyś przysłać mi plik, gdy będziesz miał chwilę?" | **identycznie** |

**Nie działa.** Na 1.25-bit obie odpowiedzi są co do znaku takie same; na
Q4_K_M różnica jest przypadkowa, nie rejestrowa. Nie budujemy tego.

---

## 12. Pytania do Milosza

1. **GPU: budujemy?** Bez tego Pro 7B nie ma sensu. OpenCL jest realny do
   zbudowania (wymaga dogrania `OpenCL-Headers`), ale nie przetestujemy go na
   Adreno 610 — potrzebny telefon z Adreno 750+ (Snapdragon 8 Gen 3).
2. **Czy w międzyczasie wystawić Pro 7B na CPU z ostrzeżeniem** („wolne, ~20 s
   za zdanie") dla chętnych, czy trzymać ukryte do czasu GPU? Moja rekomendacja:
   ukryte — recenzje za 1.6 tok/s zjedzą aplikację.
3. **Alternatywa dla Pro na CPU:** może zamiast 7B zrobić Pro = 1.8B Q4_K_M z
   lepszym promptem / dłuższym kontekstem / korektą post-hoc? Mamy zmierzone
   4.0 s za zdanie — to jest używalne.
4. **Sampler na tiere FAST** (§10): greedy zostaje na Q4_K_M, ale na 1.25-bit
   sampling naprawił 2 z 4 zdań. Przełączyć 1.25-bit na `temp 0.7 / top_p 0.6 /
   top_k 20 / rep 1.05` (ceną jest niedeterminizm), czy zostawić greedy
   wszędzie i zamiast tego mocniej promować ACCURATE?
5. **Pro = glosariusz?** (§11) Jedyna rzecz w tym researchu, która działa od
   ręki na obu silnikach i nie wymaga ani GPU, ani pobierania: własny słownik
   użytkownika wstrzykiwany do promptu. Budujemy to jako pierwszą funkcję Pro?
