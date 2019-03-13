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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers.Default
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import javax.annotation.PostConstruct
import javax.annotation.PreDestroy

internal class SqsResourceCheckListener(
  private val sqsClient: AmazonSQS,
  private val queueUrl: String,
  private val sqsProperties: SqsProperties,
  private val objectMapper: ObjectMapper,
  private val actuator: ResourceActuator
) {
  private lateinit var listenerJob: Job

  private val scope: CoroutineScope
    get() = CoroutineScope(Default)

  @PostConstruct
  fun startListening() {
    listenerJob = scope.launch {
      while (isActive) {
        handleMessages()
      }
    }
  }

  internal fun handleMessages() {
    with(sqsClient) {
      receiveMessages()
        .also {
          if (it.isNotEmpty()) log.debug("Got {} messages: {}", it.size, it.map(Message::getMessageId))
        }
        .forEach {
          try {
            with(it.parse()) {
              actuator.checkResource(name.let(::ResourceName), apiVersion, kind)
            }
            deleteMessage(it)
          } catch (e: JsonProcessingException) {
            log.error("Could not parse message payload: {}", it.body)
          } catch (e: Throwable) {
            log.error("Resource Actuator threw {}: {}", e.javaClass.simpleName, e.message)
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
    listenerJob.cancel()
    runBlocking {
      listenerJob.join()
    }
  }

  private fun Message.parse(): ResourceCheckMessage = objectMapper.readValue(body)

  private val log by lazy { LoggerFactory.getLogger(javaClass) }
}
