package org.eshragh.kartext.database

import androidx.room.TypeConverter
import org.eshragh.kartext.models.RecordType

class TypeConverters {
    @TypeConverter
    fun toRecordType(value: String) = enumValueOf<RecordType>(value)

    @TypeConverter
    fun fromRecordType(value: RecordType) = value.name
}