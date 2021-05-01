package com.fasterxml.jackson.module.kotlin

import com.fasterxml.jackson.databind.util.LRUMap
import java.lang.reflect.Constructor
import java.lang.reflect.Method
import kotlin.reflect.jvm.kotlinFunction

// TODO: Streamlining the process of getting an Instantiator by reflection.
// TODO: add extensionReceiverParameter check
internal class ReflectionCacheNew(reflectionCacheSize: Int) {
    private val javaConstructorToKotlin = LRUMap<Constructor<Any>, ConstructorInstantiator<Any>>(reflectionCacheSize, reflectionCacheSize)
    private val javaMethodToKotlin = LRUMap<Method, MethodInstantiator<*>>(reflectionCacheSize, reflectionCacheSize)

    fun kotlinFromJava(key: Constructor<Any>): ConstructorInstantiator<Any>? = javaConstructorToKotlin.get(key)
        ?: key.kotlinFunction?.let {
            val instantiator = ConstructorInstantiator(it)
            javaConstructorToKotlin.putIfAbsent(key, instantiator) ?: instantiator
        }

    fun kotlinFromJava(key: Method): MethodInstantiator<*>? = javaMethodToKotlin.get(key)
        ?: key.kotlinFunction?.let {
            val instantiator = MethodInstantiator(it)
            javaMethodToKotlin.putIfAbsent(key, instantiator) ?: instantiator
        }
}
