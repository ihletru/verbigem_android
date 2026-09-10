plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.google.services)
    //id("com.android.application")
    //id("com.google.gms.google-services")
}

android {
    namespace = "com.verbigem.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.verbigem.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 51
        versionName = "1.0.50"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }

        // Backends GGML wkompilowane w libverbigem_llama.so. To JEDYNE miejsce,
        // które trzeba zmienić po przebudowaniu natywnej biblioteki z GPU.
        // Oddzielone przecinkami, dozwolone wartości: CPU, OPENCL, VULKAN.
        // Kotlin czyta to przez BuildConfig.GGML_BACKENDS (patrz GpuAcceleration).
        // ⚠️ Nigdy nie wpisuj tu backendu, którego nie ma w CMakeLists.txt —
        // aplikacja spróbuje go użyć i GPU init poleci na starych urządzeniach.
        buildConfigField("String", "GGML_BACKENDS", "\"CPU\"")

        // --- AdMob (GMA Next-Gen SDK) ---------------------------------------
        // ⚠️ APK dystrybuowany auto-update'm jest buildem DEBUGOWYM, więc NIE WOLNO
        // przełączać tych ID po `BuildConfig.DEBUG` — realni użytkownicy dostawaliby
        // reklamy testowe do końca świata. JEDYNE miejsce do zmiany to dwie stałe
        // poniżej. Poniżej są już ID PRODUKCYJNE (od 2026-09-09).
        // App ID Verbigema (AdMob → Aplikacje → identyfikator aplikacji).
        val admobAppId = "ca-app-pub-7473087307651079~4666239941"
        // Jednostka banera (AdMob → Aplikacje → Verbigem → Jednostki reklamowe → Baner).
        val admobBannerUnitId = "ca-app-pub-7473087307651079/6237092472"
        buildConfigField("String", "ADMOB_APP_ID", "\"$admobAppId\"")
        buildConfigField("String", "ADMOB_BANNER_UNIT_ID", "\"$admobBannerUnitId\"")
        // UMP (zgody RODO) czyta App ID z manifestu, nie z kodu — ten sam placeholder
        // ląduje w AndroidManifest.xml, żeby oba miejsca nigdy się nie rozjechały.
        manifestPlaceholders["admobAppId"] = admobAppId

        // OpenCL: biała lista SoC (wartości `ro.soc.model`, np. "SM8650").
        // PUSTA = OpenCL wyłączone wszędzie. To nie jest ostrożność na wyrost:
        // zmierzono, że na Adreno 610 ggml-opencl jest 4x WOLNIEJSZY od CPU
        // (decode 1.22 vs 5.17 tok/s przy -ngl 1), przy pełnym offloadzie
        // segfaultuje, a zbudowany na OpenCL 3.0 twardo abortuje proces na
        // urządzeniach OpenCL 2.0. Dopisuj TYLKO po prawdziwym pomiarze.
        buildConfigField("String", "GGML_OPENCL_ALLOWED_SOCS", "\"\"")

        // Only arm64-v8a — STQ1_0 has ARM NEON kernel; x86 emulator is not worth building.
        ndk {
            abiFilters.addAll(listOf("arm64-v8a"))
        }

        externalNativeBuild {
            cmake {
                cppFlags += "-std=c++17 -O3 -DNDEBUG"
                arguments += listOf(
                    "-DANDROID_STL=c++_shared",
                    "-DANDROID_SUPPORT_FLEXIBLE_PAGE_SIZES=ON",
                    // Build llama.cpp native lib as RELEASE even inside a debuggable APK.
                    // Debug build compiles ggml with -O0 -> ~20-50x slower inference (0.3 tok/s).
                    "-DCMAKE_BUILD_TYPE=Release",
                    // KleidiAI: ARM CPU matrix-mult kernels (FP16/FP32) - big speedup on modern SoCs.
                    "-DGGML_CPU_KLEIDIAI=ON",
                    // SPIRV-Headers location (manually extracted, no Vulkan SDK needed)
                    "-DSPIRV-Headers_DIR=C:/SPIRV-Headers-install/share/cmake/SPIRV-Headers",
                    // Host toolchain for vulkan-shaders-gen (NDK clang + NDK ninja)
                    "-DGGML_VULKAN_SHADERS_GEN_TOOLCHAIN=C:/Users/milo/verbigem_android/verbigem-host-toolchain.cmake"
                )
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        // Wlaczone dla `BuildConfig.VERSION_NAME / VERSION_CODE` w ProfileScreen
        // (karta „O aplikacji", `R.string.app_version`). AGP 8+ domyslnie
        // wylacza generowanie BuildConfig.
        buildConfig = true
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
        jniLibs {
            useLegacyPackaging = false
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)

    // Room
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // DataStore
    implementation(libs.androidx.datastore.preferences)

    // Coroutines
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.play.services)

    // Firebase
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.messaging)
    implementation(libs.firebase.functions)
    // Storage (Faza 5): zdjęcia i głosówki w chat_attachments/{chatId}/{msgId}
    implementation(libs.firebase.storage)

    // App Check. The provider differs per variant (`src/debug` vs `src/release`) and
    // so does the dependency, so a release build physically cannot self-attest.
    implementation(libs.firebase.appcheck)
    releaseImplementation(libs.firebase.appcheck.playintegrity)
    debugImplementation(libs.firebase.appcheck.debug)

    // ML Kit Text Recognition (OCR)
    implementation(libs.mlkit.text.recognition)

    // CameraX
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)

    // OkHttp & Gson
    implementation(libs.okhttp)
    implementation(libs.gson)

    // Credential Manager
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)
    implementation(libs.googleid)

    // QR codes (Faza 4): ZXing renders "my code", GMS Code Scanner reads others'.
    implementation(libs.zxing.core)
    implementation(libs.play.services.code.scanner)
    // Obrazki w czatach (Faza 5): Coil — AsyncImage + cache (5.4)
    implementation(libs.coil.compose)

    // Reklamy: GMA Next-Gen SDK (banery) + UMP (zgody RODO/EOG, osobny artefakt)
    implementation(libs.gma.ads)
    implementation(libs.ump.user.messaging)

    debugImplementation(libs.androidx.ui.tooling)
}
