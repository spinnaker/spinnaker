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

import com.netflix.spinnaker.keel.api.DeliveryConfig
import com.netflix.spinnaker.keel.api.DockerArtifact
import com.netflix.spinnaker.keel.api.Environment
import com.netflix.spinnaker.keel.api.Resource
import com.netflix.spinnaker.keel.api.TagVersionStrategy.SEMVER_TAG
import com.netflix.spinnaker.keel.exceptions.NoDockerImageSatisfiesConstraints
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

  private val artifact = DockerArtifact(name = "spkr/keeldemo", reference = "spkr/keeldemo", tagVersionStrategy = SEMVER_TAG, deliveryConfigName = "mydeliveryconfig")

  private val oldStyleSpec = SampleSpecWithContainer(
    container = VersionedTagProvider(
      organization = "spkr",
      image = "keeldemo",
      tagVersionStrategy = SEMVER_TAG
    ),
    account = "test"
  )

  private val referenceSpec = SampleSpecWithContainer(
    container = ReferenceProvider(
      reference = "spkr/keeldemo"
    ),
    account = "test"
  )
  val versionedContainerResource = generateResource(oldStyleSpec)

  val referenceResource = generateResource(referenceSpec)
  val versionedDeliveryConfig = generateDeliveryConfig(versionedContainerResource, artifact)

  val referenceDeliveryConfig = generateDeliveryConfig(referenceResource, artifact)

  private fun generateResource(spec: SampleSpecWithContainer) =
    Resource(
      apiVersion = SAMPLE_API_VERSION,
      kind = "sample",
      spec = spec,
      metadata = mapOf(
        "id" to "${SAMPLE_API_VERSION.substringBefore(".")}:sample:sample-resource",
        "application" to "myapp",
        "serviceAccount" to "keel@spinnaker"
      ))

  private fun generateDeliveryConfig(resource: Resource<SampleSpecWithContainer>, artifact: DockerArtifact): DeliveryConfig {
    val env = Environment(
      name = "test",
      resources = setOf(resource)
    )
    return DeliveryConfig(
      name = "mydeliveryconfig",
      application = "keel",
      serviceAccount = "keel@spinnaker",
      artifacts = setOf(artifact),
      environments = setOf(env)
    )
  }

  fun tests() = rootContext<SampleDockerImageResolver> {
    fixture {
      SampleDockerImageResolver(configRepo, artifactRepo)
    }

    context("resolving from versioned tag provider") {
      before {
        configRepo.store(versionedDeliveryConfig)
        artifactRepo.register(artifact)
      }
      after {
        configRepo.dropAll()
        artifactRepo.dropAll()
      }

      test("no versions throws exception") {
        expectThrows<NoDockerImageSatisfiesConstraints> { this.invoke(versionedContainerResource) }
      }

      test("no versions approved yet") {
        artifactRepo.store(artifact, "v0.0.1", null)
        expectThrows<NoDockerImageSatisfiesConstraints> { this.invoke(versionedContainerResource) }
      }

      test("there is a version approved") {
        artifactRepo.store(artifact, "v0.0.1", null)
        artifactRepo.approveVersionFor(versionedDeliveryConfig, artifact, "v0.0.1", "test")

        val resolvedResource = this.invoke(versionedContainerResource)
        expect {
          that(resolvedResource.spec.container).isA<DigestProvider>()
          that(resolvedResource.spec.container as DigestProvider).get { digest }.isEqualTo("sha256:2763a2b9d53e529c62b326b7331d1b44aae344be0b79ff64c74559c5c96b76b7")
        }
      }
    }

    context("resolving from reference provider") {
      before {
        configRepo.store(referenceDeliveryConfig)
        artifactRepo.register(artifact)
      }
      after {
        configRepo.dropAll()
        artifactRepo.dropAll()
      }
      test("no versions throws exception") {
        expectThrows<NoDockerImageSatisfiesConstraints> { this.invoke(referenceResource) }
      }

      test("no versions approved yet") {
        artifactRepo.store(artifact, "v0.0.1", null)
        expectThrows<NoDockerImageSatisfiesConstraints> { this.invoke(referenceResource) }
      }

      test("there is a version approved") {
        artifactRepo.store(artifact, "v0.0.1", null)
        artifactRepo.approveVersionFor(referenceDeliveryConfig, artifact, "v0.0.1", "test")

        val resolvedResource = this.invoke(referenceResource)
        expect {
          that(resolvedResource.spec.container).isA<DigestProvider>()
          that(resolvedResource.spec.container as DigestProvider).get { digest }.isEqualTo("sha256:2763a2b9d53e529c62b326b7331d1b44aae344be0b79ff64c74559c5c96b76b7")
        }
      }
    }
  }
}
