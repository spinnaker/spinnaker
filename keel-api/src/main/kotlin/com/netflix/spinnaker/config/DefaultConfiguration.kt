package com.netflix.spinnaker.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.fiat.shared.EnableFiatAutoConfig
import com.netflix.spinnaker.filters.AuthenticatedRequestFilter
import com.netflix.spinnaker.keel.api.ApiVersion
import com.netflix.spinnaker.keel.persistence.ArtifactRepository
import com.netflix.spinnaker.keel.persistence.DeliveryConfigRepository
import com.netflix.spinnaker.keel.persistence.PromotionRepository
import com.netflix.spinnaker.keel.persistence.ResourceRepository
import com.netflix.spinnaker.keel.persistence.ResourceVersionTracker
import com.netflix.spinnaker.keel.persistence.memory.InMemoryArtifactRepository
import com.netflix.spinnaker.keel.persistence.memory.InMemoryDeliveryConfigRepository
import com.netflix.spinnaker.keel.persistence.memory.InMemoryPromotionRepository
import com.netflix.spinnaker.keel.persistence.memory.InMemoryResourceRepository
import com.netflix.spinnaker.keel.persistence.memory.InMemoryResourceVersionTracker
import com.netflix.spinnaker.keel.plugin.ResolvableResourceHandler
import com.netflix.spinnaker.keel.plugin.supporting
import com.netflix.spinnaker.keel.resources.ResourceTypeIdentifier
import com.netflix.spinnaker.keel.serialization.configuredObjectMapper
import de.huxhorn.sulky.ulid.ULID
import org.springframework.beans.factory.config.ConfigurableBeanFactory.SCOPE_PROTOTYPE
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Scope
import org.springframework.core.Ordered.HIGHEST_PRECEDENCE
import java.time.Clock

@EnableFiatAutoConfig
@Configuration
class DefaultConfiguration {
  @Bean
  @ConditionalOnMissingBean
  fun clock(): Clock = Clock.systemDefaultZone()

  @Bean
  fun idGenerator(): ULID = ULID()

  @Bean
  // prototype as we want to customize config for some uses
  @Scope(SCOPE_PROTOTYPE)
  fun objectMapper(): ObjectMapper = configuredObjectMapper()

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
  fun promotionRepository(): PromotionRepository = InMemoryPromotionRepository()

  @Bean
  @ConditionalOnMissingBean(ResourceVersionTracker::class)
  fun resourceVersionTracker(): ResourceVersionTracker = InMemoryResourceVersionTracker()

  @Bean
  @ConditionalOnMissingBean(ResolvableResourceHandler::class)
  fun noResourcePlugins(): List<ResolvableResourceHandler<*, *>> = emptyList()

  @Bean
  fun resourceTypeIdentifier(
    handlers: List<ResolvableResourceHandler<*, *>>
  ): ResourceTypeIdentifier =
    object : ResourceTypeIdentifier {
      override fun identify(apiVersion: ApiVersion, kind: String): Class<*> {
        return handlers.supporting(apiVersion, kind).supportedKind.second
      }
    }

  @Bean
  fun authenticatedRequestFilter(): FilterRegistrationBean<AuthenticatedRequestFilter> =
    FilterRegistrationBean(AuthenticatedRequestFilter(true))
      .apply { order = HIGHEST_PRECEDENCE }
}
