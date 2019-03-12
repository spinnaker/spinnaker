package com.netflix.spinnaker.keel.sqs

import com.amazonaws.services.sqs.AmazonSQS
import com.amazonaws.services.sqs.model.Message
import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
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
      receiveMessage(queueUrl)
        .also {
          log.info("Got {} messages", it.messages.size)
        }
        .messages
        .forEach {
          try {
            with(it.parse()) {
              actuator.checkResource(name.let(::ResourceName), apiVersion, kind)
            }
            deleteMessage(queueUrl, it.receiptHandle)
          } catch (e: JsonProcessingException) {
            log.error("Could not parse message payload: {}", it.body)
          } catch (e: Throwable) {
            log.error("Resource Actuator threw {}: {}", e.javaClass.simpleName, e.message)
          }
        }
    }
  }

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
