# idempotency-api


### API
<img width="1346" alt="image" src="https://github.com/jerry-ljh/idempotency-api/assets/87708830/16bceb86-0ce7-4656-b2b5-e92be3753733">

API 사용 예시
- interceptor & filter based
```kotlin

@IdempotencyApi
@PostMapping("/post")
fun createPost(@RequestBody body: CreatePostRequest): ResponseEntity<String> {
    ...
}

```



### Executor
<img width="467" alt="image" src="https://github.com/jerry-ljh/idempotency-api/assets/87708830/a62f09c8-5dec-494f-ae12-e1b841fa9f63">

사용 예시
- lambda / aop

```kotlin
@Idempotency(key = POST_CREATE_KEY, expression = "#postRequest.userId + ',' + #postRequest.contents.hashCode()")
fun createPost(postRequest: CreatePostRequest) {
    ...
}


fun createPost(postRequest: CreatePostRequest) {
    val key = "$POST_CREATE_KEY::${postRequest.userId},${postRequest.contents.hashCode()}"
    idempotencyExecutor.execute(key) {  ... }
}

```
