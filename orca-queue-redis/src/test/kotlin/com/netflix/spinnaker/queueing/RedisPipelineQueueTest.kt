/*
 * Copyright 2018 Netflix, Inc.
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

package com.netflix.spinnaker.queueing

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.netflix.spinnaker.kork.jedis.EmbeddedRedis
import com.netflix.spinnaker.orca.fixture.pipeline
import com.netflix.spinnaker.orca.fixture.stage
import com.netflix.spinnaker.orca.q.RestartStage
import com.netflix.spinnaker.orca.q.StartExecution
import com.netflix.spinnaker.orca.queueing.RedisPipelineQueue
import com.netflix.spinnaker.q.Message
import com.nhaarman.mockito_kotlin.*
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.*
import java.util.*

internal object RedisPipelineQueueTest : Spek({

  lateinit var redis: EmbeddedRedis
  val mapper = ObjectMapper().apply {
    registerModule(KotlinModule())
    registerSubtypes(StartExecution::class.java, RestartStage::class.java)
  }
  lateinit var subject: RedisPipelineQueue

  beforeGroup {
    redis = EmbeddedRedis.embed()
    subject = RedisPipelineQueue(redis.pool, mapper)
  }

  afterGroup {
    redis.destroy()
  }

  fun flushAll() {
    redis.pool.resource.use { it.flushAll() }
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
  val restartMessage = RestartStage(pipeline.stageByRef("2"), "fzlem@netflix.com")

  sequenceOf<Message>(startMessage, restartMessage).forEach { message ->
    describe("enqueueing a ${message.javaClass.simpleName} message") {
      given("the queue is empty") {
        beforeGroup {
          assertThat(subject.depth(id)).isZero()
        }

        on("enqueueing the message") {
          subject.enqueue(id, message)

          it("makes the depth 1") {
            assertThat(subject.depth(id)).isOne()
          }
        }

        afterGroup(::flushAll)
      }
    }
    }

    describe("popping a message") {
      given("the queue is empty") {
        beforeGroup {
          assertThat(subject.depth(id)).isZero()
        }

        on("popping a message") {
          val popped = subject.popOldest(id)

          it("returns null") {
            assertThat(popped).isNull()
          }
        }
      }

      given("a message was enqueued") {
        beforeGroup {
          subject.enqueue(id, startMessage)
        }

        on("popping a message") {
          val popped = subject.popOldest(id)

          it("returns the message") {
            assertThat(popped).isEqualTo(startMessage)
          }

          it("removes the message from the queue") {
            assertThat(subject.depth(id)).isZero()
          }
        }

        afterGroup(::flushAll)
      }

      given("multiple messages were enqueued") {
        beforeEachTest {
          subject.enqueue(id, startMessage)
          subject.enqueue(id, restartMessage)
        }

        on("popping the oldest message") {
          val popped = subject.popOldest(id)

          it("returns the oldest message") {
            assertThat(popped).isEqualTo(startMessage)
          }

          it("removes the message from the queue") {
            assertThat(subject.depth(id)).isOne()
          }
        }

        on("popping the newest message") {
          val popped = subject.popNewest(id)

          it("returns the newest message") {
            assertThat(popped).isEqualTo(restartMessage)
          }

          it("removes the message from the queue") {
            assertThat(subject.depth(id)).isOne()
          }
        }

        afterEachTest(::flushAll)
      }
    }

  describe("purging the queue") {
    val callback = mock<(Message) -> Unit>()

    given("there are some messages on the queue") {
      beforeGroup {
        subject.enqueue(id, startMessage)
        subject.enqueue(id, restartMessage)
      }

      on("purging the queue") {
        subject.purge(id, callback)

        it("makes the queue empty") {
          assertThat(subject.depth(id)).isZero()
        }

        it("invokes the callback passing each message") {
          verify(callback).invoke(startMessage)
          verify(callback).invoke(restartMessage)
        }
      }

      afterGroup(::flushAll)
    }
  }
})
