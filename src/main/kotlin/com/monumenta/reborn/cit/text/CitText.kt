package com.monumenta.reborn.cit.text

import com.google.gson.JsonParser
import com.monumenta.reborn.cit.rule.ParsedName
import java.util.regex.Pattern
import java.util.regex.PatternSyntaxException

object CitText {
    fun parseName(raw: String?): ParsedName {
        if (raw.isNullOrBlank()) return ParsedName(null, pattern = false, ignoreCase = false, regex = null)

        var pattern = false
        var ignoreCase = false
        var regexSyntax = false
        var value = raw

        when {
            value.startsWith("ipattern:") -> {
                pattern = true
                ignoreCase = true
                value = value.removePrefix("ipattern:")
            }
            value.startsWith("pattern:") -> {
                pattern = true
                value = value.removePrefix("pattern:")
            }
            value.startsWith("iregex:") -> {
                pattern = true
                ignoreCase = true
                regexSyntax = true
                value = value.removePrefix("iregex:")
            }
            value.startsWith("regex:") -> {
                pattern = true
                regexSyntax = true
                value = value.removePrefix("regex:")
            }
        }

        val stripped = stripJsonText(value)
        return ParsedName(stripped, pattern, ignoreCase, compileNamePattern(stripped, pattern, ignoreCase, regexSyntax))
    }

    fun compileNamePattern(name: String, namePattern: Boolean, nameIgnoreCase: Boolean, regexSyntax: Boolean): Pattern? {
        if (!namePattern) return null
        val flags = if (nameIgnoreCase) Pattern.CASE_INSENSITIVE or Pattern.UNICODE_CASE else 0
        return try {
            Pattern.compile(if (regexSyntax) name else globToRegex(name), flags)
        } catch (_: PatternSyntaxException) {
            null
        }
    }

    fun stripJsonText(value: String): String {
        val trimmed = value.trim()
        if (trimmed.startsWith("{") && trimmed.contains("\"text\"")) {
            try {
                val obj = JsonParser.parseString(trimmed).asJsonObject
                if (obj.has("text")) return obj["text"].asString
            } catch (_: Exception) {
                return value
            }
        }
        if (trimmed.length >= 2 && trimmed.startsWith("\"") && trimmed.endsWith("\"")) {
            return trimmed.substring(1, trimmed.length - 1)
        }
        return value
    }

    private fun globToRegex(glob: String): String = buildString {
        for (ch in glob) {
            when (ch) {
                '*' -> append(".*")
                '?' -> append('.')
                '\\', '.', '[', ']', '(', ')', '{', '}', '+', '^', '$', '|' -> append('\\').append(ch)
                else -> append(ch)
            }
        }
    }
}
