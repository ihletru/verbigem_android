package com.verbigem.app.engine

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import com.verbigem.app.BuildConfig

/**
 * Wybór backendu GGML — CPU vs GPU.
 *
 * Verbigem to aplikacja publiczna: trafia zarówno na Snapdragona 8 Elite, jak i
 * naSnapdragona 685 czy starsze Exynosy. Nie wolno więc ani z góry wyłączyć GPU,
 * ani z góry go założyć. Decyzja musi być **iloczynem dwóch rzeczy**:
 *
 * 1. **Co jest wkompilowane** — `BuildConfig.GGML_BACKENDS` (ustawiane w
 *    `app/build.gradle.kts`). Dziś `CPU`. To jedyne miejsce do zmiany po
 *    przebudowaniu natywnej biblioteki z OpenCL lub Vulkanem.
 * 2. **Co to urządzenie potrafi uruchomić** — sonda w runtime. GPU backendu
 *    nie wolno włączyć na telefonie, który go nie ma, bo inicjalizacja albo
 *    rzuci wyjątkiem, albo (gorsze) zawiesi proces w natywnym kodzie, skąd
 *    nie da się już wrócić.
 *
 * Filozofia: **stary telefon to poprawny przypadek, nie błąd.** Urządzenie bez
 * GPU ma dostać dokładnie to samo tłumaczenie, tylko wolniej.
 */
object GpuAcceleration {

    private const val TAG = "GpuAcceleration"

    enum class Backend { CPU, OPENCL, VULKAN }

    /** Offload „wszystkich warstw" — wartownik llama.cpp. */
    const val ALL_LAYERS = 99

    /** Wynik sondy jest stały przez cały proces — nie ma sensu pytać dwa razy. */
    @Volatile
    private var cached: Backend? = null

    @Volatile
    private var cacheValid = false

    /**
     * Backend, którego to urządzenie faktycznie może użyć. `null` = CPU.
     * Wynik jest cache'owany.
     */
    @Synchronized
    fun select(context: Context): Backend? {
        if (cacheValid) return cached
        val result = probe(context)
        cached = result
        cacheValid = true
        Log.i(TAG, "backend=$result (compiled=${BuildConfig.GGML_BACKENDS}, " +
            "device=${Build.DEVICE}, soc=${Build.HARDWARE})")
        return result
    }

    /** Liczba warstw do zrzucenia na GPU. `0` oznacza czyste CPU. */
    fun gpuLayers(context: Context): Int =
        if (select(context) == null) 0 else ALL_LAYERS

    private fun probe(context: Context): Backend? {
        val compiled = BuildConfig.GGML_BACKENDS
            .split(',')
            .map { it.trim().uppercase() }
            .toSet()

        // Vulkan ma pierwszeństwo: jest dojrzalszy w llama.cpp niż OpenCL.
        if (compiled.contains("VULKAN") && supportsVulkan(context)) return Backend.VULKAN
        if (compiled.contains("OPENCL") && supportsOpenCL()) return Backend.OPENCL
        return null
    }

    /**
     * Vulkan: wymagamy pełnego poziomu (nie tylko compute) oraz wersji 1.1+.
     * `FEATURE_VULKAN_HARDWARE_LEVEL`: 0 = brak, 1 = compute, 2 = pełny.
     * llama.cpp używa m.in. subgroup operations, które na starociach bywają
     * niedostępne, stąd próg 1.1 zamiast 1.0.
     */
    private fun supportsVulkan(context: Context): Boolean {
        val pm = context.packageManager
        if (!pm.hasSystemFeature(PackageManager.FEATURE_VULKAN_HARDWARE_LEVEL)) return false
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return false

        val level = try {
            val info = pm.getSystemAvailableFeatures().firstOrNull {
                it.name == PackageManager.FEATURE_VULKAN_HARDWARE_LEVEL
            }
            // API 26+: FEATURE_VULKAN_HARDWARE_LEVEL nie niesie poziomu — bierzemy
            // z FEATURE_VULKAN_HARDWARE_VERSION (wersja w górnych bitach).
            info?.version ?: 0
        } catch (e: Exception) {
            Log.w(TAG, "vulkan level probe failed", e)
            0
        }
        // 0x00401000 = Vulkan 1.1
        val versionOk = level >= 0x00401000
        if (!versionOk) {
            Log.d(TAG, "vulkan present but version too low: 0x${level.toString(16)}")
        }
        return versionOk
    }

    /**
     * OpenCL — ⚠️ sonda `dlopen` NIE wystarcza i sama w sobie jest groźna.
     *
     * Zmierzone na Redmi Note 13 (Snapdragon 685 / Adreno 610), 2026-09-07:
     *
     * - `libOpenCL.so` **jest** dostępna i ładuje się bez błędu, więc sama
     *   obecność biblioteki nic nie znaczy.
     * - ggml-opencl zbudowany z domyślnym `GGML_OPENCL_TARGET_VERSION=300`
     *   **twardo abortsuje proces** (`GGML_ASSERT` w `ggml-opencl.cpp:212`),
     *   bo platforma Qualcomma raportuje OpenCL 3.0, a urządzenie to OpenCL 2.0
     *   → zapytanie `CL_DEVICE_OPENCL_C_ALL_VERSIONS` zwraca -30.
     * - Po zbudowaniu z `TARGET_VERSION=200` backend wstaje, ale jest
     *   **4× wolniejszy od CPU**: przy `-ngl 1` decode spada z 5.17 do
     *   **1.22 tok/s**, a przy pełnym offloadzie (`-ngl 99`) jest segfault.
     *
     * Wniosek: „ładuje się" ≠ „działa" ≠ „jest szybszy". Dlatego decyzja jest
     * **domyślnie odmowna** — włączamy OpenCL tylko na SoC wpisanych na listę
     * `BuildConfig.GGML_OPENCL_ALLOWED_SOCS`, którą wypełnia się dopiero po
     * prawdziwym pomiarze na danym urządzeniu. Nieznany sprzęt → CPU.
     */
    private fun supportsOpenCL(): Boolean {
        val soc = socModel()
        val allowed = BuildConfig.GGML_OPENCL_ALLOWED_SOCS
            .split(',')
            .map { it.trim().uppercase() }
            .filter { it.isNotEmpty() }
            .toSet()

        if (allowed.isEmpty()) {
            Log.i(TAG, "opencl: allowlist empty -> CPU (soc=$soc)")
            return false
        }
        if (soc == null || soc.uppercase() !in allowed) {
            Log.i(TAG, "opencl: soc=$soc not on allowlist -> CPU")
            return false
        }

        return try {
            System.loadLibrary("OpenCL")
            Log.i(TAG, "opencl: soc=$soc allowed and libOpenCL.so loaded")
            true
        } catch (e: UnsatisfiedLinkError) {
            Log.i(TAG, "opencl: libOpenCL.so not accessible to apps -> CPU")
            false
        } catch (e: SecurityException) {
            Log.i(TAG, "opencl: libOpenCL.so blocked -> CPU")
            false
        }
    }

    /**
     * Identyfikator SoC, np. `SM6225`. `Build.SOC_MODEL` jest od API 31, więc na
     * starszych telefonach czytamy `ro.soc.model` refleksją po `SystemProperties`.
     * `null` = nie udało się ustalić → [supportsOpenCL] i tak odmówi.
     */
    private fun socModel(): String? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Build.SOC_MODEL?.takeIf { it.isNotBlank() }?.let { return it }
        }
        return try {
            val sp = Class.forName("android.os.SystemProperties")
            val get = sp.getMethod("get", String::class.java)
            (get.invoke(null, "ro.soc.model") as? String)
                ?.takeIf { it.isNotBlank() }
        } catch (e: Exception) {
            Log.d(TAG, "soc model probe failed", e)
            null
        }
    }
}
