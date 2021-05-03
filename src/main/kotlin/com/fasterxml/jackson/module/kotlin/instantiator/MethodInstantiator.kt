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

    private enum class ProcessingMode {
        Instance, ParameterTypes, Mask, Marker
    }

    // This initialization process is heavy and will not be done until it is needed.
    private val localMethod: Method by lazy {
        var processingMode = ProcessingMode.Instance
        var innerIndex = 0
        val parameterTypes = method.parameterTypes
        // argument size = parameterSize + maskSize + instanceSize(= 1) + markerSize(= 1)
        val argumentTypes = Array(valueParameters.size + bucketGenerator.maskSize + 2) {
            when(processingMode) {
                ProcessingMode.Instance -> {
                    processingMode = ProcessingMode.ParameterTypes
                    instance::class.java
                }
                ProcessingMode.ParameterTypes -> {
                    val next = parameterTypes[innerIndex]
                    innerIndex++
                    if (innerIndex == parameterTypes.size) {
                        processingMode = ProcessingMode.Mask
                        innerIndex = 0
                    }

                    next
                }
                ProcessingMode.Mask -> {
                    innerIndex++
                    if (innerIndex == bucketGenerator.maskSize) {
                        processingMode = ProcessingMode.Marker
                    }
                    Int::class.javaPrimitiveType
                }
                ProcessingMode.Marker -> Object::class.java
            }
        }

        instance::class.java.getDeclaredMethod("${method.name}\$default", *argumentTypes)
            .apply { isAccessible = true }
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
