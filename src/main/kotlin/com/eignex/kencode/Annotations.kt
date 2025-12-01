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

fun List<Annotation>.hasVarInt(): Boolean = any { it is VarInt }
fun List<Annotation>.hasVarUInt(): Boolean = any { it is VarUInt }
