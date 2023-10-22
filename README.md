# idempotency-api


사용 예시
- lambda / aop

<img width="645" alt="image" src="https://github.com/jerry-ljh/idempotency-api/assets/87708830/eccec8d6-2c42-4b78-a415-cb68a45a0f32">


```kotlin
fun createPost(postRequest: CreatePostRequest) {
    val key = "$POST_CREATE_KEY::${postRequest.userId},${postRequest.contents.hashCode()}"
    idempotencyExecutor.execute(key) {  ... }
}

```


```kotlin
@Idempotency(key = POST_CREATE_KEY, expression = "#postRequest.userId + ',' + #postRequest.contents.hashCode()")
fun createPost(postRequest: CreatePostRequest) {
    ...
}

```

API 사용 예시
- interceptor & filter based
```kotlin

@IdempotencyApi
@PostMapping("/post")
fun createPost(@RequestBody body: CreatePostRequest): ResponseEntity<String> {
    ...
}

```
