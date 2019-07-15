package com.netflix.spinnaker.orca.q

import com.netflix.spinnaker.orca.fixture.pipeline
import com.netflix.spinnaker.orca.fixture.stage
import com.netflix.spinnaker.orca.q.pending.PendingExecutionService
import com.netflix.spinnaker.q.Message
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.verify
import org.assertj.core.api.Assertions
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.given
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.dsl.on
import org.jetbrains.spek.subject.SubjectSpek
import java.util.UUID

object PendingExecutionServiceTest : SubjectSpek<PendingExecutionService>({

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
  val callback = mock<(Message) -> Unit>()

  sequenceOf<Message>(startMessage, restartMessage).forEach { message ->
    describe("enqueueing a ${message.javaClass.simpleName} message") {
      given("the queue is empty") {
        beforeGroup {
          Assertions.assertThat(subject.depth(id)).isZero()
        }

        on("enqueueing the message") {
          subject.enqueue(id, message)

          it("makes the depth 1") {
            Assertions.assertThat(subject.depth(id)).isOne()
          }
        }

        afterGroup { subject.purge(id, callback) }
      }
    }
  }

  describe("popping a message") {
    given("the queue is empty") {
      beforeGroup {
        Assertions.assertThat(subject.depth(id)).isZero()
      }

      on("popping a message") {
        val popped = subject.popOldest(id)

        it("returns null") {
          Assertions.assertThat(popped).isNull()
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
          Assertions.assertThat(popped).isEqualTo(startMessage)
        }

        it("removes the message from the queue") {
          Assertions.assertThat(subject.depth(id)).isZero()
        }
      }

      afterGroup { subject.purge(id, callback) }
    }

    given("multiple messages were enqueued") {
      beforeEachTest {
        subject.enqueue(id, startMessage)
        subject.enqueue(id, restartMessage)
      }

      on("popping the oldest message") {
        val popped = subject.popOldest(id)

        it("returns the oldest message") {
          Assertions.assertThat(popped).isEqualTo(startMessage)
        }

        it("removes the message from the queue") {
          Assertions.assertThat(subject.depth(id)).isOne()
        }
      }

      on("popping the newest message") {
        val popped = subject.popNewest(id)

        it("returns the newest message") {
          Assertions.assertThat(popped).isEqualTo(restartMessage)
        }

        it("removes the message from the queue") {
          Assertions.assertThat(subject.depth(id)).isOne()
        }
      }

      afterEachTest { subject.purge(id, callback) }
    }
  }

  describe("purging the queue") {
    val purgeCallback = mock<(Message) -> Unit>()

    given("there are some messages on the queue") {
      beforeGroup {
        subject.enqueue(id, startMessage)
        subject.enqueue(id, restartMessage)
      }

      on("purging the queue") {
        subject.purge(id, purgeCallback)

        it("makes the queue empty") {
          Assertions.assertThat(subject.depth(id)).isZero()
        }

        it("invokes the callback passing each message") {
          verify(purgeCallback).invoke(startMessage)
          verify(purgeCallback).invoke(restartMessage)
        }
      }

      afterGroup { subject.purge(id, callback) }
    }
  }
})
