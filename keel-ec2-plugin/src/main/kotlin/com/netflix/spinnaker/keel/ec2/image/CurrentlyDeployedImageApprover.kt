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

import com.netflix.spinnaker.keel.api.artifacts.ArtifactType.deb
import com.netflix.spinnaker.keel.api.ec2.ArtifactImageProvider
import com.netflix.spinnaker.keel.api.ec2.ClusterSpec
import com.netflix.spinnaker.keel.api.ec2.ReferenceArtifactImageProvider
import com.netflix.spinnaker.keel.events.ArtifactVersionDeployed
import com.netflix.spinnaker.keel.persistence.KeelRepository
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

@Component
class CurrentlyDeployedImageApprover(
  private val repository: KeelRepository
) {
  private val log = LoggerFactory.getLogger(javaClass)

  @EventListener(ArtifactVersionDeployed::class)
  fun onArtifactVersionDeployed(event: ArtifactVersionDeployed) =
    runBlocking {
      val resourceId = event.resourceId
      val resource = repository.getResource(resourceId)
      val deliveryConfig = repository.deliveryConfigFor(resourceId)
      val env = repository.environmentFor(resourceId)

      (resource.spec as? ClusterSpec)?.let { spec ->
        val artifact = when (spec.imageProvider) {
          is ArtifactImageProvider -> {
            spec.imageProvider.deliveryArtifact
          }
          is ReferenceArtifactImageProvider -> {
            deliveryConfig.matchingArtifactByReference(spec.imageProvider.reference, deb)
          }
          else -> {
            null
          }
        }

        if (artifact != null) {
          val approvedForEnv = repository.isApprovedFor(
            deliveryConfig = deliveryConfig,
            artifact = artifact,
            version = event.artifactVersion,
            targetEnvironment = env.name
          )
          // should only mark as successfully deployed if it's already approved for the environment
          if (approvedForEnv) {
            val wasSuccessfullyDeployed = repository.wasSuccessfullyDeployedTo(
              deliveryConfig = deliveryConfig,
              artifact = artifact,
              version = event.artifactVersion,
              targetEnvironment = env.name
            )
            if (!wasSuccessfullyDeployed) {
              log.info("Marking {} as deployed in {} for config {} because it is already deployed", event.artifactVersion, env.name, deliveryConfig.name)
              repository.markAsSuccessfullyDeployedTo(
                deliveryConfig = deliveryConfig,
                artifact = artifact,
                version = event.artifactVersion,
                targetEnvironment = env.name
              )
            }
          }
        }
      }
    }
}
