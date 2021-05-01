package com.fasterxml.jackson.module.kotlin

import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.MapperFeature
import java.lang.reflect.Method
import kotlin.reflect.KFunction
import kotlin.reflect.full.valueParameters
import kotlin.reflect.jvm.javaMethod

// This class does not support inner constructor.
internal class MethodInstantiator<T>(kFunction: KFunction<T>) : Instantiator<T> {
    private val method = kFunction.javaMethod!!
    // TODO: add check or fallback
    private val instance = method.declaringClass.kotlin.objectInstance!!
    private val accessible: Boolean = method.isAccessible
    private val bucketGenerator = BucketGenerator(kFunction.valueParameters)

    init {
        method.isAccessible = true
    }

    // This initialization process is heavy and will not be done until it is needed.
    private val localMethod: Method by lazy {
        // TODO: Improving efficiency of array generation and use SpreadWrapper
        method.declaringClass.getDeclaredMethod(
            method.name,
            *method.parameterTypes,
            *Array(bucketGenerator.maskSize) { Int::class.javaPrimitiveType },
            Object::class.java
        ).apply {
            isAccessible = true
        }
    }

    override fun checkAccessibility(ctxt: DeserializationContext) {
        if ((!accessible && ctxt.config.isEnabled(MapperFeature.CAN_OVERRIDE_ACCESS_MODIFIERS)) ||
            (accessible && ctxt.config.isEnabled(MapperFeature.OVERRIDE_PUBLIC_ACCESS_MODIFIERS))) {
            return
        }

        throw IllegalAccessException("Cannot access to Method, instead found ${instance::class.java.name}")
    }

    override fun generateBucket() = bucketGenerator.generate()

    // TODO: use SpreadWrapper
    @Suppress("UNCHECKED_CAST")
    override fun call(bucket: ArgumentBucket) = if (bucket.isFullInitialized()) {
        method.invoke(instance, *bucket.values)
    } else {
        localMethod.invoke(instance, *bucket.getValuesOnDefault())
    } as T
}
