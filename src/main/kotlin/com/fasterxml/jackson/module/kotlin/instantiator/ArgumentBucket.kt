package com.fasterxml.jackson.module.kotlin.instantiator

import kotlin.reflect.KParameter
import kotlin.reflect.jvm.jvmErasure

class BucketGenerator(parameters: List<KParameter>) {
    private val paramSize: Int = parameters.size
    val maskSize = (paramSize / Int.SIZE_BITS) + 1
    // For Optional and Primitive types, set the initial value because the function cannot be called if the argument is null.
    private val originalValues: Array<Any?> = Array(paramSize) {
        val param = parameters[it]

        if (param.isOptional) {
            ABSENT_VALUE[param.type.jvmErasure.java]
        } else {
            null
        }
    }
    private val originalMasks: IntArray = IntArray(maskSize) { FILLED_MASK }

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

class ArgumentBucket(
    private val paramSize: Int,
    val values: Array<Any?>,
    private val masks: IntArray
) {
    private var initializedCount: Int = 0

    private fun getMaskAddress(index: Int): Pair<Int, Int> = (index / Int.SIZE_BITS) to (index % Int.SIZE_BITS)

    // This is a method equivalent to put of MutableMap.
    operator fun set(index: Int, value: Any?): Any? {
        val maskAddress = getMaskAddress(index)

        val updatedMask = masks[maskAddress.first] and BIT_FLAGS[maskAddress.second]

        return if (updatedMask != masks[maskAddress.first]) {
            values[index] = value
            masks[maskAddress.first] = updatedMask
            initializedCount++

            null
        } else {
            values[index].apply { values[index] = value }
        }
    }

    fun isFullInitialized(): Boolean = initializedCount == paramSize

    // returns arrayOf(*values, *masks, null)
    fun getValuesOnDefault(): Array<Any?> = values.copyOf(values.size + masks.size + 1).apply {
        masks.forEachIndexed { i, mask ->
            this[values.size + i] = mask
        }
    }

    companion object {
        private val BIT_FLAGS: List<Int> = IntArray(Int.SIZE_BITS) { (1 shl it).inv() }.asList()
    }
}
