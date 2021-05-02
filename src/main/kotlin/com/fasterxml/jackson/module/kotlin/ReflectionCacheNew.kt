package com.fasterxml.jackson.module.kotlin

import com.fasterxml.jackson.databind.introspect.AnnotatedConstructor
import com.fasterxml.jackson.databind.introspect.AnnotatedMethod
import com.fasterxml.jackson.databind.introspect.AnnotatedWithParams
import com.fasterxml.jackson.databind.util.LRUMap
import com.fasterxml.jackson.module.kotlin.instantiator.ConstructorInstantiator
import com.fasterxml.jackson.module.kotlin.instantiator.Instantiator
import com.fasterxml.jackson.module.kotlin.instantiator.MethodInstantiator
import java.lang.reflect.Constructor
import java.lang.reflect.Method
import kotlin.reflect.full.extensionReceiverParameter
import kotlin.reflect.full.instanceParameter
import kotlin.reflect.jvm.kotlinFunction

// TODO: Consider whether it is necessary to include it in the ReflectionCache class.
internal class ReflectionCacheNew(reflectionCacheSize: Int) {
    private val javaConstructorToKotlin = LRUMap<Constructor<Any>, ConstructorInstantiator<Any>>(reflectionCacheSize, reflectionCacheSize)
    private val javaMethodToKotlin = LRUMap<Method, MethodInstantiator<*>>(reflectionCacheSize, reflectionCacheSize)

    fun kotlinFromJava(key: Constructor<Any>): ConstructorInstantiator<Any>? = javaConstructorToKotlin.get(key)
        ?: key.kotlinFunction?.let {
            val instantiator = ConstructorInstantiator(it, key)
            javaConstructorToKotlin.putIfAbsent(key, instantiator) ?: instantiator
        }

    fun kotlinFromJava(key: Method): MethodInstantiator<*>? = javaMethodToKotlin.get(key)
        ?: key.kotlinFunction?.takeIf {
            // we shouldn't have an instance or receiver parameter and if we do, just go with default Java-ish behavior
            it.extensionReceiverParameter == null
        }?.let { callable ->
            var companionInstance: Any? = null
            var companionAccessible: Boolean? = null

            callable.instanceParameter!!.type.erasedType().kotlin
                .takeIf { it.isCompanion } // abort, we have some unknown case here
                ?.let { possibleCompanion ->
                    try {
                        possibleCompanion.objectInstance
                    } catch (ex: IllegalAccessException) {
                        // fallback for when an odd access exception happens through Kotlin reflection
                        possibleCompanion.java.enclosingClass.fields
                            .firstOrNull { callable.name == "Companion" }
                            ?.let {
                                companionAccessible = it.isAccessible
                                it.isAccessible = true

                                companionInstance = it.get(null)
                            } ?: throw ex
                    }
                }

            companionInstance?.let {
                MethodInstantiator(callable, key, it, companionAccessible!!).run {
                    javaMethodToKotlin.putIfAbsent(key, this) ?: this
                }
            }
        }

    /*
     * return null if...
     * - can't get kotlinFunction
     * - contains extensionReceiverParameter
     * - instance parameter is not companion object or can't get
     */
    @Suppress("UNCHECKED_CAST")
    fun instantiatorFromJava(_withArgsCreator: AnnotatedWithParams): Instantiator<*>? = when (_withArgsCreator) {
        is AnnotatedConstructor -> kotlinFromJava(_withArgsCreator.annotated as Constructor<Any>)
        is AnnotatedMethod -> kotlinFromJava(_withArgsCreator.annotated as Method)
        else ->
            throw IllegalStateException("Expected a constructor or method to create a Kotlin object, instead found ${_withArgsCreator.annotated.javaClass.name}")
    }
}
