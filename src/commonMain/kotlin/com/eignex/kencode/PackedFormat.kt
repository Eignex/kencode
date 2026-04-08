package com.eignex.kencode

import kotlinx.serialization.BinaryFormat
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.modules.EmptySerializersModule
import kotlinx.serialization.modules.SerializersModule

/**
 * Holds the configuration for a [PackedFormat] instance.
 *
 * @property defaultEncoding The encoding applied to all `Int` and `Long` fields that carry no
 *   [PackedType] annotation. Defaults to [IntPacking.DEFAULT] varint encoding.
 */
data class PackedConfiguration(
    val defaultEncoding: IntPacking = IntPacking.DEFAULT
)

/**
 * Compact `BinaryFormat` optimized for small, flat Kotlin data classes.
 *
 * Features:
 * - Booleans and nullability encoded as compact bitmasks in a class header.
 * - Optional varint / zig-zag encoding via `@PackedType` annotations, or globally via [PackedConfiguration].
 * - Fixed, deterministic field order based on declaration.
 *
 * Limitations:
 * - Nested objects and collections are supported, but do not share bitmasks across structural boundaries.
 * - Polymorphism support is limited/unoptimized compared to full-featured formats.
 *
 * Use the [PackedFormat] builder function to create a customized instance.
 *
 * @property configuration The active configuration for default varint/zigzag behaviors.
 * @property serializersModule The module used to resolve contextual and polymorphic serializers.
 */
@OptIn(ExperimentalSerializationApi::class)
open class PackedFormat(
    val configuration: PackedConfiguration = PackedConfiguration(),
    override val serializersModule: SerializersModule = EmptySerializersModule()
) : BinaryFormat {

    /**
     * Default instance using [IntPacking.DEFAULT] (unsigned varint) for all unannotated `Int`/`Long` fields.
     */
    companion object Default : PackedFormat()

    /**
     * Encodes [value] into a compact binary representation.
     */
    override fun <T> encodeToByteArray(
        serializer: SerializationStrategy<T>,
        value: T
    ): ByteArray {
        val out = ByteOutput()
        val encoder = PackedEncoder(out, configuration, serializersModule)
        encoder.encodeSerializableValue(serializer, value)
        return out.toByteArray()
    }

    /**
     * Decodes [bytes] produced by [PackedFormat] back into an object of type [T].
     */
    override fun <T> decodeFromByteArray(
        deserializer: DeserializationStrategy<T>,
        bytes: ByteArray
    ): T {
        val decoder = PackedDecoder(bytes, configuration, serializersModule)
        return decoder.decodeSerializableValue(deserializer)
    }
}

/**
 * Builder for configuring [PackedFormat] instances.
 */
class PackedFormatBuilder {
    /**
     * The module containing contextual and polymorphic serializers.
     */
    var serializersModule: SerializersModule = EmptySerializersModule()

    /**
     * The encoding applied to all `Int` and `Long` fields that carry no [PackedType] annotation.
     * Defaults to [IntPacking.DEFAULT].
     */
    var defaultEncoding: IntPacking = IntPacking.DEFAULT
}

/**
 * Creates a customized [PackedFormat] instance.
 *
 * ```
 * val format = PackedFormat {
 *     defaultEncoding = IntPacking.SIGNED
 *     serializersModule = myCustomModule
 * }
 * ```
 *
 * @param from An existing [PackedFormat] instance to copy defaults from.
 * @param builderAction The configuration block.
 */
fun PackedFormat(
    from: PackedFormat = PackedFormat.Default,
    builderAction: PackedFormatBuilder.() -> Unit
): PackedFormat {
    val builder = PackedFormatBuilder().apply {
        serializersModule = from.serializersModule
        defaultEncoding = from.configuration.defaultEncoding
    }
    builder.builderAction()

    return PackedFormat(
        configuration = PackedConfiguration(builder.defaultEncoding),
        serializersModule = builder.serializersModule
    )
}
