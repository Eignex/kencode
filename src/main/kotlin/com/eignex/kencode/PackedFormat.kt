package com.eignex.kencode

import kotlinx.serialization.*
import kotlinx.serialization.modules.EmptySerializersModule
import kotlinx.serialization.modules.SerializersModule
import java.io.ByteArrayOutputStream

/**
 * Holds the configuration for a [PackedFormat] instance.
 *
 * @property defaultVarInt When true, all `Int` and `Long` fields without specific annotations are encoded as variable-length integers.
 * @property defaultZigZag When true, all `Int` and `Long` fields without specific annotations are ZigZag encoded to efficiently store small negative numbers.
 */
data class PackedConfiguration(
    val defaultVarInt: Boolean = false,
    val defaultZigZag: Boolean = false
)

/**
 * Compact `BinaryFormat` optimized for small, flat Kotlin data classes.
 *
 * Features:
 * - Booleans and nullability encoded as compact bitmasks in a class header.
 * - Optional varint / zig-zag encoding via `@VarInt` / `@VarUInt` annotations, or globally via [PackedConfiguration].
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
     * Default instance with no default varint/zigzag optimizations applied automatically.
     */
    companion object Default : PackedFormat()

    /**
     * Encodes [value] into a compact binary representation.
     */
    override fun <T> encodeToByteArray(
        serializer: SerializationStrategy<T>, value: T
    ): ByteArray {
        val out = ByteArrayOutputStream()
        val encoder = PackedEncoder(out, configuration, serializersModule)
        encoder.encodeSerializableValue(serializer, value)
        return out.toByteArray()
    }

    /**
     * Decodes [bytes] produced by [PackedFormat] back into an object of type [T].
     */
    override fun <T> decodeFromByteArray(
        deserializer: DeserializationStrategy<T>, bytes: ByteArray
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
     * If true, applies variable-length integer encoding to all `Int` and `Long` fields by default.
     */
    var defaultVarInt: Boolean = false

    /**
     * If true, applies ZigZag encoding to all `Int` and `Long` fields by default.
     * Overrides [defaultVarInt] as ZigZag inherently implies variable-length encoding.
     */
    var defaultZigZag: Boolean = false
}

/**
 * Creates a customized [PackedFormat] instance.
 *
 * ```
 * val format = PackedFormat {
 * defaultZigZag = true
 * serializersModule = myCustomModule
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
        defaultVarInt = from.configuration.defaultVarInt
        defaultZigZag = from.configuration.defaultZigZag
    }
    builder.builderAction()

    return PackedFormat(
        configuration = PackedConfiguration(builder.defaultVarInt, builder.defaultZigZag),
        serializersModule = builder.serializersModule
    )
}
