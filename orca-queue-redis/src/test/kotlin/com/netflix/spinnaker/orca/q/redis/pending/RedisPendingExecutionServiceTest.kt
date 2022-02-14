package com.netflix.spinnaker.orca.q.redis.pending

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.netflix.spinnaker.kork.jedis.EmbeddedRedis
import com.netflix.spinnaker.orca.q.PendingExecutionServiceTest
import com.netflix.spinnaker.orca.q.RestartStage
import com.netflix.spinnaker.orca.q.StartExecution
import org.jetbrains.spek.subject.SubjectSpek
import org.jetbrains.spek.subject.itBehavesLike

internal object RedisPendingExecutionServiceTest : SubjectSpek<RedisPendingExecutionService> ({

  itBehavesLike(PendingExecutionServiceTest)

  val redis = EmbeddedRedis.embed()
  val mapper = ObjectMapper().apply {
    registerModule(KotlinModule.Builder().build())
    registerSubtypes(StartExecution::class.java, RestartStage::class.java)
  }

  subject { RedisPendingExecutionService(redis.pool, mapper) }

  afterGroup {
    redis.destroy()
  }
})
