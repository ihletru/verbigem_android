package com.verbigem.app.data.model

/**
 * Why a given [ModelTier] cannot be offered / downloaded on this device.
 *
 * Checked *before* the download starts, so the user never burns 4.6 GB of mobile
 * data on a model that will OOM the process at load time.
 */
enum class ModelTierBlockReason {
    /** Device is fine — the tier may be used. */
    NONE,

    /** Total device RAM is below [ModelTier.minRamBytes]. Hard blocker. */
    LOW_RAM,

    /** Not enough free space on internal storage for the weights + working room. */
    NO_SPACE,

    /**
     * The tier needs a GPU backend ([ModelTier.requiresGpu]) and this device has
     * none that we can actually drive.
     *
     * Not a hard failure of the device — it means "we have not built a backend
     * this phone can use, or the phone has no GPU we dare touch". The engine is
     * hidden and everything else keeps working on CPU.
     */
    NO_GPU
}
