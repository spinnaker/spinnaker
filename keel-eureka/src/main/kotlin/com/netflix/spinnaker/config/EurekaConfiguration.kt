package com.netflix.spinnaker.config

import com.netflix.appinfo.InstanceInfo
import com.netflix.spinnaker.keel.eureka.DiscoveryActivator
import com.netflix.spinnaker.keel.eureka.EurekaInstanceIdSupplier
import com.netflix.spinnaker.keel.info.InstanceIdSupplier
import com.netflix.spinnaker.kork.eureka.EurekaComponents
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import

@Configuration
@ConditionalOnProperty("eureka.enabled")
@Import(EurekaComponents::class)
class EurekaConfiguration {
  @Bean
  fun instanceIdSupplier(instanceInfo: InstanceInfo): InstanceIdSupplier =
    EurekaInstanceIdSupplier(instanceInfo)

  @Bean
  fun discoveryActivator(publisher: ApplicationEventPublisher) = DiscoveryActivator(publisher)
}
