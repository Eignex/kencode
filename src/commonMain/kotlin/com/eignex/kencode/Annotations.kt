@file:OptIn(ExperimentalSerializationApi::class)

package com.eignex.kencode

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialInfo
import kotlinx.serialization.protobuf.ProtoIntegerType as KxProtoIntegerType
import kotlinx.serialization.protobuf.ProtoType as KxProtoType

/** Integer encoding for `Int` and `Long` fields in [PackedFormat]. */
enum class IntPacking {
    /** Unsigned LEB128 varint. */
    DEFAULT,

    /** ZigZag varint, for small signed values. */
    SIGNED,

    /** 4 bytes for `Int`, 8 for `Long`. */
    FIXED,
}

/**
 * Sets the [IntPacking] of an `Int` or `Long` field. Without it, [kotlinx.serialization.protobuf.ProtoType]
 * applies if present, then [PackedConfiguration.defaultEncoding].
 */
@SerialInfo
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.SOURCE)
annotation class PackedType(
    val type: IntPacking,
)

private fun KxProtoIntegerType.toIntPacking(): IntPacking = when (this) {
    KxProtoIntegerType.DEFAULT -> IntPacking.DEFAULT
    KxProtoIntegerType.SIGNED -> IntPacking.SIGNED
    KxProtoIntegerType.FIXED -> IntPacking.FIXED
}

internal fun resolveIntEncoding(anns: List<Annotation>, config: PackedConfiguration): IntPacking {
    anns.filterIsInstance<PackedType>().firstOrNull()?.let { return it.type }
    try {
        anns.filterIsInstance<KxProtoType>().firstOrNull()
            ?.let { return it.type.toIntPacking() }
    } catch (_: Throwable) {
    }
    return config.defaultEncoding
}
