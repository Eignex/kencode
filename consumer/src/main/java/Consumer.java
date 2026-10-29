import com.eignex.kencode.Base62;
import com.eignex.kencode.Base64;
import com.eignex.kencode.Base64Url;
import com.eignex.kencode.Base85;
import com.eignex.kencode.ByteEncoding;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public final class Consumer {
    public static void main(String[] args) {
        var bytes = "Published encoding 0123456789".getBytes(StandardCharsets.UTF_8);
        ByteEncoding[] codecs = {Base64.Default, Base64Url.INSTANCE, Base85.Default, Base62.INSTANCE};
        for (var codec : codecs) {
            var encoded = codec.encode(bytes, 0, bytes.length);
            if (!Arrays.equals(bytes, codec.decode(encoded))) {
                throw new AssertionError("Published encoding failed to round trip");
            }
        }
        if (!Base64.Default.encode(bytes, 0, bytes.length).equals(java.util.Base64.getEncoder().encodeToString(bytes))) {
            throw new AssertionError("Published Base64 differs from JDK Base64");
        }
        System.out.println("Published Java consumer passed");
    }
}
