package com.eignex.kencode

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.serializer
import kotlin.test.*

@OptIn(ExperimentalSerializationApi::class)
class PackedContextTest {

    // --- ClassBitmask ---

    @Test
    fun `ClassBitmask no booleans or nullables`() {
        val bitmask = ClassBitmask(NoBooleansNoNulls.serializer().descriptor)
        assertEquals(0, bitmask.boolCount)
        assertEquals(0, bitmask.nullCount)
        assertEquals(0, bitmask.totalCount)
    }

    @Test
    fun `ClassBitmask counts boolean fields`() {
        // SimpleIntsAndBooleans(id: Int, score: Int, active: Boolean, deleted: Boolean)
        val bitmask =
            ClassBitmask(SimpleIntsAndBooleans.serializer().descriptor)
        assertEquals(2, bitmask.boolCount)
        assertEquals(0, bitmask.nullCount)
        assertEquals(2, bitmask.totalCount)
    }

    @Test
    fun `ClassBitmask boolean lookup maps field indices`() {
        // fields: 0=id, 1=score, 2=active, 3=deleted
        val bitmask =
            ClassBitmask(SimpleIntsAndBooleans.serializer().descriptor)
        assertEquals(-1, bitmask.booleanPos(0))
        assertEquals(-1, bitmask.booleanPos(1))
        assertEquals(0, bitmask.booleanPos(2))
        assertEquals(1, bitmask.booleanPos(3))
    }

    @Test
    fun `ClassBitmask counts nullable fields`() {
        // Level1(active: Boolean, level2: Level2?)
        val bitmask = ClassBitmask(Level1.serializer().descriptor)
        assertEquals(1, bitmask.boolCount)
        assertEquals(1, bitmask.nullCount)
        assertEquals(2, bitmask.totalCount)
    }

    @Test
    fun `ClassBitmask nullable lookup maps field indices`() {
        // Level1: fields 0=active, 1=level2?
        val bitmask = ClassBitmask(Level1.serializer().descriptor)
        assertEquals(-1, bitmask.nullablePos(0))
        assertEquals(0, bitmask.nullablePos(1))
    }

    @Test
    fun `ClassBitmask nullable boolean contributes both bool and null bits`() {
        // NullableBooleansAndNonBooleans(flag1: Boolean?, flag2: Boolean, flag3: Boolean?, count: Int?, label: String)
        // booleans: flag1?, flag2, flag3? → boolCount=3
        // nullables: flag1?, flag3?, count? → nullCount=3
        val bitmask =
            ClassBitmask(NullableBooleansAndNonBooleans.serializer().descriptor)
        assertEquals(3, bitmask.boolCount)
        assertEquals(3, bitmask.nullCount)
        assertEquals(6, bitmask.totalCount)
        // bool positions: flag1?→0, flag2→1, flag3?→2, count→-1, label→-1
        assertEquals(0, bitmask.booleanPos(0))
        assertEquals(1, bitmask.booleanPos(1))
        assertEquals(2, bitmask.booleanPos(2))
        assertEquals(-1, bitmask.booleanPos(3))
        assertEquals(-1, bitmask.booleanPos(4))
        // null positions: flag1?→0, flag2→-1, flag3?→1, count?→2, label→-1
        assertEquals(0, bitmask.nullablePos(0))
        assertEquals(-1, bitmask.nullablePos(1))
        assertEquals(1, bitmask.nullablePos(2))
        assertEquals(2, bitmask.nullablePos(3))
        assertEquals(-1, bitmask.nullablePos(4))
    }

    @Test
    fun `ClassBitmask pos returns -1 for out-of-range index`() {
        val bitmask = ClassBitmask(NoBooleansNoNulls.serializer().descriptor)
        assertEquals(-1, bitmask.booleanPos(99))
        assertEquals(-1, bitmask.nullablePos(99))
    }

    @Test
    fun `ClassBitmask writeInlineBitmask produces no output when no bits`() {
        val bitmask = ClassBitmask(NoBooleansNoNulls.serializer().descriptor)
        val out = ByteOutput()
        bitmask.writeInlineBitmask(BooleanArray(0), BooleanArray(0), out)
        assertEquals(0, out.toByteArray().size)
    }

    @Test
    fun `ClassBitmask writeInlineBitmask packs booleans then nullables`() {
        // Level1: 1 bool bit (active) then 1 null bit (level2)
        // active=true, level2_null=true → bit0=1, bit1=1 → 0b00000011 = 3
        val bitmask = ClassBitmask(Level1.serializer().descriptor)
        val out = ByteOutput()
        bitmask.writeInlineBitmask(
            booleanArrayOf(true),
            booleanArrayOf(true),
            out
        )
        val bytes = out.toByteArray()
        assertEquals(1, bytes.size)
        assertEquals(3, bytes[0].toInt() and 0xFF)
    }

    @Test
    fun `ClassBitmask writeInlineBitmask all false produces zero byte`() {
        val bitmask = ClassBitmask(Level1.serializer().descriptor)
        val out = ByteOutput()
        bitmask.writeInlineBitmask(
            booleanArrayOf(false),
            booleanArrayOf(false),
            out
        )
        val bytes = out.toByteArray()
        assertEquals(1, bytes.size)
        assertEquals(0, bytes[0].toInt() and 0xFF)
    }

    @Test
    fun `ClassBitmask writeInlineBitmask spans multiple bytes for large counts`() {
        // SimpleIntsAndBooleans: 2 booleans → 1 byte
        // Use BooleanFlags64 which has 64 booleans → 8 bytes
        val bitmask = ClassBitmask(BooleanFlags64.serializer().descriptor)
        assertEquals(64, bitmask.boolCount)
        val out = ByteOutput()
        bitmask.writeInlineBitmask(
            BooleanArray(64) { it == 0 },
            BooleanArray(0),
            out
        )
        val bytes = out.toByteArray()
        assertEquals(8, bytes.size)
        assertEquals(1, bytes[0].toInt() and 0xFF)  // only bit 0 set
        for (i in 1..7) assertEquals(0, bytes[i].toInt() and 0xFF)
    }

    // --- HeaderContext ---

    @Test
    fun `HeaderContext reserve returns sequential start indices`() {
        val ctx = HeaderContext()
        assertEquals(0, ctx.reserve(3))
        assertEquals(3, ctx.reserve(5))
        assertEquals(8, ctx.reserve(1))
    }

    @Test
    fun `HeaderContext toByteArray is empty when nothing reserved`() {
        assertEquals(0, HeaderContext().toByteArray().size)
    }

    @Test
    fun `HeaderContext set and toByteArray packs booleans then nulls`() {
        val ctx = HeaderContext()
        val start = ctx.reserve(4)
        // booleans=[true, false], nulls=[false, true]
        // bit0=true, bit1=false, bit2=false, bit3=true → 0b00001001 = 9
        ctx.set(start, booleanArrayOf(true, false), booleanArrayOf(false, true))
        val bytes = ctx.toByteArray()
        assertEquals(1, bytes.size)
        assertEquals(9, bytes[0].toInt() and 0xFF)
    }

    @Test
    fun `HeaderContext set all false produces zero byte`() {
        val ctx = HeaderContext()
        ctx.reserve(8)
        ctx.set(0, BooleanArray(8), BooleanArray(0))
        val bytes = ctx.toByteArray()
        assertEquals(1, bytes.size)
        assertEquals(0, bytes[0].toInt() and 0xFF)
    }

    @Test
    fun `HeaderContext multiple reserves and sets combine into one header`() {
        // Simulate two nested classes each reserving 2 bits
        val ctx = HeaderContext()
        val start1 = ctx.reserve(2)
        val start2 = ctx.reserve(2)
        ctx.set(
            start1,
            booleanArrayOf(true),
            booleanArrayOf(false)
        )   // bit0=1, bit1=0
        ctx.set(
            start2,
            booleanArrayOf(false),
            booleanArrayOf(true)
        )   // bit2=0, bit3=1
        // 0b00001001 = 9
        val bytes = ctx.toByteArray()
        assertEquals(1, bytes.size)
        assertEquals(9, bytes[0].toInt() and 0xFF)
    }

    @Test
    fun `HeaderContext load returns bytes consumed`() {
        assertEquals(0, HeaderContext().load(byteArrayOf(), 0, 0))
        assertEquals(1, HeaderContext().load(byteArrayOf(0xFF.toByte()), 0, 8))
        assertEquals(
            2,
            HeaderContext().load(byteArrayOf(0xFF.toByte(), 0x00), 0, 9)
        )
    }

    @Test
    fun `HeaderContext load and read roundtrip`() {
        // byte 0b00001101 = 13 → bits: true, false, true, true, false, false, false, false
        val ctx = HeaderContext()
        ctx.load(byteArrayOf(13), 0, 5)
        assertEquals(true, ctx.read())
        assertEquals(false, ctx.read())
        assertEquals(true, ctx.read())
        assertEquals(true, ctx.read())
        assertEquals(false, ctx.read())
    }

    @Test
    fun `HeaderContext read throws when exhausted`() {
        val ctx = HeaderContext()
        ctx.load(byteArrayOf(1), 0, 2)
        ctx.read(); ctx.read()
        assertFailsWith<IllegalArgumentException> { ctx.read() }
    }

    @Test
    fun `HeaderContext set then load roundtrip`() {
        // Encode via set/toByteArray, decode via load/read
        val encoder = HeaderContext()
        val s1 = encoder.reserve(2)
        val s2 = encoder.reserve(2)
        encoder.set(s1, booleanArrayOf(true), booleanArrayOf(false))
        encoder.set(s2, booleanArrayOf(false), booleanArrayOf(true))
        val bytes = encoder.toByteArray()

        val decoder = HeaderContext()
        decoder.load(bytes, 0, 4)
        assertEquals(true, decoder.read())  // class1 bool
        assertEquals(false, decoder.read())  // class1 null
        assertEquals(false, decoder.read())  // class2 bool
        assertEquals(true, decoder.read())  // class2 null
    }

    // --- countAllBits ---

    @Test
    fun `countAllBits class with no booleans or nullables`() {
        assertEquals(0, countAllBits(NoBooleansNoNulls.serializer().descriptor))
    }

    @Test
    fun `countAllBits class with booleans only`() {
        // SimpleIntsAndBooleans: 2 boolean fields
        assertEquals(
            2,
            countAllBits(SimpleIntsAndBooleans.serializer().descriptor)
        )
    }

    @Test
    fun `countAllBits class with boolean and nullable`() {
        // Level1(active: Boolean, level2: Level2?) → 1 bool + 1 null = 2
        assertEquals(2, countAllBits(Level1.serializer().descriptor))
    }

    @Test
    fun `countAllBits recurses into non-nullable nested class`() {
        // DeepNested(name: String, level1: Level1)
        // level1 is non-nullable CLASS → recurse: countAllBits(Level1) = 2
        assertEquals(2, countAllBits(DeepNested.serializer().descriptor))
    }

    @Test
    fun `countAllBits does not recurse into nullable nested class`() {
        // Level1(active: Boolean, level2: Level2?)
        // level2 is nullable → only contributes its own null-marker bit (no recursion)
        // Level2 has 0 bits, so if recursion happened the result would still be 2.
        // Use NullableFieldsPayload which has maybeFlag: Boolean? (→ 1 bool + 1 null)
        // and no non-nullable nested classes. Total = 5 nullables + 1 bool = 6.
        assertEquals(
            6,
            countAllBits(NullableFieldsPayload.serializer().descriptor)
        )
    }

    @Test
    fun `countAllBits sums bits across multiple non-nullable nested classes`() {
        // DeepBreadth(branchA: Level1, branchB: Level1, branchC: Level1, rootValue: Int)
        // 3 × countAllBits(Level1) = 6
        assertEquals(6, countAllBits(DeepBreadth.serializer().descriptor))
    }

    @Test
    fun `countAllBits returns 0 for list descriptor`() {
        val listDesc = ListSerializer(serializer<Int>()).descriptor
        assertEquals(0, countAllBits(listDesc))
    }

    @Test
    fun `countAllBits returns 0 for class with no bits even when deeply nested`() {
        // Parent(id: Int, child: Child) — Child(value: Int) has no bits
        assertEquals(0, countAllBits(Parent.serializer().descriptor))
    }

    // --- shouldMergeChildCtx ---

    @Test
    fun `shouldMergeChildCtx true for non-nullable nested class`() {
        // DeepNested: level1 at index 1 is a non-nullable Level1 CLASS
        assertTrue(
            shouldMergeChildCtx(
                parentIsMergedKind = true,
                parentIsCollection = false,
                parentDescriptor = DeepNested.serializer().descriptor,
                fieldIndex = 1,
                childDescriptor = Level1.serializer().descriptor,
            )
        )
    }

    @Test
    fun `shouldMergeChildCtx false when parent is not merged kind`() {
        assertFalse(
            shouldMergeChildCtx(
                parentIsMergedKind = false,
                parentIsCollection = false,
                parentDescriptor = DeepNested.serializer().descriptor,
                fieldIndex = 1,
                childDescriptor = Level1.serializer().descriptor,
            )
        )
    }

    @Test
    fun `shouldMergeChildCtx false when parent is a collection`() {
        assertFalse(
            shouldMergeChildCtx(
                parentIsMergedKind = true,
                parentIsCollection = true,
                parentDescriptor = DeepNested.serializer().descriptor,
                fieldIndex = 1,
                childDescriptor = Level1.serializer().descriptor,
            )
        )
    }

    @Test
    fun `shouldMergeChildCtx false when field index is negative`() {
        assertFalse(
            shouldMergeChildCtx(
                parentIsMergedKind = true,
                parentIsCollection = false,
                parentDescriptor = DeepNested.serializer().descriptor,
                fieldIndex = -1,
                childDescriptor = Level1.serializer().descriptor,
            )
        )
    }

    @Test
    fun `shouldMergeChildCtx false when field is nullable`() {
        // Level1: level2 at index 1 is Level2? (nullable)
        assertFalse(
            shouldMergeChildCtx(
                parentIsMergedKind = true,
                parentIsCollection = false,
                parentDescriptor = Level1.serializer().descriptor,
                fieldIndex = 1,
                childDescriptor = Level2.serializer().descriptor,
            )
        )
    }

    @Test
    fun `shouldMergeChildCtx false when child is an inline class`() {
        // InlineHeavyPayload: userId at index 4 is UserId (non-nullable inline value class)
        assertFalse(
            shouldMergeChildCtx(
                parentIsMergedKind = true,
                parentIsCollection = false,
                parentDescriptor = InlineHeavyPayload.serializer().descriptor,
                fieldIndex = 4,
                childDescriptor = UserId.serializer().descriptor,
            )
        )
    }

    @Test
    fun `shouldMergeChildCtx false when child is not a class or object`() {
        // WithList: items at index 1 is List<Int>
        val listDesc = ListSerializer(serializer<Int>()).descriptor
        assertFalse(
            shouldMergeChildCtx(
                parentIsMergedKind = true,
                parentIsCollection = false,
                parentDescriptor = WithList.serializer().descriptor,
                fieldIndex = 1,
                childDescriptor = listDesc,
            )
        )
    }
}
