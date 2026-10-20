package com.netflix.spinnaker.q.redis

import com.netflix.spinnaker.q.DeadMessageCallback
import com.netflix.spinnaker.q.Message
import com.netflix.spinnaker.q.Queue
import java.time.Clock
import redis.clients.jedis.JedisCluster
import tools.jackson.databind.ObjectMapper
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.KotlinModule

class RedisClusterDeadMessageHandler(
  deadLetterQueueName: String,
  private val jedisCluster: JedisCluster,
  private val clock: Clock
) : DeadMessageCallback {

  private val dlqKey = "{$deadLetterQueueName}.messages"

  private val mapper = JsonMapper.builder()
    .addModule(KotlinModule.Builder().build())
    .build()

  override fun invoke(queue: Queue, message: Message) {
    jedisCluster.use { cluster ->
      val score = clock.instant().toEpochMilli().toDouble()
      cluster.zadd(dlqKey, score, mapper.writeValueAsString(message))
    }
  }
}
