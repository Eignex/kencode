import com.eignex.kencode.PackedDecoder
import com.eignex.kencode.PackedEncoder
import kotlinx.serialization.*
import kotlinx.serialization.modules.EmptySerializersModule
import kotlinx.serialization.modules.SerializersModule
import java.io.ByteArrayOutputStream

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
object PackedFormat : BinaryFormat {

    override val serializersModule: SerializersModule = EmptySerializersModule()

    /**
     * Encodes [value] into a compact binary representation using [PackedEncoder].
     */
    override fun <T> encodeToByteArray(
        serializer: SerializationStrategy<T>, value: T
    ): ByteArray {
        val out = ByteArrayOutputStream()
        val encoder = PackedEncoder(out)
        encoder.encodeSerializableValue(serializer, value)
        return out.toByteArray()
    }

    /**
     * Decodes [bytes] produced by [PackedFormat] using [PackedDecoder].
     */
    override fun <T> decodeFromByteArray(
        deserializer: DeserializationStrategy<T>, bytes: ByteArray
    ): T {
        val decoder = PackedDecoder(bytes)
        return decoder.decodeSerializableValue(deserializer)
    }
}
