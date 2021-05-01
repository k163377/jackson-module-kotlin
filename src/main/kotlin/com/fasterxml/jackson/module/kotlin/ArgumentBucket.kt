package com.fasterxml.jackson.module.kotlin

import kotlin.reflect.KParameter
import kotlin.reflect.jvm.jvmErasure

class BucketGenerator(parameters: List<KParameter>) {
    private val paramSize: Int = parameters.size
    // For Optional and Primitive types, set the initial value because the function cannot be called if the argument is null.
    private val originalValues: Array<Any?> = parameters.map {
        if (it.isOptional) {
            ABSENT_VALUE[it.type.jvmErasure.java]
        } else {
            null
        }
    }.toTypedArray()
    private val originalMasks: IntArray = IntArray((paramSize / Int.SIZE_BITS) + 1) { FILLED_MASK }

    fun generate() = ArgumentBucket(paramSize, originalValues.clone(), originalMasks.clone())

    companion object {
        private const val FILLED_MASK = -1

        private val ABSENT_VALUE: Map<Class<*>, Any> = mapOf(
            Byte::class.javaPrimitiveType!! to Byte.MIN_VALUE,
            Short::class.javaPrimitiveType!! to Short.MIN_VALUE,
            Int::class.javaPrimitiveType!! to Int.MIN_VALUE,
            Long::class.javaPrimitiveType!! to Long.MIN_VALUE,
            Float::class.javaPrimitiveType!! to Float.MIN_VALUE,
            Double::class.javaPrimitiveType!! to Double.MIN_VALUE
        )
    }
}
