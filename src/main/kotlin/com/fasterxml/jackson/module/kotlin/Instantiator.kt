package com.fasterxml.jackson.module.kotlin

import com.fasterxml.jackson.databind.DeserializationContext

internal interface Instantiator<T> {
    fun checkAccessibility(ctxt: DeserializationContext)

    fun generateBucket(): ArgumentBucket
    fun call(bucket: ArgumentBucket): T
}
