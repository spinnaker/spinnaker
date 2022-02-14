package com.netflix.spinnaker.orca.q.pending

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.netflix.spectator.api.NoopRegistry
import com.netflix.spinnaker.config.DualPendingExecutionServiceConfiguration
import com.netflix.spinnaker.kork.jedis.EmbeddedRedis
import com.netflix.spinnaker.orca.api.test.pipeline
import com.netflix.spinnaker.orca.api.test.stage
import com.netflix.spinnaker.orca.q.PendingExecutionServiceTest
import com.netflix.spinnaker.orca.q.RestartStage
import com.netflix.spinnaker.orca.q.StartExecution
import com.netflix.spinnaker.orca.q.redis.pending.RedisPendingExecutionService
import com.netflix.spinnaker.q.Message
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.verify
import java.util.UUID
import org.assertj.core.api.Assertions
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.given
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.dsl.on
import org.jetbrains.spek.subject.SubjectSpek
import org.jetbrains.spek.subject.itBehavesLike

internal object DualPendingExecutionServiceTest : SubjectSpek<DualPendingExecutionService>({

  itBehavesLike(PendingExecutionServiceTest)

  val primaryRedis = EmbeddedRedis.embed()
  val previousRedis = EmbeddedRedis.embed()
  val mapper = ObjectMapper().apply {
    registerModule(KotlinModule.Builder().build())
    registerSubtypes(StartExecution::class.java, RestartStage::class.java)
  }

  val primaryService = RedisPendingExecutionService(primaryRedis.pool, mapper)
  val previousService = RedisPendingExecutionServiceProxy(
    RedisPendingExecutionService(previousRedis.pool, mapper)
  )

  val properties = DualPendingExecutionServiceConfiguration().apply {
    enabled = true
    primaryClass = "com.netflix.spinnaker.orca.q.redis.pending.RedisPendingExecutionService"
    previousClass = "com.netflix.spinnaker.orca.q.pending.RedisPendingExecutionServiceProxy"
  }

  subject {
    DualPendingExecutionService(
      properties,
      listOf(primaryService, previousService),
      NoopRegistry()
    )
  }

  afterGroup {
    primaryRedis.destroy()
    previousRedis.destroy()
  }

  val id = UUID.randomUUID().toString()
  val pipeline = pipeline {
    pipelineConfigId = id
    stage {
      refId = "1"
    }
    stage {
      refId = "2"
      requisiteStageRefIds = setOf("1")
    }
  }

  val startMessage = StartExecution(pipeline)
  val restartMessage = RestartStage(pipeline.stageByRef("2"), "afeldman@netflix.com")
  val callback = mock<(Message) -> Unit>()

  describe("enqueue only writes to primary") {
    given("the queue is empty") {
      beforeGroup {
        Assertions.assertThat(subject.depth(id)).isZero()
      }

      on("enqueue a message") {
        subject.enqueue(id, startMessage)

        it("total queue depth is one") {
          Assertions.assertThat(subject.depth(id)).isEqualTo(1)
        }

        it("previous queue depth is zero") {
          Assertions.assertThat(previousService.depth(id)).isZero()
        }

        it("primary queue depth is one") {
          Assertions.assertThat(primaryService.depth(id)).isEqualTo(1)
        }
      }

      afterGroup { subject.purge(id, callback) }
    }
  }

  describe("pop prioritizes prior") {
    given("both services contain a message") {
      beforeGroup {
        Assertions.assertThat(subject.depth(id)).isZero()
        previousService.enqueue(id, startMessage)
        primaryService.enqueue(id, restartMessage)
        Assertions.assertThat(subject.depth(id)).isEqualTo(2)
      }

      on("pop a message") {
        val message = subject.popOldest(id)

        it("pops from previous") {
          Assertions.assertThat(message).isEqualTo(startMessage)
        }
      }

      on("pop another message") {
        val message = subject.popOldest(id)

        it("pops from primary") {
          Assertions.assertThat(message).isEqualTo(restartMessage)
        }
      }
    }

    afterGroup { subject.purge(id, callback) }
  }

  describe("purge iterates thru both services") {
    val purgeCallback = mock<(Message) -> Unit>()

    given("both services contain a message") {
      beforeGroup {
        Assertions.assertThat(subject.depth(id)).isZero()
        previousService.enqueue(id, startMessage)
        primaryService.enqueue(id, restartMessage)
        Assertions.assertThat(previousService.depth(id)).isEqualTo(1)
        Assertions.assertThat(primaryService.depth(id)).isEqualTo(1)
        Assertions.assertThat(subject.depth(id)).isEqualTo(2)
      }

      on("purge the queue") {
        subject.purge(id, purgeCallback)

        it("both services are purged") {
          Assertions.assertThat(previousService.depth(id)).isZero()
          Assertions.assertThat(primaryService.depth(id)).isZero()
          Assertions.assertThat(subject.depth(id)).isZero()
        }

        it("both services invoke callback") {
          verify(purgeCallback).invoke(startMessage)
          verify(purgeCallback).invoke(restartMessage)
        }
      }
    }
  }
})

class RedisPendingExecutionServiceProxy(
  private val service: PendingExecutionService
) : PendingExecutionService {
  override fun enqueue(pipelineConfigId: String, message: Message) =
    service.enqueue(pipelineConfigId, message)

  override fun popOldest(pipelineConfigId: String): Message? =
    service.popOldest(pipelineConfigId)

  override fun popNewest(pipelineConfigId: String): Message? =
    service.popNewest(pipelineConfigId)

  override fun purge(pipelineConfigId: String, callback: (Message) -> Unit) =
    service.purge(pipelineConfigId, callback)

  override fun depth(pipelineConfigId: String): Int =
    service.depth(pipelineConfigId)

  override fun pendingIds(): List<String> =
    service.pendingIds()
}
