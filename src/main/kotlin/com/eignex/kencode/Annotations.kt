@file:OptIn(ExperimentalSerializationApi::class)

package com.eignex.kencode

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialInfo

@SerialInfo
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.RUNTIME)
annotation class VarInt

@SerialInfo
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.RUNTIME)
annotation class ZigZag

fun List<Annotation>.hasVarInt(): Boolean = any { it is VarInt }
fun List<Annotation>.hasZigZag(): Boolean = any { it is ZigZag }
