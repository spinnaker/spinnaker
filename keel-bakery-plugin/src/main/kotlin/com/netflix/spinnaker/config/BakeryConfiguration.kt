package com.netflix.spinnaker.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.igor.ArtifactService
import com.netflix.spinnaker.keel.bakery.BaseImageCache
import com.netflix.spinnaker.keel.bakery.resource.ImageHandler
import com.netflix.spinnaker.keel.clouddriver.CloudDriverService
import com.netflix.spinnaker.keel.clouddriver.ImageService
import com.netflix.spinnaker.keel.mahe.DynamicPropertyService
import com.netflix.spinnaker.keel.orca.OrcaService
import com.netflix.spinnaker.keel.persistence.ArtifactRepository
import com.netflix.spinnaker.keel.plugin.Resolver
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
@ConditionalOnProperty("keel.plugins.bakery.enabled")
class BakeryConfiguration {
  @Bean
  fun baseImageCache(
    maheService: DynamicPropertyService,
    objectMapper: ObjectMapper
  ) = BaseImageCache(maheService, objectMapper)

  @Bean
  fun imageHandler(
    objectMapper: ObjectMapper,
    artifactRepository: ArtifactRepository,
    baseImageCache: BaseImageCache,
    clouddriverService: CloudDriverService,
    orcaService: OrcaService,
    igorService: ArtifactService,
    imageService: ImageService,
    publisher: ApplicationEventPublisher,
    normalizers: List<Resolver<*>>
  ) = ImageHandler(
    objectMapper,
    artifactRepository,
    baseImageCache,
    clouddriverService,
    orcaService,
    igorService,
    imageService,
    publisher,
    normalizers
  )
}
