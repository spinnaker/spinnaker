package com.netflix.spinnaker.keel.sqs

import com.amazonaws.services.sqs.AmazonSQS
import com.amazonaws.services.sqs.model.Message
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.netflix.spinnaker.keel.annealing.ResourceActuator
import com.netflix.spinnaker.keel.api.ResourceName
import com.netflix.spinnaker.kork.aws.ARN
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import javax.annotation.PostConstruct
import javax.annotation.PreDestroy

internal class SqsResourceCheckListener(
  private val sqsClient: AmazonSQS,
  private val queueARN: ARN,
  private val objectMapper: ObjectMapper,
  private val actuator: ResourceActuator
) {
  private lateinit var queueUrl: String
  private lateinit var listenerJob: Job

  private val scope: CoroutineScope
    get() = CoroutineScope(Dispatchers.Default)

  private var active: Boolean = false

  @PostConstruct
  fun startListening() {
    queueUrl = sqsClient.createQueue(queueARN.name).queueUrl
    active = true
    listenerJob = scope.launch {
      while (active) {
        handleMessages {
          with(objectMapper.readValue<ResourceCheckMessage>(it.body)) {
            actuator.checkResource(name.let(::ResourceName), apiVersion, kind)
          }
        }
      }
    }
  }

  private fun handleMessages(handler: (Message) -> Unit) {
    with(sqsClient) {
      receiveMessage(queueUrl)
        .messages
        .forEach {
          handler(it)
          deleteMessage(queueUrl, it.receiptHandle)
        }
    }
  }

  @PreDestroy
  fun stopListening() {
    runBlocking {
      active = false
      listenerJob.join()
    }
  }
}
