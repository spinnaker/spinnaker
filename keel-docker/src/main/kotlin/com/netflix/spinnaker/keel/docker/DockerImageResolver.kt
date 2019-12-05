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

import com.netflix.spinnaker.keel.api.ArtifactStatus
import com.netflix.spinnaker.keel.api.ArtifactType.DOCKER
import com.netflix.spinnaker.keel.api.DeliveryArtifact
import com.netflix.spinnaker.keel.api.DeliveryConfig
import com.netflix.spinnaker.keel.api.Environment
import com.netflix.spinnaker.keel.api.Resource
import com.netflix.spinnaker.keel.api.ResourceSpec
import com.netflix.spinnaker.keel.api.id
import com.netflix.spinnaker.keel.persistence.ArtifactRepository
import com.netflix.spinnaker.keel.persistence.DeliveryConfigRepository
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
   * Pull the container out of the resource spec
   */
  abstract fun getContainerFromSpec(resource: Resource<T>): Container

  /**
   * Pull the correct docker account out of the spec
   */
  abstract fun getAccountFromSpec(resource: Resource<T>): String

  /**
   * Replace exiting container with resolved container
   */
  abstract fun updateContainerInSpec(resource: Resource<T>, container: Container): Resource<T>

  /**
   * Get all available the tags for an image
   */
  abstract fun getTags(account: String, organization: String, image: String): List<String>

  /**
   * Get the digest for a specific image
   */
  abstract fun getDigest(account: String, organization: String, image: String, tag: String): String

  override fun invoke(resource: Resource<T>): Resource<T> {
    val container = getContainerFromSpec(resource)
    if (container is ContainerWithDigest) {
      return resource
    }
    val account = getAccountFromSpec(resource)
    val deliveryConfig = deliveryConfigRepository.deliveryConfigFor(resource.id)
    val environment = deliveryConfigRepository.environmentFor(resource.id)
    val tag: String = if (deliveryConfig != null && environment != null) {
      findTagGivenDeliveryConfig(deliveryConfig, environment, container.toArtifact())
    } else {
      findTagGivenStrategy(account, container as ContainerWithVersionedTag)
    }

    val newContainer = getContainer(account, container, tag)
    return updateContainerInSpec(resource, newContainer)
  }

  fun findTagGivenDeliveryConfig(deliveryConfig: DeliveryConfig, environment: Environment, artifact: DeliveryArtifact) =
    artifactRepository.latestVersionApprovedIn(
      deliveryConfig,
      artifact,
      environment.name,
      enumValues<ArtifactStatus>().toList()
    ) ?: throw NoDockerImageSatisfiesConstraints(artifact.name, environment.name)

  fun findTagGivenStrategy(
    account: String,
    container: ContainerWithVersionedTag
  ): String {
    val tags = getTags(account, container.organization, container.image)
    return DockerComparator.sort(tags, container.tagVersionStrategy, container.captureGroupRegex).first()
  }

  fun getContainer(
    account: String,
    container: Container,
    tag: String
  ): ContainerWithDigest {
    val digest = getDigest(account, container.organization, container.image, tag)
    return ContainerWithDigest(
      organization = container.organization,
      image = container.image,
      digest = digest
    )
  }

  private fun Container.toArtifact() =
    DeliveryArtifact(repository(), DOCKER)
}
