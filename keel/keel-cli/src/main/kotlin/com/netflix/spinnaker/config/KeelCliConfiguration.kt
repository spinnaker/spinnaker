package com.netflix.spinnaker.config

import com.netflix.spinnaker.keel.api.ResourceSpec
import com.netflix.spinnaker.keel.api.ec2.ClusterSpec
import com.netflix.spinnaker.keel.api.ec2.EC2_CLUSTER_V1_1
import com.netflix.spinnaker.keel.api.support.ExtensionRegistry
import com.netflix.spinnaker.keel.api.support.register
import com.netflix.spinnaker.keel.ec2.jackson.registerKeelEc2ApiModule
import com.netflix.spinnaker.keel.serialization.configuredObjectMapper
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Bean
import org.springframework.stereotype.Component
import org.springframework.context.annotation.Primary
import tools.jackson.databind.json.JsonMapper
import java.time.Clock
import jakarta.annotation.PostConstruct

@Component
class KeelCliConfiguration(
  private val extensionRegistry: ExtensionRegistry
) {
  private val log: Logger = LoggerFactory.getLogger(javaClass)

  @Bean
  fun clock(): Clock = Clock.systemDefaultZone()

  @Bean(name = ["jsonMapper", "objectMapper"])
  @Primary
  fun objectMapper(): JsonMapper = configuredObjectMapper()
    .registerKeelEc2ApiModule()

  @PostConstruct
  fun registerResourceSpecSubtypes() {
    extensionRegistry.register<ResourceSpec>(ClusterSpec::class.java, EC2_CLUSTER_V1_1.kind.toString())
  }
}
