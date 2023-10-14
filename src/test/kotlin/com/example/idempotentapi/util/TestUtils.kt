package com.example.idempotentapi.util

import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionException

fun callAsync(thread: Int, request: () -> Any?): List<Any?> {
    return List(thread) { CompletableFuture.supplyAsync { request() } }
        .map {
            try {
                it.join()
            } catch (e: CompletionException) {
                e.cause!!
            }
        }
}