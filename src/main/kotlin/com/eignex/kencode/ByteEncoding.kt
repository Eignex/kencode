package com.eignex.kencode

/**
 * Abstraction for bidirectional byte–text encodings.
 *
 * A `ByteEncoding` converts raw binary data into an ASCII-safe string
 * representation, and back. Implementations include:
 *
 * - `Base36` / `Base62` (`BaseRadix`-based)
 * - `Base64` / `Base64UrlSafe`
 * - `Base85`
 *
 * Contract:
 * - `encode` must produce a deterministic, reversible representation.
 * - `decode` must reject invalid input via `IllegalArgumentException`.
 * - All implementations are pure and thread-safe.
 *
 * Usage:
 * ```
 * val encoded = Base62.encode(bytes)
 * val decoded = Base62.decode(encoded)
 * ```
 */
interface ByteEncoding {

    /**
     * Encode the given byte range `[offset, offset + length)` into a text
     * representation. Throws [IllegalArgumentException] on invalid offset or
     * length.
     */
    fun encode(
        input: ByteArray, offset: Int = 0, length: Int = input.size - offset
    ): String

    /**
     * Decode an encoded string back into the original bytes. Throws
     * [IllegalArgumentException] the input contains characters not in the
     * encoding alphabet or the input is structurally invalid for the encoding.
     */
    fun decode(input: CharSequence): ByteArray
}
