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
package com.netflix.spinnaker.titus

import com.netflix.spinnaker.keel.api.Resource
import com.netflix.spinnaker.keel.api.titus.TitusClusterSpec
import com.netflix.spinnaker.keel.artifacts.DockerArtifact
import com.netflix.spinnaker.keel.clouddriver.CloudDriverCache
import com.netflix.spinnaker.keel.clouddriver.CloudDriverService
import com.netflix.spinnaker.keel.docker.ContainerProvider
import com.netflix.spinnaker.keel.docker.DockerImageResolver
import com.netflix.spinnaker.keel.persistence.KeelRepository
import com.netflix.spinnaker.titus.exceptions.NoDigestFound
import com.netflix.spinnaker.titus.exceptions.RegistryNotFound
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * Assumption: docker container digest is the same in all regions
 */
@Component
class TitusImageResolver(
  repository: KeelRepository,
  private val cloudDriverCache: CloudDriverCache,
  private val cloudDriverService: CloudDriverService
) : DockerImageResolver<TitusClusterSpec>(
  repository
) {
  override val supportedKind = TITUS_CLUSTER_V1

  private val log by lazy { LoggerFactory.getLogger(javaClass) }

  override fun getContainerFromSpec(resource: Resource<TitusClusterSpec>) =
    resource.spec.container

  override fun getAccountFromSpec(resource: Resource<TitusClusterSpec>) =
    resource.spec.deriveRegistry()

  override fun updateContainerInSpec(
    resource: Resource<TitusClusterSpec>,
    container: ContainerProvider,
    artifact: DockerArtifact,
    tag: String
  ) =
    resource.copy(
      spec = resource.spec.copy(
        container = container,
        _artifactName = artifact.name,
        artifactVersion = tag
      )
    )

  override fun getTags(account: String, organization: String, image: String) =
    runBlocking {
      val repository = "$organization/$image"
      cloudDriverService.findDockerTagsForImage(account, repository)
    }

  override fun getDigest(account: String, organization: String, image: String, tag: String) =
    runBlocking {
      val repository = "$organization/$image"
      val images = cloudDriverService.findDockerImages(account, repository, tag)
      images.firstOrNull()?.digest
        ?: throw NoDigestFound(repository, tag) // sha should be the same in all accounts for titus
    }

  protected fun TitusClusterSpec.deriveRegistry(): String =
    cloudDriverCache.credentialBy(locations.account).attributes["registry"]?.toString()
      ?: throw RegistryNotFound(locations.account)
}
