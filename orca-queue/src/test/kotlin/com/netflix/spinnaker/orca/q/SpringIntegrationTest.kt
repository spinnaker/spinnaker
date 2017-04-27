/*
 * Copyright 2017 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.orca.q

import com.netflix.appinfo.InstanceInfo.InstanceStatus.*
import com.netflix.discovery.StatusChangeEvent
import com.netflix.spectator.api.Counter
import com.netflix.spectator.api.Id
import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.config.QueueConfiguration
import com.netflix.spinnaker.kork.eureka.RemoteStatusChangedEvent
import com.netflix.spinnaker.orca.ExecutionStatus.SUCCEEDED
import com.netflix.spinnaker.orca.ExecutionStatus.TERMINAL
import com.netflix.spinnaker.orca.TaskResult
import com.netflix.spinnaker.orca.batch.exceptions.DefaultExceptionHandler
import com.netflix.spinnaker.orca.pipeline.StageDefinitionBuilder
import com.netflix.spinnaker.orca.pipeline.TaskNode.Builder
import com.netflix.spinnaker.orca.pipeline.model.Execution
import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import com.netflix.spinnaker.orca.pipeline.model.Stage
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import com.netflix.spinnaker.orca.pipeline.persistence.jedis.JedisExecutionRepository
import com.netflix.spinnaker.orca.pipeline.util.ContextParameterProcessor
import com.netflix.spinnaker.orca.pipeline.util.StageNavigator
import com.netflix.spinnaker.orca.q.handler.shouldEqual
import com.netflix.spinnaker.orca.test.redis.EmbeddedRedisConfiguration
import com.nhaarman.mockito_kotlin.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.springframework.beans.factory.DisposableBean
import org.springframework.beans.factory.InitializingBean
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.PropertyPlaceholderAutoConfiguration
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner
import java.lang.Thread.sleep
import java.time.Duration
import java.util.concurrent.atomic.AtomicBoolean

@RunWith(SpringJUnit4ClassRunner::class)
@ContextConfiguration(classes = arrayOf(TestConfig::class))
class SpringIntegrationTest {

  @Autowired lateinit var queue: Queue
  @Autowired lateinit var runner: QueueExecutionRunner
  @Autowired lateinit var repository: ExecutionRepository
  @Autowired lateinit var dummyTask: DummyTask
  @Autowired lateinit var context: ConfigurableApplicationContext

  @Before fun discoveryUp() {
    context.publishEvent(RemoteStatusChangedEvent(StatusChangeEvent(STARTING, UP)))
  }

  @After fun discoveryDown() {
    context.publishEvent(RemoteStatusChangedEvent(StatusChangeEvent(UP, OUT_OF_SERVICE)))
  }

  @After fun resetMocks() = reset(dummyTask)

  @Test fun `can run a simple pipeline`() {
    val pipeline = pipeline {
      application = "spinnaker"
      stage {
        refId = "1"
        type = "dummy"
      }
    }
    repository.store(pipeline)

    whenever(dummyTask.timeout) doReturn 2000L
    whenever(dummyTask.execute(any())) doReturn TaskResult.SUCCEEDED

    context.runToCompletion(pipeline, runner::start)

    repository.retrievePipeline(pipeline.id).status shouldEqual SUCCEEDED
  }

  @Test fun `can skip stages`() {
    val pipeline = pipeline {
      application = "spinnaker"
      stage {
        refId = "1"
        type = "dummy"
        context["stageEnabled"] = mapOf(
          "type" to "expression",
          "expression" to "false"
        )
      }
    }
    repository.store(pipeline)

    context.runToCompletion(pipeline, runner::start)

    repository.retrievePipeline(pipeline.id).status shouldEqual SUCCEEDED

    verify(dummyTask, never()).execute(any())
  }

  @Test fun `pipeline fails if a task fails`() {
    val pipeline = pipeline {
      application = "spinnaker"
      stage {
        refId = "1"
        type = "dummy"
      }
    }
    repository.store(pipeline)

    whenever(dummyTask.execute(any())) doReturn TaskResult(TERMINAL)

    context.runToCompletion(pipeline, runner::start)

    repository.retrievePipeline(pipeline.id).status shouldEqual TERMINAL
  }

  @Test fun `parallel stages that fail cancel other branches`() {
    val pipeline = pipeline {
      application = "spinnaker"
      stage {
        refId = "1"
        type = "dummy"
      }
      stage {
        refId = "2a1"
        type = "dummy"
        requisiteStageRefIds = listOf("1")
      }
      stage {
        refId = "2a2"
        type = "dummy"
        requisiteStageRefIds = listOf("2a1")
      }
      stage {
        refId = "2b"
        type = "dummy"
        requisiteStageRefIds = listOf("1")
      }
      stage {
        refId = "3"
        type = "dummy"
        requisiteStageRefIds = listOf("2a2", "2b")
      }
    }
    repository.store(pipeline)

    whenever(dummyTask.execute(argThat { getRefId() == "2a1" })) doReturn TaskResult(TERMINAL)
    whenever(dummyTask.execute(argThat { getRefId() != "2a1" })) doReturn TaskResult.SUCCEEDED

    context.runToCompletion(pipeline, runner::start)

    argumentCaptor<Stage<Pipeline>>().apply {
      verify(dummyTask, atLeastOnce()).execute(capture())
      allValues.map { it.refId }.toSet() shouldEqual setOf("1", "2a1", "2b")
    }

    repository.retrievePipeline(pipeline.id).status shouldEqual TERMINAL
  }

  @Test fun `stages set to allow failure will proceed in spite of errors`() {
    val pipeline = pipeline {
      application = "spinnaker"
      stage {
        refId = "1"
        type = "dummy"
      }
      stage {
        refId = "2a1"
        type = "dummy"
        requisiteStageRefIds = listOf("1")
        context["continuePipeline"] = true
      }
      stage {
        refId = "2a2"
        type = "dummy"
        requisiteStageRefIds = listOf("2a1")
      }
      stage {
        refId = "2b"
        type = "dummy"
        requisiteStageRefIds = listOf("1")
      }
      stage {
        refId = "3"
        type = "dummy"
        requisiteStageRefIds = listOf("2a2", "2b")
      }
    }
    repository.store(pipeline)

    whenever(dummyTask.timeout) doReturn 2000L
    whenever(dummyTask.execute(argThat { getRefId() == "2a1" })) doReturn TaskResult(TERMINAL)
    whenever(dummyTask.execute(argThat { getRefId() != "2a1" })) doReturn TaskResult.SUCCEEDED

    context.runToCompletion(pipeline, runner::start)

    argumentCaptor<Stage<Pipeline>>().apply {
      verify(dummyTask, atLeastOnce()).execute(capture())
      allValues.map { it.refId }.toSet() shouldEqual setOf("1", "2a1", "2a2", "2b", "3")
    }

    repository.retrievePipeline(pipeline.id).status shouldEqual SUCCEEDED
  }

  @Test fun `stages set to allow failure but fail the pipeline will run to completion but then mark the pipeline failed`() {
    val pipeline = pipeline {
      application = "spinnaker"
      stage {
        refId = "1"
        type = "dummy"
      }
      stage {
        refId = "2a1"
        type = "dummy"
        requisiteStageRefIds = listOf("1")
        context["continuePipeline"] = true
        context["completeOtherBranchesThenFail"] = true
      }
      stage {
        refId = "2a2"
        type = "dummy"
        requisiteStageRefIds = listOf("2a1")
      }
      stage {
        refId = "2b"
        type = "dummy"
        requisiteStageRefIds = listOf("1")
      }
      stage {
        refId = "3"
        type = "dummy"
        requisiteStageRefIds = listOf("2a2", "2b")
      }
    }
    repository.store(pipeline)

    whenever(dummyTask.timeout) doReturn 2000L
    whenever(dummyTask.execute(argThat { getRefId() == "2a1" })) doReturn TaskResult(TERMINAL)
    whenever(dummyTask.execute(argThat { getRefId() != "2a1" })) doReturn TaskResult.SUCCEEDED

    context.runToCompletion(pipeline, runner::start)

    argumentCaptor<Stage<Pipeline>>().apply {
      verify(dummyTask, atLeastOnce()).execute(capture())
      allValues.map { it.refId }.toSet() shouldEqual setOf("1", "2a1", "2a2", "2b", "3")
    }

    repository.retrievePipeline(pipeline.id).status shouldEqual TERMINAL
  }
}

@Configuration
@Import(
  PropertyPlaceholderAutoConfiguration::class,
  QueueConfiguration::class,
  EmbeddedRedisConfiguration::class,
  JedisExecutionRepository::class,
  StageNavigator::class
)
open class TestConfig {
  @Bean open fun registry(): Registry = mock {
    on { createId(any<String>()) }.doReturn(mock<Id>())
    on { counter(any<Id>()) }.doReturn(mock<Counter>())
  }

  @Bean open fun dummyTask(): DummyTask = mock {
    on { timeout } doReturn Duration.ofMinutes(2).toMillis()
  }

  @Bean open fun dummyStage(): StageDefinitionBuilder = object : StageDefinitionBuilder {
    override fun <T : Execution<T>> taskGraph(stage: Stage<T>, builder: Builder) {
      builder.withTask("dummy", DummyTask::class.java)
    }

    override fun getType() = "dummy"
  }

  @Bean open fun stupidPretendScheduler(queueProcessor: QueueProcessor): Any =
    object : InitializingBean, DisposableBean {
      private val running = AtomicBoolean(false)

      override fun afterPropertiesSet() {
        running.set(true)
        Thread(Runnable {
          while (running.get()) {
            queueProcessor.pollOnce()
            sleep(10)
          }
        }).start()
      }

      override fun destroy() {
        running.set(false)
      }
    }

  @Bean open fun currentInstanceId() = "localhost"

  @Bean open fun contextParameterProcessor() = ContextParameterProcessor()

  @Bean open fun defaultExceptionHandler() = DefaultExceptionHandler()
}

