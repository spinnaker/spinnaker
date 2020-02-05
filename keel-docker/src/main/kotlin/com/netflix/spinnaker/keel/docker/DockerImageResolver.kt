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
package com.netflix.spinnaker.keel.docker

import com.netflix.spinnaker.keel.api.ArtifactType.docker
import com.netflix.spinnaker.keel.api.DeliveryArtifact
import com.netflix.spinnaker.keel.api.DeliveryConfig
import com.netflix.spinnaker.keel.api.DockerArtifact
import com.netflix.spinnaker.keel.api.Environment
import com.netflix.spinnaker.keel.api.Resource
import com.netflix.spinnaker.keel.api.ResourceSpec
import com.netflix.spinnaker.keel.api.TagComparator
import com.netflix.spinnaker.keel.api.id
import com.netflix.spinnaker.keel.api.image
import com.netflix.spinnaker.keel.api.organization
import com.netflix.spinnaker.keel.exceptions.NoDockerImageSatisfiesConstraints
import com.netflix.spinnaker.keel.persistence.ArtifactRepository
import com.netflix.spinnaker.keel.persistence.DeliveryConfigRepository
import com.netflix.spinnaker.keel.persistence.NoMatchingArtifactException
import com.netflix.spinnaker.keel.plugin.Resolver

/**
 * Provides the basic functionality for deciding what digest is desired.
 *
 * Implement the methods in this interface for each cloud provider.
 */
abstract class DockerImageResolver<T : ResourceSpec>(
  val deliveryConfigRepository: DeliveryConfigRepository,
  val artifactRepository: ArtifactRepository
) : Resolver<T> {

  /**
   * Pull the container provider out of the resource spec
   */
  abstract fun getContainerFromSpec(resource: Resource<T>): ContainerProvider

  /**
   * Pull the correct docker account out of the spec
   */
  abstract fun getAccountFromSpec(resource: Resource<T>): String

  /**
   * Replace exiting container with resolved container
   */
  abstract fun updateContainerInSpec(resource: Resource<T>, container: ContainerProvider): Resource<T>

  /**
   * Get all available the tags for an image
   */
  abstract fun getTags(account: String, organization: String, image: String): List<String>

  /**De
   * Get the digest for a specific image
   */
  abstract fun getDigest(account: String, organization: String, image: String, tag: String): String

  override fun invoke(resource: Resource<T>): Resource<T> {
    val container = getContainerFromSpec(resource)
    if (container is DigestProvider) {
      return resource
    }
    val deliveryConfig = deliveryConfigRepository.deliveryConfigFor(resource.id)
    val environment = deliveryConfigRepository.environmentFor(resource.id)
    val artifact = getArtifact(container, deliveryConfig)
    val account = getAccountFromSpec(resource)
    val tag: String = findTagGivenDeliveryConfig(deliveryConfig, environment, artifact)

    val newContainer = getContainer(account, artifact, tag)
    return updateContainerInSpec(resource, newContainer)
  }

  fun getArtifact(container: ContainerProvider, deliveryConfig: DeliveryConfig): DockerArtifact =
    when (container) {
      is ReferenceProvider -> {
        deliveryConfig.artifacts.find { it.reference == container.reference && it.type == docker } as DockerArtifact?
          ?: throw NoMatchingArtifactException(reference = container.reference, type = docker, deliveryConfigName = deliveryConfig.name)
      }
      is VersionedTagProvider -> {
        // deprecated
        // container is old tag strategy, not artifact reference
        DockerArtifact(name = container.repository(), deliveryConfigName = deliveryConfig.name, tagVersionStrategy = container.tagVersionStrategy, captureGroupRegex = container.captureGroupRegex)
      }
      else -> throw IllegalArgumentException("Unsupported container provider ${container.javaClass}")
    }

  fun findTagGivenDeliveryConfig(deliveryConfig: DeliveryConfig, environment: Environment, artifact: DeliveryArtifact) =
    artifactRepository.latestVersionApprovedIn(
      deliveryConfig,
      artifact,
      environment.name
    ) ?: throw NoDockerImageSatisfiesConstraints(artifact.name, environment.name)

  fun findTagGivenStrategy(
    account: String,
    container: VersionedTagProvider
  ): String {
    val tags = getTags(account, container.organization, container.image)
    return tags.sortedWith(TagComparator(container.tagVersionStrategy, container.captureGroupRegex)).first()
  }

  fun getContainer(
    account: String,
    artifact: DockerArtifact,
    tag: String
  ): DigestProvider {
    val digest = getDigest(account, artifact.organization, artifact.image, tag)
    return DigestProvider(
      organization = artifact.organization,
      image = artifact.image,
      digest = digest
    )
  }
}
