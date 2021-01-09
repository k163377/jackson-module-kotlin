package com.fasterxml.jackson.module.kotlin

import kotlin.reflect.KParameter

internal class ArgumentBucket(private val parameters: List<KParameter>) : Map<KParameter, Any?> {
    val valueArray: Array<Any?> = arrayOfNulls(parameters.size)
    private val initializationStatuses: BooleanArray = BooleanArray(parameters.size)
    private var count = 0

    class Entry internal constructor(
            override val key: KParameter,
            override var value: Any?
    ) : Map.Entry<KParameter, Any?>

    operator fun set(key: KParameter, value: Any?): Any? {
        return valueArray[key.index].apply {
            valueArray[key.index] = value

            if (!initializationStatuses[key.index]) {
                initializationStatuses[key.index] = true
                count++
            }
        }
    }

    fun isFullInitialized(): Boolean = parameters.size == count

    override val entries: Set<Map.Entry<KParameter, Any?>>
        get() = valueArray.withIndex()
                .filter { (i, _) -> initializationStatuses[i] }
                .map { (i, arg) -> Entry(parameters[i], arg) }
                .toSet()
    override val keys: Set<KParameter>
        get() = parameters
                .filterIndexed { i, _ -> initializationStatuses[i] }
                .toSet()
    override val size: Int
        get() = count
    override val values: Collection<Any?>
        get() = valueArray.filterIndexed { i, _ -> initializationStatuses[i] }

    override fun containsKey(key: KParameter): Boolean = initializationStatuses[key.index]

    override fun containsValue(value: Any?): Boolean = valueArray.withIndex()
            .any { (i, arg) -> initializationStatuses[i] && value == arg }

    override fun get(key: KParameter): Any? = valueArray[key.index]

    override fun isEmpty(): Boolean = count == 0
}
