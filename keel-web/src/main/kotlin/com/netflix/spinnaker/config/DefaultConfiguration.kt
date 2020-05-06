package com.netflix.spinnaker.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper
import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.fiat.shared.EnableFiatAutoConfig
import com.netflix.spinnaker.filters.AuthenticatedRequestFilter
import com.netflix.spinnaker.keel.api.plugins.ResourceHandler
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
import com.netflix.spinnaker.keel.serialization.configuredObjectMapper
import com.netflix.spinnaker.keel.serialization.configuredYamlMapper
import com.netflix.spinnaker.kork.web.interceptors.MetricsInterceptor
import de.huxhorn.sulky.ulid.ULID
import java.time.Clock
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.core.Ordered.HIGHEST_PRECEDENCE
import org.springframework.scheduling.TaskScheduler
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler
import org.springframework.web.servlet.config.annotation.InterceptorRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

private const val IPC_SERVER_METRIC = "controller.invocations"

@EnableFiatAutoConfig
@Configuration
class DefaultConfiguration(
  val spectatorRegistry: Registry
) : WebMvcConfigurer {

  /**
   * Enable controller metrics
   */
  override fun addInterceptors(interceptorRegistry: InterceptorRegistry) {

    /**
     * Path variables that will be added as tags to the metrics
     *
     * Lorin chose these particular variables arbitrarily as potentially useful. Once we start consuming
     * these metrics, we should revisit whether to change these based on usefulness & cardinality
     */
    val pathVarsToTag = listOf("name", "application")

    val queryParamsToTag = null

    // exclude list copied from fiat: https://github.com/spinnaker/fiat/blob/0d58386152ad78234b2554f5efdf12d26b77d57c/fiat-web/src/main/java/com/netflix/spinnaker/fiat/config/FiatConfig.java#L50
    val exclude = listOf("BasicErrorController")

    val interceptor = MetricsInterceptor(spectatorRegistry, IPC_SERVER_METRIC, pathVarsToTag, queryParamsToTag, exclude)
    interceptorRegistry.addInterceptor(interceptor)
  }

  @Bean
  @ConditionalOnMissingBean
  fun clock(): Clock = Clock.systemUTC()

  @Bean
  fun idGenerator(): ULID = ULID()

  @Bean
  @Primary
  @Qualifier("jsonMapper")
  fun objectMapper(): ObjectMapper = configuredObjectMapper()

  @Bean
  @Qualifier("yamlMapper")
  fun yamlMapper(): YAMLMapper = configuredYamlMapper()

  @Bean
  @ConditionalOnMissingBean
  fun resourceRepository(clock: Clock): ResourceRepository = InMemoryResourceRepository(clock)

  @Bean
  @ConditionalOnMissingBean
  fun artifactRepository(clock: Clock): ArtifactRepository = InMemoryArtifactRepository(clock)

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
  fun authenticatedRequestFilter(): FilterRegistrationBean<AuthenticatedRequestFilter> =
    FilterRegistrationBean(AuthenticatedRequestFilter(true))
      .apply { order = HIGHEST_PRECEDENCE }

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
