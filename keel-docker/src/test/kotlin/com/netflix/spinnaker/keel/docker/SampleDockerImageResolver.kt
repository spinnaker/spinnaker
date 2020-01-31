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

import com.fasterxml.jackson.annotation.JsonIgnore
import com.netflix.spinnaker.keel.api.Resource
import com.netflix.spinnaker.keel.api.ResourceSpec
import com.netflix.spinnaker.keel.persistence.ArtifactRepository
import com.netflix.spinnaker.keel.persistence.DeliveryConfigRepository

class SampleDockerImageResolver(
  deliveryConfigRepository: DeliveryConfigRepository,
  artifactRepository: ArtifactRepository
) : DockerImageResolver<SampleSpecWithContainer>(
  deliveryConfigRepository,
  artifactRepository
) {

  override val apiVersion: String = SAMPLE_API_VERSION
  override val supportedKind: String = "sample"

  override fun getContainerFromSpec(resource: Resource<SampleSpecWithContainer>) =
    resource.spec.container

  override fun getAccountFromSpec(resource: Resource<SampleSpecWithContainer>) =
    resource.spec.account

  override fun updateContainerInSpec(resource: Resource<SampleSpecWithContainer>, container: ContainerProvider) =
    resource.copy(spec = resource.spec.copy(container = container))

  // this would normally call out to clouddriver
  override fun getTags(account: String, organization: String, image: String) =
    listOf("latest", "v0.0.1", "v0.0.2", "v0.0.4", "v0.1.1", "v0.1.0")

  // this would normally call out to clouddriver
  override fun getDigest(account: String, organization: String, image: String, tag: String) =
    when (tag) {
      "v0.0.1" -> "sha256:2763a2b9d53e529c62b326b7331d1b44aae344be0b79ff64c74559c5c96b76b7"
      "v0.0.2" -> "sha256:b4857d7596462aeb1977e6e5d1e31b20a5b5eecf890cd64ac62f145b3839ee97"
      "v0.0.4" -> "sha256:85741705089153592531668168c265bacd79d71368d14a956140ccc446d6f9aa"
      "v0.1.0" -> "sha256:5854562820b00313022005bdad50efa09c267777daf178aad108faaf10d7ce08"
      "v0.1.1" -> "sha256:0d49cebbbb00d5cefd384fcbb2f46c97b7412b59b62862a3459f09232d6acf28"
      else -> "sha256:c5d3b32d4af6bae7fb337d1df32f32d2e6fd2fba622d98743280c2d0bf47dbe1"
    }
}

data class SampleSpecWithContainer(
  val container: ContainerProvider,
  val account: String
) : ResourceSpec {
  @JsonIgnore
  override val id: String = "sample-resource"

  @JsonIgnore
  override val application: String = "myapp"
}

val SAMPLE_API_VERSION = "sample.resource/v1"
