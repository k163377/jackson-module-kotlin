package com.fasterxml.jackson.module.kotlin.instantiator

import com.fasterxml.jackson.databind.DeserializationContext
import kotlin.reflect.KParameter

internal interface Instantiator<T> {
    val hasValueParameter: Boolean
    val valueParameters: List<KParameter>
    fun checkAccessibility(ctxt: DeserializationContext)

    fun generateBucket(): ArgumentBucket
    fun call(bucket: ArgumentBucket): T

    companion object {
        val INT_PRIMITIVE_CLASS: Class<Int> = Int::class.javaPrimitiveType!!
    }
}
