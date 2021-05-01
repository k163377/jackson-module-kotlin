package com.fasterxml.jackson.module.kotlin

import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.MapperFeature
import java.lang.reflect.Constructor
import kotlin.reflect.KFunction
import kotlin.reflect.jvm.javaConstructor

// This class does not support inner constructor.
internal class ConstructorInstantiator<T>(kConstructor: KFunction<T>) : Instantiator<T> {
    private val constructor: Constructor<T> = kConstructor.javaConstructor!!
    private val accessible: Boolean = constructor.isAccessible
    private val bucketGenerator = BucketGenerator(kConstructor.parameters)
    // This initialization process is heavy and will not be done until it is needed.
    private val localConstructor: Constructor<T> by lazy {
        // TODO: Improving efficiency of array generation and use SpreadWrapper
        constructor.declaringClass.getConstructor(
            *constructor.parameterTypes,
            *Array(bucketGenerator.maskSize) { Int::class.javaPrimitiveType },
            DEFAULT_CONSTRUCTOR_MARKER
        ).apply {
            isAccessible = true
        }
    }

    init {
        // Preserve the initial value of Accessibility, and make the entity Accessible.
        constructor.isAccessible = true
    }

    override fun checkAccessibility(ctxt: DeserializationContext) {
        if ((!accessible && ctxt.config.isEnabled(MapperFeature.CAN_OVERRIDE_ACCESS_MODIFIERS)) ||
            (accessible && ctxt.config.isEnabled(MapperFeature.OVERRIDE_PUBLIC_ACCESS_MODIFIERS))) {
            return
        }

        throw IllegalAccessException("Cannot access to Constructor, instead found ${constructor.declaringClass.name}")
    }

    override fun generateBucket() = bucketGenerator.generate()

    // TODO: use SpreadWrapper
    override fun call(bucket: ArgumentBucket) = if (bucket.isFullInitialized())
        constructor.newInstance(*bucket.values)
    else
        localConstructor.newInstance(*bucket.getValuesOnDefault())

    companion object {
        private val DEFAULT_CONSTRUCTOR_MARKER: Class<*> = try {
            Class.forName("kotlin.jvm.internal.DefaultConstructorMarker")
        } catch (ex: ClassNotFoundException) {
            throw IllegalStateException(
                "DefaultConstructorMarker not on classpath. Make sure the Kotlin stdlib is on the classpath."
            )
        }
    }
}
