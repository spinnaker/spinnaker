package com.netflix.spinnaker.keel.sqs

import com.amazonaws.services.sqs.AmazonSQS
import com.amazonaws.services.sqs.model.DeleteMessageRequest
import com.amazonaws.services.sqs.model.Message
import com.amazonaws.services.sqs.model.ReceiveMessageRequest
import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.netflix.spinnaker.config.SqsProperties
import com.netflix.spinnaker.keel.activation.ApplicationDown
import com.netflix.spinnaker.keel.activation.ApplicationUp
import com.netflix.spinnaker.keel.annealing.ResourceActuator
import com.netflix.spinnaker.keel.api.ResourceName
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import java.lang.Thread.currentThread
import kotlin.coroutines.CoroutineContext

internal class SqsResourceCheckListener(
  private val sqsClient: AmazonSQS,
  private val queueUrl: String,
  private val sqsProperties: SqsProperties,
  private val objectMapper: ObjectMapper,
  private val actuator: ResourceActuator
) : CoroutineScope {
  private val supervisorJob: Job = SupervisorJob()

  override val coroutineContext: CoroutineContext
    get() = Dispatchers.IO + supervisorJob

  @EventListener(ApplicationUp::class)
  fun onApplicationUp() {
    log.info("Application up: starting SQS queue monitor")
    launch {
      val channel = Channel<Message>()
      launchMessageReceiver(channel)
      repeat(sqsProperties.listenerFibers) {
        launchWorker(channel)
      }
    }
  }

  @EventListener(ApplicationDown::class)
  fun onApplicationDown() {
    log.info("Application down: stopping SQS queue monitor")
    runBlocking {
      log.info(
        "Stopping supervisor job with {} children and {} grandchildren",
        supervisorJob.children.toList().size,
        supervisorJob.children.flatMap { it.children }.toList().size
      )
      supervisorJob.cancelAndJoin()
    }
  }

  private fun CoroutineScope.launchMessageReceiver(channel: SendChannel<Message>) = launch {
    runUntilCancelled {
      sqsClient
        .receiveMessages()
        .also {
          if (it.isNotEmpty()) log.debug("Got {} messages: {}", it.size, it.map(Message::getMessageId))
        }
        .forEach {
          channel.send(it)
        }
    }
  }

  private fun CoroutineScope.launchWorker(channel: ReceiveChannel<Message>) = launch {
    runUntilCancelled {
      for (message in channel) {
        try {
          with(message.parse()) {
            actuator.checkResource(name.let(::ResourceName), apiVersion, kind)
          }
          sqsClient.deleteMessage(message)
        } catch (ex: JsonProcessingException) {
          log.error("Could not parse message payload: {} due to {}", message.body, ex.message)
        } catch (ex: Exception) {
          log.error("ResourceActuator threw {}: {}", ex.javaClass.simpleName, ex.message)
        }
      }
    }
  }

  private fun AmazonSQS.deleteMessage(it: Message) {
    deleteMessage(
      DeleteMessageRequest()
        .withQueueUrl(queueUrl)
        .withReceiptHandle(it.receiptHandle)
    )
  }

  private fun AmazonSQS.receiveMessages(): List<Message> =
    receiveMessage(
      ReceiveMessageRequest()
        .withQueueUrl(queueUrl)
        .withVisibilityTimeout(sqsProperties.visibilityTimeoutSeconds)
        .withWaitTimeSeconds(sqsProperties.waitTimeSeconds)
    )
      .messages

  private suspend fun CoroutineScope.runUntilCancelled(block: suspend () -> Unit) {
    while (isActive) {
      try {
        block()
        yield()
      } catch (ex: CancellationException) {
        log.debug("coroutine on {} cancelled", currentThread().name)
      } catch (ex: Exception) {
        log.error("{} failed with {} \"{}\". Retrying...", currentThread().name, ex.javaClass.simpleName, ex.message)
      }
    }
    log.debug("coroutine on {} exiting", currentThread().name)
  }

  private fun Message.parse(): ResourceCheckMessage = objectMapper.readValue(body)

  private val log by lazy { LoggerFactory.getLogger(javaClass) }
}
