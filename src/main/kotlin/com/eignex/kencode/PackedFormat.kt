import com.eignex.kencode.PackedDecoder
import com.eignex.kencode.PackedEncoder
import kotlinx.serialization.*
import kotlinx.serialization.modules.EmptySerializersModule
import kotlinx.serialization.modules.SerializersModule
import java.io.ByteArrayOutputStream

data class PackedConfiguration(
    val defaultVarInt: Boolean = false,
    val defaultZigZag: Boolean = false
)

/**
 * Compact `BinaryFormat` optimized for small, flat Kotlin data classes.
 *
 * Features:
 * - Booleans and nullability encoded as bitmasks
 * - Optional varint / zig-zag via `@VarInt` / `@VarUInt` annotations
 * - Fixed, deterministic field order based on declaration
 *
 * Limitations:
 * - No nested objects, collections, or maps
 * - No polymorphism
 *
 * For richer models, use `ProtoBuf` (or another [BinaryFormat]) with [com.eignex.kencode.EncodedFormat].
 */
@OptIn(ExperimentalSerializationApi::class)
open class PackedFormat(
    val configuration: PackedConfiguration = PackedConfiguration(),
    override val serializersModule: SerializersModule = EmptySerializersModule()
) : BinaryFormat {

    companion object Default : PackedFormat()

    /**
     * Encodes [value] into a compact binary representation using [PackedEncoder].
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
     * Decodes [bytes] produced by [PackedFormat] using [PackedDecoder].
     */
    override fun <T> decodeFromByteArray(
        deserializer: DeserializationStrategy<T>, bytes: ByteArray
    ): T {
        val decoder = PackedDecoder(bytes, configuration, serializersModule)
        return decoder.decodeSerializableValue(deserializer)
    }
}


class PackedFormatBuilder {
    var serializersModule: SerializersModule = EmptySerializersModule()
    var defaultVarInt: Boolean = false
    var defaultZigZag: Boolean = false
}

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
