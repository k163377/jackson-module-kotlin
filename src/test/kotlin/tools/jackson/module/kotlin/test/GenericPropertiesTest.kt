package tools.jackson.module.kotlin.test

import org.junit.jupiter.api.Test

class GenericPropertiesTest {
    data class Dto<T, U : T & Any>(val prop: U)

    @Test
    fun test() { println(Dto::class) }
}
