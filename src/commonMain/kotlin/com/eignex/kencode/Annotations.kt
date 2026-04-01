@file:OptIn(ExperimentalSerializationApi::class)

package com.eignex.kencode

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialInfo
import kotlinx.serialization.protobuf.ProtoIntegerType as KxProtoIntegerType
import kotlinx.serialization.protobuf.ProtoType as KxProtoType

/**
 * Specifies the integer encoding strategy for `Int` and `Long` fields in [PackedFormat].
 *
 * Mirrors [kotlinx.serialization.protobuf.ProtoIntegerType].
 */
enum class PackedIntegerType {
    /** Unsigned variable-length (LEB128). Compact for small non-negative values. */
    DEFAULT,
    /** ZigZag variable-length. Compact for small signed values. */
    SIGNED,
    /** Fixed-width. 4 bytes for `Int`, 8 bytes for `Long`. */
    FIXED
}

/**
 * Marks an `Int` or `Long` field with an explicit encoding strategy in [PackedFormat].
 *
 * Mirrors [kotlinx.serialization.protobuf.ProtoType]:
 * - [PackedIntegerType.DEFAULT]: unsigned variable-length (LEB128)
 * - [PackedIntegerType.SIGNED]: ZigZag variable-length
 * - [PackedIntegerType.FIXED]: fixed-width (4 / 8 bytes)
 *
 * Usage:
 * ```
 * @Serializable
 * data class Example(
 *     @PackedType(PackedIntegerType.DEFAULT) val id: Long,    // compact for small positive values
 *     @PackedType(PackedIntegerType.SIGNED)  val delta: Int,  // compact for small signed values
 *     @PackedType(PackedIntegerType.FIXED)   val hash: Int    // always 4 bytes
 * )
 * ```
 *
 * Takes precedence over [kotlinx.serialization.protobuf.ProtoType] when both are present.
 * Has no effect for types outside [PackedFormat].
 */
@SerialInfo
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.SOURCE)
annotation class PackedType(val type: PackedIntegerType)

internal enum class IntEncoding { FIXED, VARINT, ZIGZAG }

private fun PackedIntegerType.toIntEncoding(): IntEncoding = when (this) {
    PackedIntegerType.DEFAULT -> IntEncoding.VARINT
    PackedIntegerType.SIGNED  -> IntEncoding.ZIGZAG
    PackedIntegerType.FIXED   -> IntEncoding.FIXED
}

private fun KxProtoIntegerType.toIntEncoding(): IntEncoding = when (this) {
    KxProtoIntegerType.DEFAULT -> IntEncoding.VARINT
    KxProtoIntegerType.SIGNED  -> IntEncoding.ZIGZAG
    KxProtoIntegerType.FIXED   -> IntEncoding.FIXED
}

internal fun resolveIntEncoding(anns: List<Annotation>, config: PackedConfiguration): IntEncoding {
    anns.filterIsInstance<PackedType>().firstOrNull()?.let { return it.type.toIntEncoding() }
    try {
        anns.filterIsInstance<KxProtoType>().firstOrNull()?.let { return it.type.toIntEncoding() }
    } catch (_: Throwable) { }
    return config.defaultEncoding.toIntEncoding()
}
