package com.nexlock.navigation

object Routes {
    const val DASHBOARD = "dashboard"
    const val APPS = "apps"
    const val VAULT = "vault"
    const val SETTINGS = "settings"

    const val AUTH_SETUP = "auth_setup"
    const val AUTH_MANAGE = "auth_manage"
    const val AUTH_CHANGE = "auth_change"
    const val INTRUDER = "intruder"
    const val AUTOMATION = "automation"
    const val APPEARANCE = "appearance"
    const val SECURITY = "security"
    const val NOTIFICATIONS = "notifications"
    const val SECURITY_LOG = "security_log"
    const val BACKUP = "backup"
    const val ABOUT = "about"
    const val PRIVACY = "privacy"
    const val TERMS = "terms"

    val topLevel = setOf(DASHBOARD, APPS, VAULT, SETTINGS)
}
