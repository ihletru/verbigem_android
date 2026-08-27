package com.verbigem.app.data.model

enum class LangCode(
    val code: String,
    val displayName: String,
    val flag: String,
    val englishName: String,
    val bcp47: String
) {
    PL("pl", "Polski", "🇵🇱", "Polish", "pl-PL"),
    EN("en", "English", "🇬🇧", "English", "en-US"),
    ES("es", "Español", "🇪🇸", "Spanish", "es-ES"),
    ZH("zh", "中文", "🇨🇳", "Chinese", "zh-CN"),
    DE("de", "Deutsch", "🇩🇪", "German", "de-DE"),
    TR("tr", "Türkçe", "🇹🇷", "Turkish", "tr-TR");

    companion object {
        fun fromCode(code: String): LangCode {
            return entries.find { it.code.equals(code, ignoreCase = true) } ?: PL
        }
    }
}
