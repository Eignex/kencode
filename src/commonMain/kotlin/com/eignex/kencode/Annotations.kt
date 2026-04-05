@file:OptIn(ExperimentalSerializationApi::class)

package com.eignex.kencode

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialInfo
import kotlinx.serialization.protobuf.ProtoIntegerType as KxProtoIntegerType
import kotlinx.serialization.protobuf.ProtoType as KxProtoType

/**
 * Integer encoding strategy for `Int` and `Long` fields in [PackedFormat].
 *
 * Compatible with [kotlinx.serialization.protobuf.ProtoIntegerType] as a fallback (see [PackedType]).
 */
enum class IntPacking {
    /** Unsigned variable-length (LEB128). Compact for small non-negative values. */
    VARINT,
    /** ZigZag variable-length. Compact for small signed values. */
    ZIGZAG,
    /** Fixed-width. 4 bytes for `Int`, 8 bytes for `Long`. */
    FIXED
}

/**
 * Overrides the integer encoding strategy for an `Int` or `Long` field in [PackedFormat].
 * Falls back to [kotlinx.serialization.protobuf.ProtoType] if present, then [PackedConfiguration.defaultEncoding].
 */
@SerialInfo
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.SOURCE)
annotation class PackedType(val type: IntPacking)

private fun KxProtoIntegerType.toIntPacking(): IntPacking = when (this) {
    KxProtoIntegerType.DEFAULT -> IntPacking.VARINT
    KxProtoIntegerType.SIGNED  -> IntPacking.ZIGZAG
    KxProtoIntegerType.FIXED   -> IntPacking.FIXED
}

internal fun resolveIntEncoding(anns: List<Annotation>, config: PackedConfiguration): IntPacking {
    anns.filterIsInstance<PackedType>().firstOrNull()?.let { return it.type }
    try {
        anns.filterIsInstance<KxProtoType>().firstOrNull()?.let { return it.type.toIntPacking() }
    } catch (_: Throwable) { }
    return config.defaultEncoding
}
