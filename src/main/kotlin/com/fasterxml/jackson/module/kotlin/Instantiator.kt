package com.fasterxml.jackson.module.kotlin

import com.fasterxml.jackson.databind.DeserializationContext
import kotlin.reflect.KParameter

internal interface Instantiator<T> {
    val valueParameters: List<KParameter>
    fun checkAccessibility(ctxt: DeserializationContext)

    fun generateBucket(): ArgumentBucket
    fun call(bucket: ArgumentBucket): T
}
