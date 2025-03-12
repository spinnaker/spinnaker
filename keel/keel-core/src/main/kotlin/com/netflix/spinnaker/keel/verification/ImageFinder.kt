package com.netflix.spinnaker.keel.verification

import com.netflix.spinnaker.keel.api.ComputeResourceSpec
import com.netflix.spinnaker.keel.api.DeliveryConfig
import com.netflix.spinnaker.keel.api.Resource
import com.netflix.spinnaker.keel.api.plugins.BaseClusterHandler
import com.netflix.spinnaker.keel.api.plugins.CurrentImages
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class ImageFinder(
  val clusterHandlers: List<BaseClusterHandler<*,*>>
) {
  private val log by lazy { LoggerFactory.getLogger(javaClass) }

  /**
   * Find images that are currently running in an environment and returns them
   */
  fun getImages(deliveryConfig: DeliveryConfig, envName: String): List<CurrentImages> {
    val env = checkNotNull(deliveryConfig.environments.find { it.name == envName }) {
      "Failed to find environment $envName in deliveryConfig ${deliveryConfig.name}"
    }
    return env.resources.mapNotNull { resource ->
        runBlocking { getImages(resource) }
      }
  }

  private suspend fun getImages(resource: Resource<*>): CurrentImages? =
    clusterHandlers
      .find { it.supportedKind.kind == resource.kind }
      ?.getImages(resource)

  @Suppress("UNCHECKED_CAST")
  private suspend fun <S : ComputeResourceSpec<*>, R : Any> BaseClusterHandler<S, R>.getImages(
    resource: Resource<*>
  ): CurrentImages =
    getImage(resource as Resource<S>)
}
