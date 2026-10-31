package com.nexlock.feature.settings

/**
 * In-app legal copy. Written to match what the application actually does - no placeholder text.
 */
object LegalContent {

    const val LAST_UPDATED = "1 October 2026"

    val privacyPolicy = """
        NexLock privacy policy
        Last updated: $LAST_UPDATED

        1. Data we collect
        NexLock does not collect, sell or share personal data. The application has no INTERNET
        permission, so it cannot transmit anything off your device.
        Everything below is stored only inside NexLock's private app sandbox:

        • the list of applications you chose to protect (package name, display name, category)
        • your authentication policy (PIN/pattern/password mode, timeout, toggles)
        • a PBKDF2-SHA256 hash of your unlock credential, peppered with a Keystore key
        • your encrypted vault files, encrypted notes and intruder incident records
        • a local security journal of security-relevant events

        2. What we never access
        • We never read your messages, contacts, call logs or photos unless you explicitly import a
          file into the vault through the system file picker.
        • We never record audio, video or photos in the background.
        • We never store biometric data. Biometric verification is performed by Android and NexLock
          only receives a success/failure callback.
        • Installed-app information is never used for advertising or profiling.

          3. Permissions and why they exist
        • Accessibility service (optional): detects when a protected app comes to the foreground so
          the lock screen can be shown immediately. NexLock never reads screen content, never
          performs gestures and never types on your behalf.
        • Usage access (optional): fallback foreground detection when the accessibility service is off.
        • Camera (optional): only for a single front-camera capture after repeated failed unlock
          attempts, and only if you enabled that option.
        • Location (optional): only to read the Wi-Fi network name and to evaluate trusted places.
        • Notifications: security alerts that never contain protected app names.

        4. Encryption
        Vault files and private notes are encrypted with AES-256-GCM using a key generated inside the
        Android Keystore (StrongBox or TEE when available). Your credential is never stored in
        plaintext; it is hashed with PBKDF2-HMAC-SHA256 (210,000 iterations, random salt) plus a
        device-bound HMAC pepper.

        5. Retention and deletion
        You can delete individual vault items, notes and incidents inside the app, or clear the entire
        security journal. Uninstalling NexLock removes the app sandbox and the Keystore key, which
        permanently destroys the ability to decrypt any remaining files.

        6. Backups you create
        Encrypted backups are produced only when you ask for them, are protected with a passphrase you
        choose, and are written to a location you pick. NexLock cannot decrypt them without that
        passphrase.

        7. Children
        NexLock is not directed at children and does not knowingly collect data from anyone.

        8. Changes
        Material changes to this policy will be published with a new "last updated" date in the app.

        9. Contact
        Questions about privacy can be raised through the project's issue tracker.
    """.trimIndent()

    val termsOfService = """
        NexLock terms of service
        Last updated: $LAST_UPDATED

        1. Acceptance
        By installing and using NexLock you agree to these terms.

        2. What NexLock is
        NexLock is a privacy tool that adds an authentication gate in front of applications you select
        and stores files you import in an encrypted local vault.

        3. What NexLock is not
        NexLock is not a substitute for device encryption, a screen lock, or OS-level security. It
        cannot prevent a determined attacker with root access, physical control of an unlocked device,
        or a modified operating system from accessing your data. Protection is best-effort and
        app-level.

        4. Your responsibilities
        • Remember your unlock credential and store your recovery code safely.
        • Keep your device OS up to date.
        • Only capture intruder photos where doing so is lawful in your jurisdiction.
        • Do not use NexLock to access devices or data you are not authorised to access.

        5. Recovery and data loss
        Credentials are hashed, not stored. If you lose both your credential and your recovery code,
        NexLock cannot restore access; the only remedy is clearing app data, which deletes vault
        contents and notes irreversibly.

        6. Permissions
        Some features require optional system permissions. Refusing them limits functionality but never
        causes data loss. You can revoke any permission at any time in system settings.

        7. Availability and warranty
        NexLock is provided "as is", without warranty of any kind, to the maximum extent permitted by
        law.

        8. Limitation of liability
        To the maximum extent permitted by law, the authors are not liable for indirect, incidental or
        consequential damages, or for loss of data resulting from forgotten credentials, device reset,
        or uninstallation.

        9. Open-source components
        NexLock uses AndroidX, Jetpack Compose, Hilt, Room and Kotlin coroutines, distributed under
        their respective licences.

        10. Changes to these terms
        Continued use after an update constitutes acceptance of the updated terms.
    """.trimIndent()
}
