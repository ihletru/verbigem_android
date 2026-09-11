# Research 2: inny model "Pro" (CPU-only, bez GPU) — 2026-09-07

Poprzedni task (log `2026-09-07.md` + `SILNIK_PRO_RESEARCH.md`): Pro = **7B
UD-Q2_K_XL**, ale na CPU Redmi Note 13 daje **1.64 tok/s → odrzucone**.
OpenCL/GPU to pułapka (4× wolniej + `GGML_ASSERT` crash na Adreno 610),
speculative decoding nie ratuje (decode bandwidth-bound). Zostały tylko
FAST (1.25-bit, 440 MB) i ACCURATE (Q4_K_M, 1.13 GB).

Ten dokument szuka **innego** modelu / metody na "Pro" — pod twardym
ograniczeniem: **CPU-only, 6 GB RAM, brak GPU**.

## 0. Twarde ograniczenia (nie do obejścia na tym telefonie)

- **Decode na CPU jest bandwidth-bound**: czas/token ∝ rozmiar wag w RAM.
  Większy model = liniowo wolniej, niezależnie od architektury.
- Pomiar (llama-bench, t=8): 1.8B Q4 = 5.76 tok/s · 7B Q2 = 1.64 tok/s.
- RAM użyteczna ~5.6 GB → model + KV + apka muszą się zmieścić. Górna
  granica praktyczna to model **≤ ~4.5 GB** (inaczej nie ma miejsca na KV).
- Obsługiwane języki: **PL, EN, DE, ES, TR, ZH**. Model Pro MUSI znać ZH —
  to eliminuje kandydatów czysto europejskich (EuroLLM).

## 1. Kandydaci na model — tabela

Rozmiary Q4_K_M szacowane; tok/s = ekstrapolacja z pomiarów (do zmierzenia
na telefonie). "ZH?" = czy model pokrywa chiński na przyzwoitym poziomie.

| Model | Parametry | Q4 ≈ rozmiar | szac. tok/s | ZH? | Werdykt |
|---|---|---|---|---|---|
| **Qwen3-4B** | dense 4B | ~2.5 GB | ~3.0 | TAK (baza chińska) | **Testować** — ogólny, silne instrukcje |
| **Gemma-3-4B** | dense 4B | ~2.5 GB | ~3.0 | TAK (140 jęz.) | **Testować** — wielojęzyczny z założenia |
| **EuroLLM-9B** | dense 9B | ~5.5 GB | ~1.2 | NIE | Odrzucić — za wolny + brak ZH |
| **EuroLLM-1.7B** | dense 1.7B | ~1.1 GB | ~5.8 | NIE | Tylko zamiennik ACCURATE (brak ZH) |
| **Phi-mini-MoE** | 7.6B / **2.4B aktyw.** | ~4.5 GB | ~4.0* | słabo | **Ciekawy (MoE)**, ale słaby ZH/PL |
| **Hunyuan 1.8B** (obecny) | dense 1.8B | 1.13 GB | 5.76 | TAK | Baseline |

\* Phi-mini-MoE: decode czyta TYLKO aktywnych ekspertów (~2.4B) + warstwy
współdzielone → prędkość jak model ~3B, jakość jak 7.6B. To jedyna architektura
rozłączająca jakość od prędkości na CPU. Ale Phi to model anglocentryczny —
PL/DE/ES/TR/ZH będą gorsze niż u Hunyuana.

### Wniosek z tabeli
- **Dense 4B** to jedyne realne "większe" ≈ 2× wolniej (3 vs 6 tok/s) za
  (prawdopodobnie) marginalnie lepszą jakość. Ryzyko: Hunyuan 1.8B jest
  wytrenowany pod MT, więc ogólny 4B może go **nie przebić** w czystym
  tłumaczeniu — trzeba A/B, nie założenie.
- **MoE** to właściwa architektura "Pro na CPU", ale w 2026 nie ma silnego
  **wielojęzycznego** MoE, który mieści się w 6 GB i zna ZH. Phi-mini-MoE
  pasuje rozmiarem, ale językowo odpada dla Verbigema.
- **EuroLLM odpada** (brak ZH + za wolny w wersji użytecznej).

## 2. Gdzie obecne tiery faktycznie failują (z 8 zdań testowych)

Różnice FAST↔ACCURATE są małe na płynności, ale **obie** robią błędy
faktograficzne — i to jest miejsce na "Pro":

- FAST: *"half past seven"* → **"wpół do siódmej" (6:30)** — błąd liczby/godziny.
- ACCURATE: *"podpisałem"* (neutralne) → **"podpisałam"** (żeński) — błąd rodzaju.
- Oba: *"send it back signed"* → zniekształcone ("odprawienie podpisanym").

→ "Pro" nie musi być "bardziej płynny". Musi być **bardziej poprawny
faktograficznie** (liczby, czas, płeć, idiomy). To celuje metoda, nie tylko
rozmiar.

## 3. Metody (dźwignia jakości BEZ większego modelu)

### 3.1 Self-refinement / TEaR (Translate → Estimate → Refine)
- Dwuprzebiegowe: (1) przetłumacz, (2) "sprawdź i popraw błędy: liczby,
  czas, rodzaj, sens" → odpowiedz poprawioną. Framework TEaR (arXiv 2402.16379)
  pokazuje, że samorefleksja podnosi jakość MT na małych modelach.
- **Celuje dokładnie w błędy z §2** (godzina, rodzaj).
- Koszt: **~2.5-3× latencji** (przebieg 2. ma znacznie większy prompt →
  droższy prefill; zob. obliczenia w §3.4). Dla "Pro" akceptowalne tylko,
  jeśli jakość rośnie wyraźnie.
- Wymaga tylko zmiany w `HyMt2NativeEngine.buildPrompt()` + pętli —
  **żadnego nowego modelu, żadnego GPU**.
- ⚠️ Greedy zostaje (z §10b: deterministyczny, bez wariancji).

### 3.4 Ile dokładnie wolniej? (obliczone z pomiarów pp/tg)

Miary 1.8B Q4_K_M @ t=8: **pp = 13.1 tok/s, tg = 5.76 tok/s**
(z wczorajszego logu). Przykład: zdanie, prompt 40 tok, tłumaczenie 25 tok.

| Wariant | Prefill | Decode | Suma | vs 1 przebieg |
|---|---|---|---|---|
| 1 przebieg | 40/13.1 = 3.1s | 25/5.76 = 4.3s | **7.4s** | 1× |
| self-refine, przebieg 2 (prompt ~115 tok) | 115/13.1 = 8.8s | 4.3s | 13.1s | — |
| **self-refine razem** | — | — | **20.5s** | **2.8×** |
| self-refine, krótki prompt (~60 tok) | 4.6s | 4.3s | 8.9s | **2.2×** (razem 16.3s) |
| self-refine, długi segment (~300+300 tok) | — | — | ~177s | **2.4×** |

→ Realnie **~2.2-2.8×, średnio ~2.5×**, nie 2×. Dodatkowy koszt to
przede wszystkim **prefill drugiego przebiegu** (musi odczytać oryginał +
pierwsze tłumaczenie).

⚠️ **Haczyk — model 4B w JEDNYM przebiegu może być SZYBSZY niż self-refine:**
4B @ ~3 tok/s, 25 tok decode = 8.3s + prefill ≈ 3s → **~11s**, podczas gdy
self-refine to 16-20s. Jeśli jakość 4B ≥ jakość (1.8B+refine), to 4B wygrywa
też na prędkości i jest prostszy (brak logiki 2 przebiegów). Dlatego self-refine
nie jest automatycznie "najlepszy" — trzeba A/B jakości obu.

### 3.2 Few-shot in-context (przykłady w prompcie)
- Do promptu dołożyć 1–2 przykłady tłumaczenia tej pary języków
  (z glosariusza lub statycznego korpusu). Pomaga trudne przypadki.
- Koszt: +~40–80 tokenów promptu (pp jest ~2× szybsze niż decode → tanie).

### 3.3 Glosariusz (już zrobione, §11 poprzedniego taska)
- Działa na obu tierach, jedyna funkcja Pro bez GPU. Rozbudować pokrycie.

## 4. Rekomendacja

**Nie gonić za większym dense modelem** (7B martwy, 4B = 2× wolniej za
prawdopodobnie marginalny zysk, a Hunyuan 1.8B jest MT-specialized).
Hierarchia:

1. **PRIMARY (kandydat) — metoda self-refinement (TEaR) na 1.8B Q4_K_M.**
   Zero nowego modelu, celuje w realne błędy faktograficzne, działa na CPU.
   ⚠️ Ale ~2.5-3× wolniej (§3.4) i trudniejsze w kodzie (pętla 2 przebiegów).
   Traktować jako OPCJĘ A, nie pewnik — zestawić z Opcją B (4B 1-przebieg).
2. **SECONDARY — test A/B Qwen3-4B / Gemma-3-4B (Q4) jako drop-in "Pro".**
   Tylko jeśli A/B na FLORES-200 (PL/DE/ES/TR/ZH↔EN) pokaże wyraźną
   przewagę nad ACCURATE i ZH trzyma poziom. Akceptujemy ~3 tok/s.
3. **WATCH — wielojęzyczny MoE.** Jedyna architektura rozłączająca jakość
   od prędkości na CPU. Dziś tylko anglocentryczny Phi-mini-MoE mieści się
   w 6 GB. Wrócić, gdy pojawi się MoE z ZH+EU i ≤4.5 GB.

### Plan testów (gdy zdecydujesz)
1. Pobierz `Qwen3-4B-*` Q4_K_M i `Gemma-3-4B-*` Q4_K_M do `_bench_models/`.
2. Rozbuduj `bench_prompts.txt` do ~50 zdań FLORES-200 (PL/DE/ES/TR/ZH↔EN).
3. `_probe.cpp -b` na każdym kandydacie + ACCURATE; ocena chrF / COMET
   (skrypt python, już mamy `gen_bench_v2.py`).
4. Jeśli 4B bije ACCURATE na ≥60% i ZH OK → kandydat Pro (akcept. 3 tok/s).
5. Równolegle: prototyp self-refinement na ACCURATE, zmierz Δjakości przy ~2.5-3× latencji. Porównaj z 4B 1-przebieg (§3.4).

## 5. Pytania do Milosza
- Czy "Pro" = lokalny model (offline), czy akceptujesz też silnik online
  (istniejąca opcja ONLINE/BOTH) jako "Pro"?
- Czy ~2.5-3× latencji w self-refinement jest OK dla warstwy Pro
  (pamiętając, że 4B 1-przebieg może być szybszy, §3.4)?
- Czy ~3 tok/s (model 4B) jest akceptowalne dla Pro, czy musi być blisko
  obecnego Dokładnego (~6 tok/s)?
