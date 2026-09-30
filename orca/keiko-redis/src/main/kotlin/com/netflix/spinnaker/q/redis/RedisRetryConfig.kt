package com.netflix.spinnaker.q.redis

data class RedisRetryConfig(
  var maxAttempts: Int = 3,
  var backoffMs: Long = 100,
  var exponentialBackoff: Boolean = true
)
