package com.netflix.spinnaker.keel.ami

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.google.common.annotations.VisibleForTesting
import com.netflix.spinnaker.keel.bakery.api.BaseLabel
import com.netflix.spinnaker.keel.mahe.DynamicPropertyService
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class BaseImageCache(
  private val maheService: DynamicPropertyService,
  private val objectMapper: ObjectMapper
) {
  private val baseImages: MutableMap<Pair<String, BaseLabel>, String> = mutableMapOf()

  @Scheduled(cron = "\${bakery.base-image-cache.schedule:0 0 0/2 ? * * *}")
  fun scheduledRefresh() {
    GlobalScope.launch { refresh() }
  }

  @VisibleForTesting
  internal suspend fun refresh() {
    maheService
      .getProperties("bakery")
      .await()
      .propertiesList
      .find { it.key == "bakery.api.base_label_map" }
      ?.let {
        objectMapper.readValue<Map<String, Map<String, String>>>(it.value as String)
      }
      ?.forEach { (os, images) ->
        images.forEach { (label, imageName) ->
          if (label.toUpperCase() in BASE_LABEL_NAMES) {
            baseImages.put(os to BaseLabel.valueOf(label.toUpperCase()), imageName)
              .also {
                if (it != imageName) {
                  log.info("Updated base image for {} {} to {}", os, label, imageName)
                }
              }
          }
        }
      }
  }

  fun getBaseImage(os: String, label: BaseLabel): String = baseImages.getValue(os to label)

  private val log by lazy { LoggerFactory.getLogger(javaClass) }

  companion object {
    val BASE_LABEL_NAMES = BaseLabel.values().map { it.name }
  }
}
