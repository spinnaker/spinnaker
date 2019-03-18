package com.netflix.spinnaker.keel.sqs

import com.amazonaws.services.sqs.AmazonSQS
import com.amazonaws.services.sqs.model.DeleteMessageResult
import com.amazonaws.services.sqs.model.Message
import com.amazonaws.services.sqs.model.ReceiveMessageRequest
import com.amazonaws.services.sqs.model.ReceiveMessageResult
import com.netflix.spinnaker.config.SqsProperties
import com.netflix.spinnaker.keel.annealing.ResourceActuator
import com.netflix.spinnaker.keel.api.ResourceName
import com.netflix.spinnaker.keel.api.SPINNAKER_API_V1
import com.netflix.spinnaker.keel.api.randomUID
import com.netflix.spinnaker.keel.serialization.configuredObjectMapper
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import strikt.api.expect
import strikt.assertions.isEqualTo

internal class SqsResourceCheckListenerTests : JUnit5Minutests {

  val messageBody = ResourceCheckMessage("ec2:cluster:prod:ap-south-1:keel", SPINNAKER_API_V1, "cluster")
  val objectMapper = configuredObjectMapper()
  val sqsClient: AmazonSQS = mockk(relaxUnitFun = true)
  val actuator: ResourceActuator = mockk(relaxUnitFun = true)

  data class Fixture(
    val listener: SqsResourceCheckListener,
    val messages: List<Message> = emptyList()
  ) {
    val receiveMessageResults: List<ReceiveMessageResult>
      get() = messages.map {
        ReceiveMessageResult().withMessages(it)
      } + ReceiveMessageResult()
  }

  fun tests() = rootContext<Fixture> {

    fixture {
      Fixture(
        listener = SqsResourceCheckListener(
          sqsClient,
          "queueURL",
          SqsProperties().apply { listenerFibers = 2 },
          objectMapper,
          actuator
        )
      )
    }

    before {
      coEvery { sqsClient.deleteMessage(any()) } returns DeleteMessageResult()
    }

    after {
      listener.onApplicationDown()
      clearMocks(sqsClient, actuator)
    }

    context("actuator succeeds") {
      deriveFixture {
        copy(
          messages = listOf(wrap(messageBody))
        )
      }

      before {
        coEvery {
          sqsClient.receiveMessage(any<ReceiveMessageRequest>())
        } returnsMany receiveMessageResults

        listener.onApplicationUp()
      }

      test("invokes the actuator") {
        coVerify(timeout = 250) {
          actuator
            .checkResource(messageBody.name.let(::ResourceName), messageBody.apiVersion, messageBody.kind)
        }
      }

      test("deletes the message from the queue") {
        coVerify(timeout = 250) {
          sqsClient
            .deleteMessage(withArg {
              expect {
                that(it.queueUrl).isEqualTo("queueURL")
                that(it.receiptHandle).isEqualTo(messages[0].receiptHandle)
              }
            })
        }
      }
    }

    context("can't parse the message") {
      deriveFixture {
        copy(
          messages = listOf(wrap("SOME RANDOM JUNK"), wrap(messageBody))
        )
      }

      before {
        coEvery {
          sqsClient.receiveMessage(any<ReceiveMessageRequest>())
        } returnsMany receiveMessageResults

        listener.onApplicationUp()
      }

      test("goes on to process the valid message") {
        coVerify(timeout = 250) {
          actuator
            .checkResource(messageBody.name.let(::ResourceName), messageBody.apiVersion, messageBody.kind)
        }
      }

      test("deletes the valid message but not the bad one") {
        coVerify(timeout = 250) {
          sqsClient.deleteMessage(match {
            it.receiptHandle == messages[1].receiptHandle
          })
        }
        coVerify(exactly = 0) {
          sqsClient.deleteMessage(match {
            it.receiptHandle == messages[0].receiptHandle
          })
        }
      }
    }

    context("actuator fails") {
      deriveFixture {
        copy(
          messages = listOf(
            wrap(messageBody),
            wrap(messageBody.copy(name = "ec2:security-group:prod:ap-south-1:keel", kind = "security-group"))
          )
        )
      }

      before {
        coEvery {
          sqsClient.receiveMessage(any<ReceiveMessageRequest>())
        } returnsMany receiveMessageResults
        coEvery {
          actuator.checkResource(messageBody.name.let(::ResourceName), messageBody.apiVersion, messageBody.kind)
        } throws IllegalStateException("o noes")

        listener.onApplicationUp()
      }

      test("goes on to process the next message") {
        coVerify(timeout = 250) {
          actuator
            .checkResource(messageBody.name.let(::ResourceName), messageBody.apiVersion, messageBody.kind)
        }
      }

      test("deletes the successfully handled message but not the failed one") {
        coVerify(timeout = 250) {
          sqsClient.deleteMessage(match {
            it.receiptHandle == messages[1].receiptHandle
          })
        }
        coVerify(exactly = 0) {
          sqsClient.deleteMessage(match {
            it.receiptHandle == messages[0].receiptHandle
          })
        }
      }
    }
  }

  private fun wrap(body: Any): Message =
    Message()
      .withBody(objectMapper.writeValueAsString(body))
      .withReceiptHandle(randomUID().toString())
}
