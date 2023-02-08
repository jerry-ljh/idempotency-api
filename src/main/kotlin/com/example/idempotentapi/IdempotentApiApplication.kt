package com.example.idempotentapi

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class IdempotentApiApplication

fun main(args: Array<String>) {
	runApplication<IdempotentApiApplication>(*args)
}
