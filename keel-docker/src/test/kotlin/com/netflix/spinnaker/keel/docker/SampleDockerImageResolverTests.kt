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

import com.netflix.spinnaker.keel.api.ArtifactStatus.FINAL
import com.netflix.spinnaker.keel.api.ArtifactType.DOCKER
import com.netflix.spinnaker.keel.api.DeliveryArtifact
import com.netflix.spinnaker.keel.api.DeliveryConfig
import com.netflix.spinnaker.keel.api.Environment
import com.netflix.spinnaker.keel.api.Resource
import com.netflix.spinnaker.keel.docker.TagVersionStrategy.SEMVER_TAG
import com.netflix.spinnaker.keel.persistence.memory.InMemoryArtifactRepository
import com.netflix.spinnaker.keel.persistence.memory.InMemoryDeliveryConfigRepository
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import strikt.api.expect
import strikt.api.expectThrows
import strikt.assertions.isA
import strikt.assertions.isEqualTo

/**
 * Tests of the Docker Image Resolver with a sample implementation of the resolver.
 * The goal of this test is to test the overall logic of resolving the sha from a list of tags, but not the
 * underlying logic of pulling the right tag or right digest.
 */
class SampleDockerImageResolverTests : JUnit5Minutests {

  val configRepo: InMemoryDeliveryConfigRepository = InMemoryDeliveryConfigRepository()
  val artifactRepo: InMemoryArtifactRepository = InMemoryArtifactRepository()
  private val spec = SampleSpecWithContainer(
    container = ContainerWithVersionedTag(
      organization = "spkr",
      image = "keeldemo",
      tagVersionStrategy = SEMVER_TAG
    ),
    account = "test"
  )

  private val resource = Resource(
    apiVersion = SAMPLE_API_VERSION,
    kind = "sample",
    spec = spec,
    metadata = mapOf(
      "id" to "${SAMPLE_API_VERSION.prefix}:sample:sample-resource",
      "application" to "myapp",
      "serviceAccount" to "keel@spinnaker"
    ))

  private val artifact = DeliveryArtifact(
    name = "spkr/keeldemo",
    type = DOCKER
  )

  private val env = Environment(
    name = "test",
    resources = setOf(resource)
  )
  private val deliveryConfig = DeliveryConfig(
    name = "mydeliveryconfig",
    application = "keel",
    artifacts = setOf(artifact),
    environments = setOf(env)
  )

  fun tests() = rootContext<SampleDockerImageResolver> {
    fixture {
      SampleDockerImageResolver(configRepo, artifactRepo)
    }

    context("a semantic versioned resource") {
      test("resolves latest to correct digest") {
        val resolvedResource = this.invoke(resource)
        expect {
          that(resolvedResource.spec.container).isA<ContainerWithDigest>()
          that(resolvedResource.spec.container as ContainerWithDigest).get { digest }.isEqualTo("sha256:0d49cebbbb00d5cefd384fcbb2f46c97b7412b59b62862a3459f09232d6acf28")
        }
      }
    }

    context("a resource part of a delivery config") {
      before {
        configRepo.store(deliveryConfig)
        artifactRepo.register(artifact)
      }

      after {
        configRepo.dropAll()
        artifactRepo.dropAll()
      }

      test("no versions throws exception") {
        expectThrows<NoDockerImageSatisfiesConstraints> { this.invoke(resource) }
      }

      test("no versions approved yet") {
        artifactRepo.store(artifact, "v0.0.1", FINAL)
        expectThrows<NoDockerImageSatisfiesConstraints> { this.invoke(resource) }
      }

      test("there is a version approved") {
        artifactRepo.store(artifact, "v0.0.1", FINAL)
        artifactRepo.approveVersionFor(deliveryConfig, artifact, "v0.0.1", env.name)

        val resolvedResource = this.invoke(resource)
        expect {
          that(resolvedResource.spec.container).isA<ContainerWithDigest>()
          that(resolvedResource.spec.container as ContainerWithDigest).get { digest }.isEqualTo("sha256:2763a2b9d53e529c62b326b7331d1b44aae344be0b79ff64c74559c5c96b76b7")
        }
      }
    }
  }
}
