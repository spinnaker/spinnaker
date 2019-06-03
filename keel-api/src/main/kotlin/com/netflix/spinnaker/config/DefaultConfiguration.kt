package com.netflix.spinnaker.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.keel.persistence.ArtifactRepository
import com.netflix.spinnaker.keel.persistence.ResourceRepository
import com.netflix.spinnaker.keel.persistence.ResourceVersionTracker
import com.netflix.spinnaker.keel.persistence.memory.InMemoryArtifactRepository
import com.netflix.spinnaker.keel.persistence.memory.InMemoryResourceRepository
import com.netflix.spinnaker.keel.persistence.memory.InMemoryResourceVersionTracker
import com.netflix.spinnaker.keel.plugin.ResolvableResourceHandler
import com.netflix.spinnaker.keel.serialization.configuredObjectMapper
import de.huxhorn.sulky.ulid.ULID
import org.springframework.beans.factory.config.ConfigurableBeanFactory.SCOPE_PROTOTYPE
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Scope
import org.springframework.core.Ordered.HIGHEST_PRECEDENCE
import org.springframework.core.annotation.Order
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter
import java.time.Clock

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
  @ConditionalOnMissingBean(ResourceVersionTracker::class)
  fun resourceVersionTracker(): ResourceVersionTracker = InMemoryResourceVersionTracker()

  @Bean
  @ConditionalOnMissingBean(ResolvableResourceHandler::class)
  fun noResourcePlugins(): List<ResolvableResourceHandler<*, *>> = emptyList()

  @Bean
  fun csrfDisable() = @Order(HIGHEST_PRECEDENCE) object : WebSecurityConfigurerAdapter() {
    override fun configure(http: HttpSecurity) {
      http.csrf().disable()
    }
  }
}
