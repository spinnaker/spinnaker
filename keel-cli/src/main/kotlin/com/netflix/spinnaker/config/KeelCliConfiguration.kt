package com.netflix.spinnaker.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.keel.api.ResourceSpec
import com.netflix.spinnaker.keel.api.ec2.ClusterSpec
import com.netflix.spinnaker.keel.api.ec2.EC2_CLUSTER_V1
import com.netflix.spinnaker.keel.api.support.ExtensionRegistry
import com.netflix.spinnaker.keel.api.support.register
import com.netflix.spinnaker.keel.ec2.jackson.registerKeelEc2ApiModule
import com.netflix.spinnaker.keel.serialization.configureForKeel
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Bean
import org.springframework.stereotype.Component
import java.time.Clock
import javax.annotation.PostConstruct

@Component
class KeelCliConfiguration(
  private val extensionRegistry: ExtensionRegistry,
  private val objectMappers: List<ObjectMapper>,
) {
  private val log: Logger = LoggerFactory.getLogger(javaClass)

  @Bean
  fun clock(): Clock = Clock.systemDefaultZone()

  @PostConstruct
  fun registerApiExtensionsWithObjectMappers() {
    objectMappers.forEach {
      it.configureForKeel()
      it.registerKeelEc2ApiModule()
    }
  }

  @PostConstruct
  fun registerResourceSpecSubtypes() {
    extensionRegistry.register<ResourceSpec>(ClusterSpec::class.java, EC2_CLUSTER_V1.kind.toString())
  }
}