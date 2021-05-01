package com.fasterxml.jackson.module.kotlin

import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.MapperFeature
import java.lang.reflect.Method
import kotlin.reflect.KFunction
import kotlin.reflect.KParameter
import kotlin.reflect.full.instanceParameter
import kotlin.reflect.full.valueParameters
import kotlin.reflect.jvm.javaMethod

// This class does not support inner constructor.
internal class MethodInstantiator<T>(kFunction: KFunction<T>) : Instantiator<T> {
    override val hasValueParameter: Boolean = true // TODO: fix on support top level function
    override val valueParameters: List<KParameter> = kFunction.valueParameters
    private val method = kFunction.javaMethod!!
    // TODO: add fallback option
    private val instance: Any?
    private val accessible: Boolean = method.isAccessible
    private val bucketGenerator = BucketGenerator(valueParameters)

    init {
        method.isAccessible = true

        val possibleCompanion = kFunction.instanceParameter!!.type.erasedType().kotlin

        instance = if (!possibleCompanion.isCompanion) {
            null
        } else {
            try {
                possibleCompanion.objectInstance!!
            } catch (ex: IllegalAccessException) {
                val companionField = possibleCompanion.java.enclosingClass.fields.firstOrNull { it.name == "Companion" }
                    ?: throw ex
                companionField.isAccessible = true
                companionField.get(null) ?: throw ex
            }
        }
    }

    // This initialization process is heavy and will not be done until it is needed.
    private val localMethodPair: Pair<Method, Boolean> by lazy {
        // TODO: Consider whether this method of acquisition is appropriate.
        val method = method.declaringClass.declaredMethods
            .first { it.name == "${method.name}\$default" }
            .apply { isAccessible = true }
        val isStatic = method.parameterTypes
            .firstOrNull()
            ?.let { instance != null && it == instance::class.java } ?: false

        method to isStatic
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
    override fun call(bucket: ArgumentBucket) = when {
        bucket.isFullInitialized() -> method.invoke(instance, *bucket.values)
        localMethodPair.second -> localMethodPair.first.invoke(null, instance, *bucket.getValuesOnDefault())
        else -> localMethodPair.first.invoke(instance, *bucket.getValuesOnDefault())
    } as T
}
