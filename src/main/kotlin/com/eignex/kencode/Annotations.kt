@file:OptIn(ExperimentalSerializationApi::class)

package com.eignex.kencode

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialInfo

/**
 * Marks an `Int` or `Long` property to be encoded using **signed varint** encoding.
 *
 * Behavior:
 * - Values are ZigZag–transformed, so small negative numbers encode compactly.
 * - Serialized using a variable-length LEB128-style varint.
 * - Applies only to `Int` and `Long` fields inside `PackedFormat` structures.
 *
 * Decoding:
 * - Automatically applies ZigZag decode.
 *
 * Usage:
 * ```
 * @Serializable
 * data class Example(
 *     @VarInt val delta: Int,   // small negatives become very compact
 *     @VarInt val offset: Long
 * )
 * ```
 *
 * Has no effect for primitive types outside `PackedFormat`.
 */
@SerialInfo
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.RUNTIME)
annotation class VarInt

/**
 * Marks an `Int` or `Long` property to be encoded using **unsigned varint** encoding.
 *
 * Behavior:
 * - No ZigZag transform; values are treated as non-negative.
 * - Serialized using a variable-length LEB128-style varint.
 * - Applies only to `Int` and `Long` fields inside `PackedFormat` structures.
 *
 * Recommended for:
 * - IDs
 * - version counters
 * - monotonic sequence numbers
 * - any value that is always `>= 0`
 *
 * Usage:
 * ```
 * @Serializable
 * data class Example(
 *     @VarUInt val id: Long,    // compact for small and medium positive values
 *     @VarUInt val count: Int
 * )
 * ```
 *
 * Has no effect for primitive types outside `PackedFormat`.
 */
@SerialInfo
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.RUNTIME)
annotation class VarUInt

/**
 * Marks an `Int` or `Long` property to be encoded using **fixed-length** encoding.
 *
 * Behavior:
 * - Serialized densely using fixed bytes (4 bytes for Int, 8 bytes for Long).
 * - **Overrides** any global `PackedConfiguration` defaults (`defaultVarInt` or `defaultZigZag`).
 * - Applies only to `Int` and `Long` fields inside `PackedFormat` structures.
 *
 * Usage:
 * ```
 * @Serializable
 * data class Example(
 *     @FixedInt val hash: Int,    // always 4 bytes, even if defaultVarInt = true
 *     @FixedInt val mask: Long    // always 8 bytes
 * )
 * ```
 */
@SerialInfo
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.RUNTIME)
annotation class FixedInt

fun List<Annotation>.hasVarInt(): Boolean = any { it is VarInt }
fun List<Annotation>.hasVarUInt(): Boolean = any { it is VarUInt }
fun List<Annotation>.hasFixedInt(): Boolean = any { it is FixedInt }

internal enum class IntEncoding { FIXED, VARINT, ZIGZAG }

internal fun resolveIntEncoding(anns: List<Annotation>, config: PackedConfiguration): IntEncoding = when {
    anns.hasFixedInt()   -> IntEncoding.FIXED
    anns.hasVarInt()     -> IntEncoding.ZIGZAG
    anns.hasVarUInt()    -> IntEncoding.VARINT
    config.defaultZigZag -> IntEncoding.ZIGZAG
    config.defaultVarInt -> IntEncoding.VARINT
    else                 -> IntEncoding.FIXED
}
