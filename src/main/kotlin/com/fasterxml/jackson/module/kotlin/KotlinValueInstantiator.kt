package com.fasterxml.jackson.module.kotlin

import com.fasterxml.jackson.databind.BeanDescription
import com.fasterxml.jackson.databind.DeserializationConfig
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.deser.SettableBeanProperty
import com.fasterxml.jackson.databind.deser.ValueInstantiator
import com.fasterxml.jackson.databind.deser.ValueInstantiators
import com.fasterxml.jackson.databind.deser.impl.NullsAsEmptyProvider
import com.fasterxml.jackson.databind.deser.impl.PropertyValueBuffer
import com.fasterxml.jackson.databind.deser.std.StdValueInstantiator
import kotlin.reflect.KParameter
import kotlin.reflect.jvm.javaType

internal class KotlinValueInstantiator(
    src: StdValueInstantiator,
    private val cache: ReflectionCache,
    private val cacheNew: ReflectionCacheNew,
    private val nullToEmptyCollection: Boolean,
    private val nullToEmptyMap: Boolean,
    private val nullIsSameAsDefault: Boolean,
    private val strictNullChecks: Boolean
) : StdValueInstantiator(src) {
    @Suppress("UNCHECKED_CAST")
    override fun createFromObjectWith(
        ctxt: DeserializationContext,
        props: Array<out SettableBeanProperty>,
        buffer: PropertyValueBuffer
    ): Any? {
        val instantiator: Instantiator<*> = cacheNew.instantiatorFromJava(_withArgsCreator)
            ?: return super.createFromObjectWith(ctxt, props, buffer) // we cannot reflect this method so do the default Java-ish behavior

        val bucket = instantiator.generateBucket()

        instantiator.valueParameters.forEachIndexed { idx, paramDef ->
            val jsonProp = props[idx]
            val isMissing = !buffer.hasParameter(jsonProp)

            if (isMissing && paramDef.isOptional) {
                return@forEachIndexed
            }

            var paramVal = if (!isMissing || paramDef.isPrimitive || jsonProp.hasInjectableValueId()) {
                val tempParamVal = buffer.getParameter(jsonProp)
                if (nullIsSameAsDefault && tempParamVal == null && paramDef.isOptional) {
                    return@forEachIndexed
                }
                tempParamVal
            } else {
                // trying to get suitable "missing" value provided by deserializer
                jsonProp.valueDeserializer?.getNullValue(ctxt)
            }

            if (paramVal == null && ((nullToEmptyCollection && jsonProp.type.isCollectionLikeType) || (nullToEmptyMap && jsonProp.type.isMapLikeType))) {
                paramVal = NullsAsEmptyProvider(jsonProp.valueDeserializer).getNullValue(ctxt)
            }

            val isMissingAndRequired = paramVal == null && isMissing && jsonProp.isRequired
            if (isMissingAndRequired ||
                (!paramDef.isGenericTypeVar && paramVal == null && !paramDef.isMarkedNullable)) {
                throw MissingKotlinParameterException(
                    parameter = paramDef.rawValue,
                    processor = ctxt.parser,
                    msg = "Instantiation of ${this.valueTypeDesc} value failed for JSON property ${jsonProp.name} due to missing (therefore NULL) value for creator parameter ${paramDef.name} which is a non-nullable type"
                ).wrapWithPath(this.valueClass, jsonProp.name)
            }

            if (strictNullChecks && paramVal != null && paramDef.isInnerTypeMarkedNullable == false) {
                val containsNull: Boolean = when {
                    paramDef.isArray -> (paramVal as Array<*>).any { it == null }
                    paramDef.isCollection -> (paramVal as Collection<*>).any { it == null }
                    paramDef.isMap -> (paramVal as Map<*, *>).any { it.value == null }
                    else -> false
                }

                if (containsNull) {
                    throw MissingKotlinParameterException(
                        parameter = paramDef.rawValue,
                        processor = ctxt.parser,
                        msg = "Instantiation of ${paramDef.itemType} ${paramDef.paramTypeLabel} failed for JSON property ${jsonProp.name} due to null value in a ${paramDef.paramTypeLabel} that does not allow null values"
                    ).wrapWithPath(this.valueClass, jsonProp.name)
                }
            }

            bucket[idx] = paramVal
        }

        // TODO: Is it necessary to call them differently? Direct execution will perform better.
        return if (bucket.isFullInitialized() && !instantiator.hasInstanceParameter) {
            // we didn't do anything special with default parameters, do a normal call
            super.createFromObjectWith(ctxt, bucket.values)
        } else {
            instantiator.checkAccessibility(ctxt)
            instantiator.callBy(bucket)
        }
    }

    private fun KParameter.isPrimitive(): Boolean {
        return when (val javaType = type.javaType) {
            is Class<*> -> javaType.isPrimitive
            else -> false
        }
    }

    private fun SettableBeanProperty.hasInjectableValueId(): Boolean = injectableValueId != null
}

internal class KotlinInstantiators(
    private val cache: ReflectionCache,
    private val cacheNew: ReflectionCacheNew,
    private val nullToEmptyCollection: Boolean,
    private val nullToEmptyMap: Boolean,
    private val nullIsSameAsDefault: Boolean,
    private val strictNullChecks: Boolean
) : ValueInstantiators {
    override fun findValueInstantiator(
        deserConfig: DeserializationConfig,
        beanDescriptor: BeanDescription,
        defaultInstantiator: ValueInstantiator
    ): ValueInstantiator {
        return if (beanDescriptor.beanClass.isKotlinClass()) {
            if (defaultInstantiator is StdValueInstantiator) {
                KotlinValueInstantiator(
                    defaultInstantiator,
                    cache,
                    cacheNew,
                    nullToEmptyCollection,
                    nullToEmptyMap,
                    nullIsSameAsDefault,
                    strictNullChecks
                )
            } else {
                // TODO: return defaultInstantiator and let default method parameters and nullability go unused?  or die with exception:
                throw IllegalStateException("KotlinValueInstantiator requires that the default ValueInstantiator is StdValueInstantiator")
            }
        } else {
            defaultInstantiator
        }
    }
}
