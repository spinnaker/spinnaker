package com.netflix.spinnaker.keel.sqs

import com.amazonaws.services.sqs.AmazonSQS
import com.amazonaws.services.sqs.AmazonSQSClient
import com.netflix.spinnaker.keel.serialization.configuredObjectMapper
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.testcontainers.containers.localstack.LocalStackContainer

@Configuration
class LocalStackSqsConfiguration {

  @Bean
  fun objectMapper() = configuredObjectMapper()

  @Bean(destroyMethod = "stop")
  fun localStack(): LocalStackContainer =
    LocalStackContainer()
      .withServices(LocalStackContainer.Service.SQS)
      .also(LocalStackContainer::start)

  @Bean
  fun localStackSqs(localStack: LocalStackContainer): AmazonSQS {
    return AmazonSQSClient.builder()
      .withEndpointConfiguration(localStack.getEndpointConfiguration(LocalStackContainer.Service.SQS))
      .withCredentials(localStack.defaultCredentialsProvider)
      .build()
  }
}
