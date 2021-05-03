package com.fasterxml.jackson.module.kotlin.instantiator

import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.MapperFeature
import com.fasterxml.jackson.module.kotlin.SpreadWrapper
import com.fasterxml.jackson.module.kotlin.instantiator.Instantiator.Companion.INT_PRIMITIVE_CLASS
import java.lang.reflect.Constructor
import kotlin.reflect.KFunction
import kotlin.reflect.KParameter

// This class does not support inner constructor.
internal class ConstructorInstantiator<T>(
    kConstructor: KFunction<T>, private val constructor: Constructor<T>
) : Instantiator<T> {
    override val hasValueParameter: Boolean = false
    override val valueParameters: List<KParameter> = kConstructor.parameters
    private val accessible: Boolean = constructor.isAccessible
    private val bucketGenerator = BucketGenerator(valueParameters)
    // This initialization process is heavy and will not be done until it is needed.
    private val localConstructor: Constructor<T> by lazy {
        val lastMaskIndex = valueParameters.size + bucketGenerator.maskSize

        val parameterTypes = constructor.parameterTypes.copyOf(lastMaskIndex + 1).apply {
            (valueParameters.size until lastMaskIndex).forEach { this[it] = INT_PRIMITIVE_CLASS }
            this[lastMaskIndex] = DEFAULT_CONSTRUCTOR_MARKER
        }

        SpreadWrapper.getConstructor(constructor.declaringClass, parameterTypes)
            .apply { isAccessible = true }
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

    override fun call(bucket: ArgumentBucket): T = when (bucket.isFullInitialized()) {
        true -> SpreadWrapper.newInstance(constructor, bucket.values)
        false -> SpreadWrapper.newInstance(localConstructor, bucket.getValuesOnDefault())
    }

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
