package com.example.idempotentapi.component

import java.io.Serializable

class IdempotencyResult(data: Any? = null): Serializable {

    companion object {
        fun unitTypeWrapping(data : Any?): Any? {
            return if(data is Unit) UnitWrapper() else data
        }

        fun unitTypeUnWrapping(data: Any?): Any? {
            return if(data is UnitWrapper) Unit else data
        }
    }

    val data: Any? = unitTypeWrapping(data)
        get() = unitTypeUnWrapping(field)
}

class UnitWrapper: Serializable