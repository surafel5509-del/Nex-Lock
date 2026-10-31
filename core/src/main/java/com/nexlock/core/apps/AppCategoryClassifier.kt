package com.nexlock.core.apps

import com.nexlock.common.model.AppCategory

/**
 * Lightweight, fully local categorisation used by the application picker filters.
 * It is a heuristic over the package name / label only - no app data leaves the device and no
 * installed-app information is ever collected for analytics.
 */
object AppCategoryClassifier {

    private val rules: List<Pair<AppCategory, List<String>>> = listOf(
        AppCategory.SOCIAL to listOf(
            "instagram", "facebook", "twitter", "snapchat", "tiktok", "reddit", "linkedin",
            "pinterest", "threads", "mastodon", "tumblr",
        ),
        AppCategory.MEDIA to listOf(
            "youtube", "netflix", "spotify", "gallery", "photos", "camera", "music",
            "video", "vlc", "podcast", "deezer", "plex",
        ),
        AppCategory.FINANCE to listOf(
            "bank", "wallet", "paypal", "venmo", "revolut", "binance", "coinbase", "stripe",
            "finance", "invest", "telebirr", "cbe", "dashen", "awash", "pay",
        ),
        AppCategory.COMMUNICATION to listOf(
            "whatsapp", "telegram", "signal", "messenger", "viber", "wechat", "discord",
            "gmail", "mail", "skype", "zoom", "slack", "teams", "sms", "phone", "dialer",
        ),
        AppCategory.PRODUCTIVITY to listOf(
            "notes", "calendar", "drive", "docs", "office", "notion", "evernote", "todo",
            "keep", "task", "sheets", "dropbox", "onedrive",
        ),
        AppCategory.GAMES to listOf("game", "puzzle", "clash", "pubg", "roblox", "minecraft", "candy"),
        AppCategory.SYSTEM to listOf("settings", "launcher", "security", "com.android", "com.google.android.gms"),
    )

    fun classify(packageName: String, label: String): AppCategory {
        val haystack = "$packageName $label".lowercase()
        rules.forEach { (category, keywords) ->
            if (keywords.any { haystack.contains(it) }) return category
        }
        return AppCategory.OTHER
    }
}
