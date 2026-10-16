package com.netflix.spinnaker.orca.q.redis.pending

import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.KotlinModule
import com.netflix.spinnaker.kork.jedis.EmbeddedRedis
import com.netflix.spinnaker.orca.q.PendingExecutionServiceTest
import com.netflix.spinnaker.orca.q.RestartStage
import com.netflix.spinnaker.orca.q.StartExecution
import org.jetbrains.spek.subject.SubjectSpek
import org.jetbrains.spek.subject.itBehavesLike

internal object RedisPendingExecutionServiceTest : SubjectSpek<RedisPendingExecutionService> ({

  itBehavesLike(PendingExecutionServiceTest)

  val redis = EmbeddedRedis.embed()
  val mapper = JsonMapper.builder()
    .addModule(KotlinModule.Builder().build())
    .registerSubtypes(StartExecution::class.java, RestartStage::class.java)
    .build()

  subject { RedisPendingExecutionService(redis.pool, mapper) }

  afterGroup {
    redis.destroy()
  }
})
