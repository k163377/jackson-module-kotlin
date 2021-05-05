package com.fasterxml.jackson.module.kotlin

import java.lang.reflect.TypeVariable
import kotlin.reflect.KClass
import kotlin.reflect.KParameter
import kotlin.reflect.KType
import kotlin.reflect.KTypeProjection
import kotlin.reflect.full.isSuperclassOf
import kotlin.reflect.jvm.javaType

// parameter reflection cache for read
class KParameterCache(val rawValue: KParameter) {
    // TODO: It looks like the whole area around type and primitive decisions could be optimized.
    val type = rawValue.type
    private val javaType = type.javaType

    // for common read
    val isOptional: Boolean = rawValue.isOptional
    val isPrimitive: Boolean = when (javaType) {
        is Class<*> -> javaType.isPrimitive
        else -> false
    }
    val isGenericTypeVar: Boolean = javaType is TypeVariable<*>
    val isMarkedNullable: Boolean = type.isMarkedNullable

    // for strictNullChecks
    private val clazz: Class<*> by lazy { type.erasedType() }
    private val kClazz: KClass<*> by lazy { clazz.kotlin }
    private val typeArguments: List<KTypeProjection> by lazy { type.arguments }
    val isArray: Boolean by lazy { clazz.isArray }
    val isCollection: Boolean by lazy { Collection::class.isSuperclassOf(kClazz) }
    val isMap: Boolean by lazy { Map::class.isSuperclassOf(kClazz) }

    val itemType: KType? by lazy {
        when {
            isArray || isCollection -> typeArguments.getOrNull(0)?.type
            isMap -> typeArguments.getOrNull(1)?.type
            else -> null
        }
    }
    val isInnerTypeMarkedNullable: Boolean? by lazy { itemType?.isMarkedNullable }

    // for error label
    val name: String? by lazy { rawValue.name }
    val paramTypeLabel: String? by lazy {
        when {
            isArray -> "array"
            isCollection -> "collection"
            isMap -> "map"
            else -> null
        }
    }
}
