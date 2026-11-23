package com.example.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import com.example.model.AppLanguage

val LocalAppLanguage = staticCompositionLocalOf { AppLanguage.CS }

/**
 * Lightweight bilingual string helper (`CS` / `EN`) for immediate runtime language switching.
 */
fun tr(language: AppLanguage, cs: String, en: String): String =
  if (language == AppLanguage.EN) en else cs
