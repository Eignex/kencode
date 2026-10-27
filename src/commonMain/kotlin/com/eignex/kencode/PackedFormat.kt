package com.eignex.kencode

import kotlinx.serialization.BinaryFormat
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.modules.EmptySerializersModule
import kotlinx.serialization.modules.SerializersModule

/** @property defaultEncoding Encoding for `Int` and `Long` fields without a [PackedType]. */
data class PackedConfiguration(val defaultEncoding: IntPacking = IntPacking.DEFAULT)

/**
 * Compact [BinaryFormat] for Kotlin classes.
 *
 * Booleans, null markers, and enum ordinals share one bit header per class, merged with those of
 * non-nullable nested classes. Boolean and enum lists are bit-packed. Integers use varint unless
 * set otherwise with [PackedType] or [PackedConfiguration]. Fields are written in declaration
 * order, so the wire layout depends on the schema, including enum entry counts. Polymorphism
 * works but is not optimized.
 *
 * @property configuration Integer encoding defaults.
 * @property serializersModule Module for contextual and polymorphic serializers.
 */
@OptIn(ExperimentalSerializationApi::class)
open class PackedFormat(
    val configuration: PackedConfiguration = PackedConfiguration(),
    override val serializersModule: SerializersModule = EmptySerializersModule(),
) : BinaryFormat {

    /** Instance with varint integers and no serializers module. */
    companion object Default : PackedFormat()

    override fun <T> encodeToByteArray(serializer: SerializationStrategy<T>, value: T): ByteArray {
        val out = ByteOutput()
        val encoder = PackedEncoder(out, configuration, serializersModule)
        encoder.encodeSerializableValue(serializer, value)
        return out.toByteArray()
    }

    /** @throws IllegalArgumentException if [bytes] is malformed or has trailing data. */
    override fun <T> decodeFromByteArray(deserializer: DeserializationStrategy<T>, bytes: ByteArray): T {
        val decoder = PackedDecoder(bytes, configuration, serializersModule)
        val value = decoder.decodeSerializableValue(deserializer)
        require(decoder.position == bytes.size) {
            "Unexpected trailing data: decoded ${decoder.position} of ${bytes.size} bytes"
        }
        return value
    }
}

/** Builder for [PackedFormat]. */
class PackedFormatBuilder {
    /** Module for contextual and polymorphic serializers. */
    var serializersModule: SerializersModule = EmptySerializersModule()

    /** Encoding for `Int` and `Long` fields without a [PackedType]. */
    var defaultEncoding: IntPacking = IntPacking.DEFAULT
}

/**
 * Creates a [PackedFormat].
 *
 * ```
 * val format = PackedFormat {
 *     defaultEncoding = IntPacking.SIGNED
 *     serializersModule = myCustomModule
 * }
 * ```
 *
 * @param from Instance to copy settings from.
 * @param builderAction Configuration block.
 */
fun PackedFormat(
    from: PackedFormat = PackedFormat.Default,
    builderAction: PackedFormatBuilder.() -> Unit,
): PackedFormat {
    val builder = PackedFormatBuilder().apply {
        serializersModule = from.serializersModule
        defaultEncoding = from.configuration.defaultEncoding
    }
    builder.builderAction()

    return PackedFormat(
        configuration = PackedConfiguration(builder.defaultEncoding),
        serializersModule = builder.serializersModule,
    )
}
