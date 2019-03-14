package com.netflix.spinnaker.keel.sqs

import com.amazonaws.services.sqs.AmazonSQS
import com.amazonaws.services.sqs.model.DeleteMessageRequest
import com.amazonaws.services.sqs.model.Message
import com.amazonaws.services.sqs.model.ReceiveMessageRequest
import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.netflix.spinnaker.config.SqsProperties
import com.netflix.spinnaker.keel.annealing.ResourceActuator
import com.netflix.spinnaker.keel.api.ResourceName
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.slf4j.LoggerFactory
import java.lang.Thread.currentThread
import javax.annotation.PostConstruct
import javax.annotation.PreDestroy

internal class SqsResourceCheckListener(
  private val sqsClient: AmazonSQS,
  private val queueUrl: String,
  private val sqsProperties: SqsProperties,
  private val objectMapper: ObjectMapper,
  private val actuator: ResourceActuator
) {
  private lateinit var rootJob: Job

  private val scope: CoroutineScope = CoroutineScope(IO)

  @PostConstruct
  fun startListening() {
    rootJob = scope.launch {
      val channel = Channel<Message>()
      launchMessageReceiver(channel)
      repeat(10) {
        launchWorker(channel)
      }
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

  @PreDestroy
  fun stopListening() {
    runBlocking {
      log.info("Stopping job with {} children", rootJob.children.toList().size)
      rootJob.cancelAndJoin()
    }
  }

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
  }

  private fun Message.parse(): ResourceCheckMessage = objectMapper.readValue(body)

  private val log by lazy { LoggerFactory.getLogger(javaClass) }
}
