package com.nexlock.database.converter

import androidx.room.TypeConverter
import com.nexlock.common.model.AppCategory
import com.nexlock.common.model.AuthMode
import com.nexlock.common.model.SecurityEventType
import com.nexlock.common.model.SecuritySeverity
import com.nexlock.common.model.VaultKind

class Converters {

    @TypeConverter fun fromAppCategory(value: AppCategory): String = value.name
    @TypeConverter fun toAppCategory(value: String?): AppCategory = AppCategory.fromStorage(value)

    @TypeConverter fun fromAuthMode(value: AuthMode): String = value.name
    @TypeConverter fun toAuthMode(value: String?): AuthMode = AuthMode.fromStorage(value)

    @TypeConverter fun fromEventType(value: SecurityEventType): String = value.name
    @TypeConverter fun toEventType(value: String?): SecurityEventType = SecurityEventType.fromStorage(value)

    @TypeConverter fun fromSeverity(value: SecuritySeverity): String = value.name
    @TypeConverter fun toSeverity(value: String?): SecuritySeverity =
        SecuritySeverity.entries.firstOrNull { it.name == value } ?: SecuritySeverity.INFO

    @TypeConverter fun fromVaultKind(value: VaultKind): String = value.name
    @TypeConverter fun toVaultKind(value: String?): VaultKind = VaultKind.fromStorage(value)
}
