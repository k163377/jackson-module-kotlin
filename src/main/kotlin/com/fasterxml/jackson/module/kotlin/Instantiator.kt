package com.fasterxml.jackson.module.kotlin

import com.fasterxml.jackson.databind.DeserializationContext
import kotlin.reflect.KParameter

internal interface Instantiator<T> {
    val hasInstanceParameter: Boolean
    val valueParameters: List<KParameterCache>
    fun checkAccessibility(ctxt: DeserializationContext)

    fun generateBucket(): ArgumentBucket
    fun callBy(bucket: ArgumentBucket): T

    companion object {
        val INT_PRIMITIVE_CLASS: Class<Int> = Int::class.javaPrimitiveType!!
    }
}
