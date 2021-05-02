package com.fasterxml.jackson.module.kotlin.instantiator

import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.MapperFeature
import com.fasterxml.jackson.module.kotlin.erasedType
import java.lang.reflect.Method
import kotlin.reflect.KFunction
import kotlin.reflect.KParameter
import kotlin.reflect.full.instanceParameter
import kotlin.reflect.full.valueParameters
import kotlin.reflect.jvm.javaMethod

// This class does not support inner constructor.
internal class MethodInstantiator<T>(
    kFunction: KFunction<T>,
    private val method: Method,
    private val instance: Any,
    companionAccessible: Boolean
) : Instantiator<T> {
    override val hasValueParameter: Boolean = true // TODO: fix on support top level function
    override val valueParameters: List<KParameter> = kFunction.valueParameters
    private val accessible: Boolean = companionAccessible && method.isAccessible
    private val bucketGenerator = BucketGenerator(valueParameters)

    init {
        method.isAccessible = true
    }

    // This initialization process is heavy and will not be done until it is needed.
    private val localMethod: Method by lazy {
        method.declaringClass.getDeclaredMethod(
            "${method.name}\$default",
            instance::class.java,
            *method.parameterTypes,
            *Array(bucketGenerator.maskSize) { Int::class.javaPrimitiveType },
            Object::class.java
        )
    }

    override fun checkAccessibility(ctxt: DeserializationContext) {

        if ((!accessible && ctxt.config.isEnabled(MapperFeature.CAN_OVERRIDE_ACCESS_MODIFIERS)) ||
            (accessible && ctxt.config.isEnabled(MapperFeature.OVERRIDE_PUBLIC_ACCESS_MODIFIERS))) {
            return
        }

        throw IllegalAccessException("Cannot access to Method, instead found ${method.name}")
    }

    override fun generateBucket() = bucketGenerator.generate()

    // TODO: use SpreadWrapper
    @Suppress("UNCHECKED_CAST")
    override fun call(bucket: ArgumentBucket) = when (bucket.isFullInitialized()) {
        true -> method.invoke(instance, *bucket.values)
        false -> localMethod.invoke(null, instance, *bucket.getValuesOnDefault())
    } as T
}
