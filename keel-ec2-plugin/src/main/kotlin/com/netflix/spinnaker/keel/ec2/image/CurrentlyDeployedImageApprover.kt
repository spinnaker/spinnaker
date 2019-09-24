/*
 *
 * Copyright 2019 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package com.netflix.spinnaker.keel.ec2.image

import com.netflix.spinnaker.keel.api.ResourceId
import com.netflix.spinnaker.keel.api.ec2.ArtifactImageProvider
import com.netflix.spinnaker.keel.api.ec2.ClusterSpec
import com.netflix.spinnaker.keel.api.serviceAccount
import com.netflix.spinnaker.keel.clouddriver.CloudDriverService
import com.netflix.spinnaker.keel.clouddriver.model.appVersion
import com.netflix.spinnaker.keel.persistence.ArtifactRepository
import com.netflix.spinnaker.keel.persistence.DeliveryConfigRepository
import com.netflix.spinnaker.keel.persistence.ResourceRepository
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

@Component
class CurrentlyDeployedImageApprover(
  private val cloudDriverService: CloudDriverService,
  private val artifactRepository: ArtifactRepository,
  private val resourceRepository: ResourceRepository,
  private val deliveryConfigRepository: DeliveryConfigRepository
) {
  private val log = LoggerFactory.getLogger(javaClass)

  @EventListener(ArtifactAlreadyDeployedEvent::class)
  fun artifactAlreadyDeployedEventHandler(event: ArtifactAlreadyDeployedEvent) =
    runBlocking {
      val resourceId = ResourceId(event.resourceId)
      val resource = resourceRepository.get(resourceId)
      val deliveryConfig = deliveryConfigRepository.deliveryConfigFor(resourceId)
      val env = deliveryConfigRepository.environmentFor(resourceId)

      if (resource.spec is ClusterSpec && deliveryConfig != null && env != null) {
        val spec = resource.spec as ClusterSpec // needed because kotlin can't cast it automatically
        if (spec.imageProvider is ArtifactImageProvider) {
          val artifact = spec.imageProvider.deliveryArtifact
          val image = cloudDriverService.namedImages(resource.serviceAccount, event.imageId, null).first()
          val appversion = image.appVersion

          val approvedForEnv = artifactRepository.isApprovedFor(
            deliveryConfig = deliveryConfig,
            artifact = artifact,
            version = appversion,
            targetEnvironment = env.name
          )
          // should only mark as successfully deployed if it's already approved for the environment
          if (approvedForEnv) {
            val wasSuccessfullyDeployed = artifactRepository.wasSuccessfullyDeployedTo(
              deliveryConfig = deliveryConfig,
              artifact = artifact,
              version = appversion,
              targetEnvironment = env.name
            )
            if (!wasSuccessfullyDeployed) {
              log.info("Marking {} as deployed in {} for config {} because it is already deployed", appversion, env.name, deliveryConfig.name)
              artifactRepository.markAsSuccessfullyDeployedTo(
                deliveryConfig = deliveryConfig,
                artifact = artifact,
                version = appversion,
                targetEnvironment = env.name
              )
            }
          }
        }
      }
    }
}
