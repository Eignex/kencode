@file:OptIn(ExperimentalTime::class)

package com.eignex.kencode

import kotlinx.serialization.Serializable
import kotlin.time.*

@Serializable
data class SimpleIntsAndBooleans(
    val id: Int,
    val score: Int,
    val active: Boolean,
    val deleted: Boolean
)

@Serializable
data class AllPrimitiveTypes(
    val intVal: Int, val longVal: Long, val shortVal: Short, val byteVal: Byte,
    val floatVal: Float, val doubleVal: Double, val charVal: Char,
    val boolVal: Boolean, val stringVal: String
)

@Serializable
data class AllPrimitiveTypesNullable(
    val intVal: Int?,
    val longVal: Long?,
    val shortVal: Short?,
    val byteVal: Byte?,
    val floatVal: Float?,
    val doubleVal: Double?,
    val charVal: Char?,
    val boolVal: Boolean?,
    val stringVal: String?
)

@Serializable
data class NoBooleansNoNulls(val x: Int, val y: Long, val msg: String)

@Serializable
enum class Status { NEW, IN_PROGRESS, DONE }

@Serializable
data class EnumPayload(
    val id: Int,
    val status: Status,
    val secondaryStatus: Status?
)

@Serializable
data class NullableFieldsPayload(
    val maybeId: Int?,
    val maybeName: String?,
    val maybeScore: Long?,
    val maybeFlag: Boolean?
)

@Serializable
data class NullableBooleansAndNonBooleans(
    val flag1: Boolean?,
    val flag2: Boolean,
    val flag3: Boolean?,
    val count: Int?,
    val label: String
)

@Serializable
data class StringHeavyPayload(
    val a: String,
    val b: String,
    val c: String,
    val d: String?
)

@Serializable
data class VarIntVarUIntPayload(
    @VarInt val signedIntVar: Int,
    @VarInt val signedLongVar: Long,
    @VarUInt val unsignedIntVar: Int,
    @VarUInt val unsignedLongVar: Long,
    val plainInt: Int,
    val plainLong: Long
)

@Serializable
data class UIntInlinePayload(
    val id: Int,
    val first: UInt,
    val second: UInt,
    val active: Boolean
)

@Serializable
data class DurationInlinePayload(
    val label: String,
    val primary: Duration,
    val secondary: Duration?,
    val count: Int
)

@Serializable
data class InstantInlinePayload(
    val first: Instant,
    val second: Instant?,
    val active: Boolean,
    val seq: Int
)

@Serializable
data class Child(val value: Int)

@Serializable
data class Parent(val id: Int, val child: Child)

@Serializable
data class WithList(val id: Int, val items: List<Int>)

@Serializable
data class Grid(val rows: List<List<Int>>)

@Serializable
data class NestedOptionalList(
    val id: Int,
    val tags: List<String>?,
    val scores: List<Int>
)

@Serializable
data class DeepNested(val name: String, val level1: Level1)

@Serializable
data class Level1(val active: Boolean, val level2: Level2?)

@Serializable
data class Level2(val data: String, val matrix: List<List<Int>>)

@Serializable
data class MapHolder(
    val config: Map<String, String>,
    val counts: Map<String, Int>?
)

@Serializable
data class ComplexMap(
    val history: Map<String, List<Int>>,
    val registry: Map<Int, Child>
)

@Serializable
data class MixedBag(
    val id: Int,
    val meta: Map<String, String>?,
    val children: List<Child>,
    val flags: List<Boolean?>
)

@Serializable
data class NullableMap(val data: Map<String, String?>)

@Serializable
data class BooleanFlags63(
    val b01: Boolean,
    val b02: Boolean,
    val b03: Boolean,
    val b04: Boolean,
    val b05: Boolean,
    val b06: Boolean,
    val b07: Boolean,
    val b08: Boolean,
    val b09: Boolean,
    val b10: Boolean,
    val b11: Boolean,
    val b12: Boolean,
    val b13: Boolean,
    val b14: Boolean,
    val b15: Boolean,
    val b16: Boolean,
    val b17: Boolean,
    val b18: Boolean,
    val b19: Boolean,
    val b20: Boolean,
    val b21: Boolean,
    val b22: Boolean,
    val b23: Boolean,
    val b24: Boolean,
    val b25: Boolean,
    val b26: Boolean,
    val b27: Boolean,
    val b28: Boolean,
    val b29: Boolean,
    val b30: Boolean,
    val b31: Boolean,
    val b32: Boolean,
    val b33: Boolean,
    val b34: Boolean,
    val b35: Boolean,
    val b36: Boolean,
    val b37: Boolean,
    val b38: Boolean,
    val b39: Boolean,
    val b40: Boolean,
    val b41: Boolean,
    val b42: Boolean,
    val b43: Boolean,
    val b44: Boolean,
    val b45: Boolean,
    val b46: Boolean,
    val b47: Boolean,
    val b48: Boolean,
    val b49: Boolean,
    val b50: Boolean,
    val b51: Boolean,
    val b52: Boolean,
    val b53: Boolean,
    val b54: Boolean,
    val b55: Boolean,
    val b56: Boolean,
    val b57: Boolean,
    val b58: Boolean,
    val b59: Boolean,
    val b60: Boolean,
    val b61: Boolean,
    val b62: Boolean,
    val b63: Boolean
) {
    constructor(flags: BooleanArray) : this(
        flags[0],
        flags[1],
        flags[2],
        flags[3],
        flags[4],
        flags[5],
        flags[6],
        flags[7],
        flags[8],
        flags[9],
        flags[10],
        flags[11],
        flags[12],
        flags[13],
        flags[14],
        flags[15],
        flags[16],
        flags[17],
        flags[18],
        flags[19],
        flags[20],
        flags[21],
        flags[22],
        flags[23],
        flags[24],
        flags[25],
        flags[26],
        flags[27],
        flags[28],
        flags[29],
        flags[30],
        flags[31],
        flags[32],
        flags[33],
        flags[34],
        flags[35],
        flags[36],
        flags[37],
        flags[38],
        flags[39],
        flags[40],
        flags[41],
        flags[42],
        flags[43],
        flags[44],
        flags[45],
        flags[46],
        flags[47],
        flags[48],
        flags[49],
        flags[50],
        flags[51],
        flags[52],
        flags[53],
        flags[54],
        flags[55],
        flags[56],
        flags[57],
        flags[58],
        flags[59],
        flags[60],
        flags[61],
        flags[62]
    ) {
        require(flags.size == 63) { "Expected 63 flags, got ${flags.size}" }
    }
}

@Serializable
data class BooleanFlags64(
    val b01: Boolean,
    val b02: Boolean,
    val b03: Boolean,
    val b04: Boolean,
    val b05: Boolean,
    val b06: Boolean,
    val b07: Boolean,
    val b08: Boolean,
    val b09: Boolean,
    val b10: Boolean,
    val b11: Boolean,
    val b12: Boolean,
    val b13: Boolean,
    val b14: Boolean,
    val b15: Boolean,
    val b16: Boolean,
    val b17: Boolean,
    val b18: Boolean,
    val b19: Boolean,
    val b20: Boolean,
    val b21: Boolean,
    val b22: Boolean,
    val b23: Boolean,
    val b24: Boolean,
    val b25: Boolean,
    val b26: Boolean,
    val b27: Boolean,
    val b28: Boolean,
    val b29: Boolean,
    val b30: Boolean,
    val b31: Boolean,
    val b32: Boolean,
    val b33: Boolean,
    val b34: Boolean,
    val b35: Boolean,
    val b36: Boolean,
    val b37: Boolean,
    val b38: Boolean,
    val b39: Boolean,
    val b40: Boolean,
    val b41: Boolean,
    val b42: Boolean,
    val b43: Boolean,
    val b44: Boolean,
    val b45: Boolean,
    val b46: Boolean,
    val b47: Boolean,
    val b48: Boolean,
    val b49: Boolean,
    val b50: Boolean,
    val b51: Boolean,
    val b52: Boolean,
    val b53: Boolean,
    val b54: Boolean,
    val b55: Boolean,
    val b56: Boolean,
    val b57: Boolean,
    val b58: Boolean,
    val b59: Boolean,
    val b60: Boolean,
    val b61: Boolean,
    val b62: Boolean,
    val b63: Boolean,
    val b64: Boolean
) {
    constructor(flags: BooleanArray) : this(
        flags[0],
        flags[1],
        flags[2],
        flags[3],
        flags[4],
        flags[5],
        flags[6],
        flags[7],
        flags[8],
        flags[9],
        flags[10],
        flags[11],
        flags[12],
        flags[13],
        flags[14],
        flags[15],
        flags[16],
        flags[17],
        flags[18],
        flags[19],
        flags[20],
        flags[21],
        flags[22],
        flags[23],
        flags[24],
        flags[25],
        flags[26],
        flags[27],
        flags[28],
        flags[29],
        flags[30],
        flags[31],
        flags[32],
        flags[33],
        flags[34],
        flags[35],
        flags[36],
        flags[37],
        flags[38],
        flags[39],
        flags[40],
        flags[41],
        flags[42],
        flags[43],
        flags[44],
        flags[45],
        flags[46],
        flags[47],
        flags[48],
        flags[49],
        flags[50],
        flags[51],
        flags[52],
        flags[53],
        flags[54],
        flags[55],
        flags[56],
        flags[57],
        flags[58],
        flags[59],
        flags[60],
        flags[61],
        flags[62],
        flags[63]
    ) {
        require(flags.size == 64) { "Expected 64 flags, got ${flags.size}" }
    }
}

@Serializable
data class BooleanFlags65(
    val b01: Boolean,
    val b02: Boolean,
    val b03: Boolean,
    val b04: Boolean,
    val b05: Boolean,
    val b06: Boolean,
    val b07: Boolean,
    val b08: Boolean,
    val b09: Boolean,
    val b10: Boolean,
    val b11: Boolean,
    val b12: Boolean,
    val b13: Boolean,
    val b14: Boolean,
    val b15: Boolean,
    val b16: Boolean,
    val b17: Boolean,
    val b18: Boolean,
    val b19: Boolean,
    val b20: Boolean,
    val b21: Boolean,
    val b22: Boolean,
    val b23: Boolean,
    val b24: Boolean,
    val b25: Boolean,
    val b26: Boolean,
    val b27: Boolean,
    val b28: Boolean,
    val b29: Boolean,
    val b30: Boolean,
    val b31: Boolean,
    val b32: Boolean,
    val b33: Boolean,
    val b34: Boolean,
    val b35: Boolean,
    val b36: Boolean,
    val b37: Boolean,
    val b38: Boolean,
    val b39: Boolean,
    val b40: Boolean,
    val b41: Boolean,
    val b42: Boolean,
    val b43: Boolean,
    val b44: Boolean,
    val b45: Boolean,
    val b46: Boolean,
    val b47: Boolean,
    val b48: Boolean,
    val b49: Boolean,
    val b50: Boolean,
    val b51: Boolean,
    val b52: Boolean,
    val b53: Boolean,
    val b54: Boolean,
    val b55: Boolean,
    val b56: Boolean,
    val b57: Boolean,
    val b58: Boolean,
    val b59: Boolean,
    val b60: Boolean,
    val b61: Boolean,
    val b62: Boolean,
    val b63: Boolean,
    val b64: Boolean,
    val b65: Boolean
) {
    constructor(flags: BooleanArray) : this(
        flags[0],
        flags[1],
        flags[2],
        flags[3],
        flags[4],
        flags[5],
        flags[6],
        flags[7],
        flags[8],
        flags[9],
        flags[10],
        flags[11],
        flags[12],
        flags[13],
        flags[14],
        flags[15],
        flags[16],
        flags[17],
        flags[18],
        flags[19],
        flags[20],
        flags[21],
        flags[22],
        flags[23],
        flags[24],
        flags[25],
        flags[26],
        flags[27],
        flags[28],
        flags[29],
        flags[30],
        flags[31],
        flags[32],
        flags[33],
        flags[34],
        flags[35],
        flags[36],
        flags[37],
        flags[38],
        flags[39],
        flags[40],
        flags[41],
        flags[42],
        flags[43],
        flags[44],
        flags[45],
        flags[46],
        flags[47],
        flags[48],
        flags[49],
        flags[50],
        flags[51],
        flags[52],
        flags[53],
        flags[54],
        flags[55],
        flags[56],
        flags[57],
        flags[58],
        flags[59],
        flags[60],
        flags[61],
        flags[62],
        flags[63],
        flags[64]
    ) {
        require(flags.size == 65) { "Expected 65 flags, got ${flags.size}" }
    }
}

@Serializable
data class RecursiveTree(
    val name: String,
    val children: List<RecursiveTree> = emptyList(),
    val metadata: Map<String, RecursiveTree>? = null
)

@Serializable
data class DeepBreadth(
    val branchA: Level1,
    val branchB: Level1,
    val branchC: Level1,
    val rootValue: Int
)

@JvmInline
@Serializable
value class UserId(val id: Long)

@JvmInline
@Serializable
value class Email(val address: String)

@Serializable
data class InlineHeavyPayload(
    val uByte: UByte,
    val uShort: UShort,
    val uInt: UInt,
    val uLong: ULong,
    val userId: UserId,
    val contactEmail: Email?,
    val tags: List<UserId>,
    val timestamp: Instant
)

@Serializable
data class MultiLevelCollections(
    val complexMap: Map<String, Map<Int, List<String?>>>,
    val nestedLists: List<List<List<Int>>>,
    val optionalDeepMap: Map<Int, Map<String, Boolean>?>?
)

@Serializable
sealed class PolymorphicBase {
    @Serializable
    data class Action(val name: String, val priority: Int) : PolymorphicBase()

    @Serializable
    data class Notification(val message: String, val silent: Boolean) :
        PolymorphicBase()

    @Serializable
    object Heartbeat : PolymorphicBase()
}

@Serializable
data class PolymorphicContainer(
    val main: PolymorphicBase,
    val history: List<PolymorphicBase>
)

@Serializable
data class UnannotatedPayload(val x: Int, val y: Long)

@Serializable
data class InversePayload(
    @FixedInt val fixedX: Int,
    @FixedInt val fixedY: Long,
    @FixedInt val fixedUX: UInt,
    @FixedInt val fixedUY: ULong
)
