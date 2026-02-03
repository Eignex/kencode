@file:OptIn(ExperimentalTime::class)

package com.eignex.kencode

import kotlinx.serialization.Serializable
import kotlin.time.Duration
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@Serializable
data class SimpleIntsAndBooleans(val id: Int, val score: Int, val active: Boolean, val deleted: Boolean)

@Serializable
data class AllPrimitiveTypes(
    val intVal: Int, val longVal: Long, val shortVal: Short, val byteVal: Byte,
    val floatVal: Float, val doubleVal: Double, val charVal: Char,
    val boolVal: Boolean, val stringVal: String
)

@Serializable
data class AllPrimitiveTypesNullable(
    val intVal: Int?, val longVal: Long?, val shortVal: Short?, val byteVal: Byte?,
    val floatVal: Float?, val doubleVal: Double?, val charVal: Char?,
    val boolVal: Boolean?, val stringVal: String?
)

@Serializable
data class NoBooleansNoNulls(val x: Int, val y: Long, val msg: String)

@Serializable
enum class Status { NEW, IN_PROGRESS, DONE }

@Serializable
data class EnumPayload(val id: Int, val status: Status, val secondaryStatus: Status?)

@Serializable
data class NullableFieldsPayload(
    val maybeId: Int?, val maybeName: String?, val maybeScore: Long?, val maybeFlag: Boolean?
)

@Serializable
data class NullableBooleansAndNonBooleans(
    val flag1: Boolean?, val flag2: Boolean, val flag3: Boolean?, val count: Int?, val label: String
)

@Serializable
data class StringHeavyPayload(val a: String, val b: String, val c: String, val d: String?)

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
data class UIntInlinePayload(val id: Int, val first: UInt, val second: UInt, val active: Boolean)

@Serializable
data class DurationInlinePayload(val label: String, val primary: Duration, val secondary: Duration?, val count: Int)

@Serializable
data class InstantInlinePayload(val first: Instant, val second: Instant?, val active: Boolean, val seq: Int)

@Serializable
data class Child(val value: Int)

@Serializable
data class Parent(val id: Int, val child: Child)

@Serializable
data class WithList(val id: Int, val items: List<Int>)

@Serializable
data class Grid(val rows: List<List<Int>>)

@Serializable
data class NestedOptionalList(val id: Int, val tags: List<String>?, val scores: List<Int>)

@Serializable
data class DeepNested(val name: String, val level1: Level1)

@Serializable
data class Level1(val active: Boolean, val level2: Level2?)

@Serializable
data class Level2(val data: String, val matrix: List<List<Int>>)

@Serializable
data class MapHolder(val config: Map<String, String>, val counts: Map<String, Int>?)

@Serializable
data class ComplexMap(val history: Map<String, List<Int>>, val registry: Map<Int, Child>)

@Serializable
data class MixedBag(val id: Int, val meta: Map<String, String>?, val children: List<Child>, val flags: List<Boolean?>)

@Serializable
data class NullableMap(val data: Map<String, String?>)
