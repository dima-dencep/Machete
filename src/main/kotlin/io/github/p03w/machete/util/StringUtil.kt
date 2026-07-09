package io.github.p03w.machete.util

import java.util.*

fun String.capital() = replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }

fun Regex.matches(char: Char) = matches(char.toString())

/**
 * The final path segment of a jar entry name, e.g. `com/foo/Bar.class` -> `Bar.class`
 */
val String.entryName: String
    get() = substringAfterLast('/')

/**
 * The extension of a jar entry name, matching [java.io.File.extension] semantics,
 * e.g. `com/foo/Bar.class` -> `class`, `com/foo/README` -> `` (empty)
 */
val String.entryExtension: String
    get() = entryName.substringAfterLast('.', "")
