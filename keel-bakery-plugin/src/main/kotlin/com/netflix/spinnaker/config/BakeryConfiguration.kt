package com.netflix.spinnaker.config

import com.netflix.spinnaker.keel.api.actuation.TaskLauncher
import com.netflix.spinnaker.keel.bakery.BaseImageCache
import com.netflix.spinnaker.keel.bakery.BaseImageCacheProperties
import com.netflix.spinnaker.keel.bakery.DefaultBaseImageCache
import com.netflix.spinnaker.keel.bakery.artifact.BakeCredentials
import com.netflix.spinnaker.keel.bakery.artifact.ImageHandler
import com.netflix.spinnaker.keel.clouddriver.CloudDriverService
import com.netflix.spinnaker.keel.clouddriver.ImageService
import com.netflix.spinnaker.keel.igor.artifact.ArtifactService
import com.netflix.spinnaker.keel.persistence.BakedImageRepository
import com.netflix.spinnaker.keel.persistence.KeelRepository
import com.netflix.spinnaker.keel.persistence.PausedRepository
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.env.Environment

@Configuration
@ConditionalOnProperty("keel.plugins.bakery.enabled")
@EnableConfigurationProperties(BaseImageCacheProperties::class)
class BakeryConfiguration {
  @Bean
  fun imageHandler(
    keelRepository: KeelRepository,
    baseImageCache: BaseImageCache,
    bakedImageRepository: BakedImageRepository,
    clouddriverService: CloudDriverService,
    igorService: ArtifactService,
    imageService: ImageService,
    publisher: ApplicationEventPublisher,
    taskLauncher: TaskLauncher,
    @Value("\${bakery.defaults.serviceAccount:keel@spinnaker.io}") defaultServiceAccount: String,
    @Value("\${bakery.defaults.application:keel}") defaultApplication: String,
    pausedRepository: PausedRepository,
    springEnv: Environment
  ) = ImageHandler(
    keelRepository,
    baseImageCache,
    bakedImageRepository,
    igorService,
    imageService,
    publisher,
    taskLauncher,
    BakeCredentials(defaultServiceAccount, defaultApplication),
    pausedRepository,
    springEnv
  )

  @Bean
  @ConditionalOnMissingBean
  fun baseImageCache(
    baseImageCacheProperties: BaseImageCacheProperties
  ): BaseImageCache = DefaultBaseImageCache(baseImageCacheProperties.baseImages)
}
