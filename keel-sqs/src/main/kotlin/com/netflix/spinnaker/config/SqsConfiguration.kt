package com.netflix.spinnaker.config

import com.amazonaws.auth.AWSCredentialsProvider
import com.amazonaws.services.sqs.AmazonSQS
import com.amazonaws.services.sqs.AmazonSQSClientBuilder
import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.keel.annealing.ResourceActuator
import com.netflix.spinnaker.keel.annealing.ResourceCheckQueue
import com.netflix.spinnaker.keel.sqs.SqsResourceCheckListener
import com.netflix.spinnaker.keel.sqs.SqsResourceCheckQueue
import com.netflix.spinnaker.kork.aws.ARN
import com.netflix.spinnaker.kork.aws.bastion.BastionConfig
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import

@Configuration
@ConditionalOnProperty("sqs.enabled")
@Import(BastionConfig::class)
@EnableConfigurationProperties(SqsProperties::class)
class SqsConfiguration {

  @Bean
  fun queueARN(sqsProperties: SqsProperties) = sqsProperties.queueARN.let(::ARN)

  @Bean
  @ConditionalOnMissingBean(AmazonSQS::class)
  fun sqsClient(
    awsCredentialsProvider: AWSCredentialsProvider,
    queueARN: ARN
  ): AmazonSQS =
    AmazonSQSClientBuilder
      .standard()
      .withCredentials(awsCredentialsProvider)
      .withRegion(queueARN.region)
      .build()

  @Bean
  fun resourceCheckQueue(
    sqsClient: AmazonSQS,
    queueARN: ARN,
    objectMapper: ObjectMapper
  ): ResourceCheckQueue =
    SqsResourceCheckQueue(sqsClient, queueARN, objectMapper)

  @Bean
  fun resourceCheckListener(
    sqsClient: AmazonSQS,
    queueARN: ARN,
    objectMapper: ObjectMapper,
    actuator: ResourceActuator
  ): Any =
    sqsClient.createQueue(queueARN.name).run {
      SqsResourceCheckListener(sqsClient, queueUrl, objectMapper, actuator)
    }
}
