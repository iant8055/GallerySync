package com.gallery.sync.domain.model

/**
 * The languages the user can choose, on the Welcome screen and in Settings (Ian, 14 Oct 2026: "please select your
 * language to continue", and the same choice in Settings). Each [code] is the BCP-47 tag the How To Guide's
 * translated pages are published under (`how-to-guide-accordion.<code>.html`; see tools/guide/translation-plan.md),
 * and [nativeName] is how the language names itself, so a reader finds theirs without reading English.
 *
 * The app's own screens are English only for now; the choice decides which How To Guide pages open. A language
 * whose pages are not published yet falls back to English (see `InAppPageDialog`).
 */
enum class AppLanguage(val code: String, val nativeName: String) {
    ENGLISH("en", "English"),
    SPANISH("es", "Español"),
    PORTUGUESE_BRAZIL("pt-BR", "Português (Brasil)"),
    FRENCH("fr", "Français"),
    GERMAN("de", "Deutsch"),
    JAPANESE("ja", "日本語"),
    HINDI("hi", "हिन्दी");

    companion object {
        val DEFAULT = ENGLISH

        fun fromCode(code: String?): AppLanguage = entries.firstOrNull { it.code == code } ?: DEFAULT
    }
}
