package com.fasterxml.jackson.module.kotlin

interface Instantiator<T> {
    fun generateBucket(): ArgumentBucket
    fun call(bucket: ArgumentBucket): T
}
