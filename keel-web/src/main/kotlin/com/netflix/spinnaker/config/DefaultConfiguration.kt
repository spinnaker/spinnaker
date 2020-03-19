package com.netflix.spinnaker.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper
import com.netflix.spinnaker.fiat.shared.EnableFiatAutoConfig
import com.netflix.spinnaker.filters.AuthenticatedRequestFilter
import com.netflix.spinnaker.keel.api.ComputeResourceSpec
import com.netflix.spinnaker.keel.api.ResourceKind
import com.netflix.spinnaker.keel.api.ResourceSpec
import com.netflix.spinnaker.keel.api.plugins.Resolver
import com.netflix.spinnaker.keel.api.plugins.ResourceHandler
import com.netflix.spinnaker.keel.api.plugins.supporting
import com.netflix.spinnaker.keel.persistence.AgentLockRepository
import com.netflix.spinnaker.keel.persistence.ArtifactRepository
import com.netflix.spinnaker.keel.persistence.DeliveryConfigRepository
import com.netflix.spinnaker.keel.persistence.DiffFingerprintRepository
import com.netflix.spinnaker.keel.persistence.PausedRepository
import com.netflix.spinnaker.keel.persistence.ResourceRepository
import com.netflix.spinnaker.keel.persistence.TaskTrackingRepository
import com.netflix.spinnaker.keel.persistence.memory.InMemoryAgentLockRepository
import com.netflix.spinnaker.keel.persistence.memory.InMemoryArtifactRepository
import com.netflix.spinnaker.keel.persistence.memory.InMemoryDeliveryConfigRepository
import com.netflix.spinnaker.keel.persistence.memory.InMemoryDiffFingerprintRepository
import com.netflix.spinnaker.keel.persistence.memory.InMemoryPausedRepository
import com.netflix.spinnaker.keel.persistence.memory.InMemoryResourceRepository
import com.netflix.spinnaker.keel.persistence.memory.InMemoryTaskTrackingRepository
import com.netflix.spinnaker.keel.resources.ResourceTypeIdentifier
import com.netflix.spinnaker.keel.serialization.configuredObjectMapper
import com.netflix.spinnaker.keel.serialization.configuredYamlMapper
import de.huxhorn.sulky.ulid.ULID
import java.time.Clock
import org.springframework.beans.factory.annotation.Value
import org.springframework.beans.factory.getBeansOfType
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.Ordered.HIGHEST_PRECEDENCE
import org.springframework.scheduling.TaskScheduler
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler

@EnableFiatAutoConfig
@Configuration
class DefaultConfiguration {
  @Bean
  @ConditionalOnMissingBean
  fun clock(): Clock = Clock.systemDefaultZone()

  @Bean
  fun idGenerator(): ULID = ULID()

  @Bean
  fun objectMapper(): ObjectMapper = configuredObjectMapper()

  @Bean
  fun yamlMapper(): YAMLMapper = configuredYamlMapper()

  @Bean
  @ConditionalOnMissingBean
  fun resourceRepository(clock: Clock): ResourceRepository = InMemoryResourceRepository(clock)

  @Bean
  @ConditionalOnMissingBean
  fun artifactRepository(): ArtifactRepository = InMemoryArtifactRepository()

  @Bean
  @ConditionalOnMissingBean
  fun deliveryConfigRepository(
    artifactRepository: ArtifactRepository
  ): DeliveryConfigRepository =
    InMemoryDeliveryConfigRepository()

  @Bean
  @ConditionalOnMissingBean
  fun diffFingerprintRepository(clock: Clock): DiffFingerprintRepository = InMemoryDiffFingerprintRepository(clock)

  @Bean
  @ConditionalOnMissingBean
  fun pausedRepository(): PausedRepository = InMemoryPausedRepository()

  @Bean
  @ConditionalOnMissingBean(ResourceHandler::class)
  fun noResourcePlugins(): List<ResourceHandler<*, *>> = emptyList()

  @Bean
  @ConditionalOnMissingBean
  fun taskTrackingRepository(): TaskTrackingRepository = InMemoryTaskTrackingRepository()

  @Bean
  @ConditionalOnMissingBean
  fun agentLockRepository(): AgentLockRepository = InMemoryAgentLockRepository(emptyList())

  @Bean
  fun resourceTypeIdentifier(
    applicationContext: ApplicationContext
  ): ResourceTypeIdentifier =
    object : ResourceTypeIdentifier {
      // because otherwise we get a circular dependency
      private val handlers by lazy {
        applicationContext.getBeansOfType<ResourceHandler<*, *>>().values
      }

      override fun identify(kind: ResourceKind): Class<out ResourceSpec> {
        return handlers.supporting(kind).supportedKind.specClass
      }
    }

  @Bean
  fun authenticatedRequestFilter(): FilterRegistrationBean<AuthenticatedRequestFilter> =
    FilterRegistrationBean(AuthenticatedRequestFilter(true))
      .apply { order = HIGHEST_PRECEDENCE }

  @Bean
  fun computeResourceResolvers(resolvers: List<Resolver<*>>): List<Resolver<ComputeResourceSpec>> =
    resolvers
      .filter { ComputeResourceSpec::class.java.isAssignableFrom(it.supportedKind.specClass) }
      .map { it as Resolver<ComputeResourceSpec> }

  @Bean
  fun taskScheduler(
    @Value("\${keel.scheduler.pool-size:5}") poolSize: Int
  ): TaskScheduler {
    val scheduler = ThreadPoolTaskScheduler()
    scheduler.threadNamePrefix = "scheduler-"
    scheduler.poolSize = poolSize
    return scheduler
  }
}
