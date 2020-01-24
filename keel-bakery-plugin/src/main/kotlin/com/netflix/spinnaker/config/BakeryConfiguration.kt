package com.netflix.spinnaker.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.igor.ArtifactService
import com.netflix.spinnaker.keel.bakery.BaseImageCache
import com.netflix.spinnaker.keel.bakery.BaseImageCacheProperties
import com.netflix.spinnaker.keel.bakery.DefaultBaseImageCache
import com.netflix.spinnaker.keel.bakery.resource.ImageHandler
import com.netflix.spinnaker.keel.clouddriver.CloudDriverService
import com.netflix.spinnaker.keel.clouddriver.ImageService
import com.netflix.spinnaker.keel.orca.OrcaService
import com.netflix.spinnaker.keel.persistence.ArtifactRepository
import com.netflix.spinnaker.keel.plugin.Resolver
import com.netflix.spinnaker.keel.plugin.TaskLauncher
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
@ConditionalOnProperty("keel.plugins.bakery.enabled")
@EnableConfigurationProperties(BaseImageCacheProperties::class)
class BakeryConfiguration {
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
    taskLauncher: TaskLauncher,
    normalizers: List<Resolver<*>>
  ) = ImageHandler(
    artifactRepository,
    baseImageCache,
    clouddriverService,
    orcaService,
    igorService,
    imageService,
    publisher,
    taskLauncher,
    objectMapper,
    normalizers
  )

  @Bean
  @ConditionalOnMissingBean
  fun baseImageCache(
    baseImageCacheProperties: BaseImageCacheProperties
  ): BaseImageCache = DefaultBaseImageCache(baseImageCacheProperties.baseImages)
}
