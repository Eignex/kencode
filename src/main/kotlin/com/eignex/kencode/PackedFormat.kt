import com.eignex.kencode.PackedDecoder
import com.eignex.kencode.PackedEncoder
import kotlinx.serialization.*
import kotlinx.serialization.modules.EmptySerializersModule
import kotlinx.serialization.modules.SerializersModule
import java.io.ByteArrayOutputStream

@OptIn(ExperimentalSerializationApi::class)
object PackedFormat : BinaryFormat {

    override val serializersModule: SerializersModule = EmptySerializersModule()

    override fun <T> encodeToByteArray(
        serializer: SerializationStrategy<T>,
        value: T
    ): ByteArray {
        val out = ByteArrayOutputStream()
        val encoder = PackedEncoder(out)
        encoder.encodeSerializableValue(serializer, value)
        return out.toByteArray()
    }

    override fun <T> decodeFromByteArray(
        deserializer: DeserializationStrategy<T>,
        bytes: ByteArray
    ): T {
        val decoder = PackedDecoder(bytes)
        return decoder.decodeSerializableValue(deserializer)
    }
}
