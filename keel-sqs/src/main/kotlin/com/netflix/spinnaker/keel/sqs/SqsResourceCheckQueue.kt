package com.netflix.spinnaker.keel.sqs

import com.amazonaws.services.sqs.AmazonSQS
import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.keel.annealing.ResourceCheckQueue
import com.netflix.spinnaker.keel.api.ApiVersion
import com.netflix.spinnaker.keel.api.ResourceName
import com.netflix.spinnaker.kork.aws.ARN
import javax.annotation.PostConstruct

internal class SqsResourceCheckQueue(
  private val sqsClient: AmazonSQS,
  private val queueARN: ARN,
  private val objectMapper: ObjectMapper
) : ResourceCheckQueue {

  private lateinit var queueUrl: String

  @PostConstruct
  fun initQueue() {
    queueUrl = sqsClient.createQueue(queueARN.name).queueUrl
  }

  override fun scheduleCheck(name: ResourceName, apiVersion: ApiVersion, kind: String) {
    sqsClient.sendMessage(
      queueUrl,
      objectMapper.writeValueAsString(ResourceCheckMessage(name.value, apiVersion, kind))
    )
  }
}
