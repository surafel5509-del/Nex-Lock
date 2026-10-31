package com.nexlock.core.vault

import com.nexlock.common.model.VaultKind

data class VaultItem(
    val id: Long,
    val kind: VaultKind,
    val displayName: String,
    val fileName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val createdAt: Long,
)

/** Decrypted note, only ever held in memory. */
data class SecureNote(
    val id: Long,
    val title: String,
    val content: String,
    val createdAt: Long,
    val updatedAt: Long,
)
