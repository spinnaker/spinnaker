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
import com.netflix.spinnaker.keel.api.Environment
import com.netflix.spinnaker.keel.api.Resource
import com.netflix.spinnaker.keel.api.artifacts.TagVersionStrategy.SEMVER_TAG
import com.netflix.spinnaker.keel.artifacts.DockerArtifact
import com.netflix.spinnaker.keel.exceptions.NoDockerImageSatisfiesConstraints
import com.netflix.spinnaker.keel.persistence.KeelRepository
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import io.mockk.every
import io.mockk.mockk
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
  val repository: KeelRepository = mockk()

  private val artifacts = setOf(
    DockerArtifact(name = "spkr/keeldemo", reference = "spkr/keeldemo", tagVersionStrategy = SEMVER_TAG, deliveryConfigName = "mydeliveryconfig"),
    DockerArtifact(name = "spkr/anotherkeeldemo", reference = "spkr/anotherkeeldemo", tagVersionStrategy = SEMVER_TAG, deliveryConfigName = "mydeliveryconfig")
  )


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

  private val multiReferenceSpec = SampleSpecWithContainer(
    container = MultiReferenceContainerProvider(
      references = setOf("spkr/keeldemo", "spkr/anotherkeeldemo")
    ),
    account = "test"
  )

  private val nullContainerSpec = SampleSpecWithContainer(
    account = "test"
  )

  val versionedContainerResource = generateResource(oldStyleSpec)
  val referenceResource = generateResource(referenceSpec)
  val multiReferenceResource = generateResource(multiReferenceSpec)
  val nullContainerResource = generateResource(nullContainerSpec)

  val versionedDeliveryConfig = generateDeliveryConfig(versionedContainerResource, artifacts)
  val referenceDeliveryConfig = generateDeliveryConfig(referenceResource, artifacts)
  val multiReferenceDeliveryConfig = generateDeliveryConfig(multiReferenceResource, artifacts)
  val nullContainerDeliveryConfig = generateDeliveryConfig(nullContainerResource, artifacts)

  private fun generateResource(spec: SampleSpecWithContainer) =
    Resource(
      kind = SAMPLE_API_VERSION.qualify("sample"),
      spec = spec,
      metadata = mapOf(
        "id" to "${SAMPLE_API_VERSION.group}:sample:sample-resource",
        "application" to "myapp",
        "serviceAccount" to "keel@spinnaker"
      )
    )

  private fun generateDeliveryConfig(resource: Resource<SampleSpecWithContainer>, artifacts: Set<DockerArtifact>): DeliveryConfig {
    val env = Environment(
      name = "test",
      resources = setOf(resource)
    )
    return DeliveryConfig(
      name = "mydeliveryconfig",
      application = "keel",
      serviceAccount = "keel@spinnaker",
      artifacts = artifacts,
      environments = setOf(env)
    )
  }

  fun tests() = rootContext<SampleDockerImageResolver> {
    fixture {
      SampleDockerImageResolver(repository)
    }

    context("resolving from versioned tag provider") {
      before {
        every { repository.deliveryConfigFor(versionedContainerResource.id) } returns versionedDeliveryConfig
        every { repository.environmentFor(versionedContainerResource.id) } returns versionedDeliveryConfig.environments.first()
      }

      context("no versions are approved yet") {
        before {
          every { repository.latestVersionApprovedIn(versionedDeliveryConfig, artifacts.first(), "test") } returns null
        }

        test("the resolver throws an exception") {
          expectThrows<NoDockerImageSatisfiesConstraints> { this.invoke(versionedContainerResource) }
        }
      }

      context("there is a version approved") {
        before {
          every { repository.latestVersionApprovedIn(versionedDeliveryConfig, artifacts.first(), "test") } returns "v0.0.1"
        }

        test("the resolver finds the correct version") {
          val resolvedResource = this.invoke(versionedContainerResource)
          expect {
            that(resolvedResource.spec.container).isA<DigestProvider>()
            that(resolvedResource.spec.container as DigestProvider).get { digest }.isEqualTo("sha256:2763a2b9d53e529c62b326b7331d1b44aae344be0b79ff64c74559c5c96b76b7")
          }
        }
      }
    }

    context("resolving from reference provider") {
      before {
        every { repository.deliveryConfigFor(referenceResource.id) } returns referenceDeliveryConfig
        every { repository.environmentFor(referenceResource.id) } returns referenceDeliveryConfig.environments.first()
      }

      context("no versions are approved yet") {
        before {
          every { repository.latestVersionApprovedIn(referenceDeliveryConfig, artifacts.first(), "test") } returns null
        }

        test("the resolver throws an exception") {
          expectThrows<NoDockerImageSatisfiesConstraints> { this.invoke(referenceResource) }
        }
      }

      context("there is a version approved") {
        before {
          every { repository.latestVersionApprovedIn(referenceDeliveryConfig, artifacts.first(), "test") } returns "v0.0.1"
        }

        test("the resolver finds the correct version") {
          val resolvedResource = this.invoke(referenceResource)
          expect {
            that(resolvedResource.spec.container).isA<DigestProvider>()
            that(resolvedResource.spec.container as DigestProvider).get { digest }.isEqualTo("sha256:2763a2b9d53e529c62b326b7331d1b44aae344be0b79ff64c74559c5c96b76b7")
          }
        }
      }
    }

    context("resolving from multiple reference providers") {
      before {
        every { repository.deliveryConfigFor(multiReferenceResource.id) } returns multiReferenceDeliveryConfig
        every { repository.environmentFor(multiReferenceResource.id) } returns multiReferenceDeliveryConfig.environments.first()
      }

      context("no versions are approved yet") {
        before {
          every { repository.latestVersionApprovedIn(multiReferenceDeliveryConfig, artifacts.elementAt(0), "test") } returns null
          every { repository.latestVersionApprovedIn(multiReferenceDeliveryConfig, artifacts.elementAt(1), "test") } returns null
        }

        test("the resolver throws an exception") {
          expectThrows<NoDockerImageSatisfiesConstraints> { this.invoke(multiReferenceResource) }
        }
      }

      // Given the updateContainerInSpec behavior in SimpleDockerImageResolver, the test verifies that
      // for multiple container references, the resource specification goes through the resolver twice
      // and that the container reference is eventually overwritten by the last artifact. Hence the
      // test verifies that the digest for the resolvedResource will be that of the second artifact.
      context("there is a version approved for each artifact") {
        before {
          every { repository.latestVersionApprovedIn(multiReferenceDeliveryConfig, artifacts.elementAt(0), "test") } returns "v0.0.1"
          every { repository.latestVersionApprovedIn(multiReferenceDeliveryConfig, artifacts.elementAt(1), "test") } returns "v0.0.2"
        }

        test("the resolver overrides to the latest artifact") {
          val resolvedResource = this.invoke(multiReferenceResource)
          expect {
            that(resolvedResource.spec.container).isA<DigestProvider>()
            that(resolvedResource.spec.container as DigestProvider).get { digest }.isEqualTo("sha256:b4857d7596462aeb1977e6e5d1e31b20a5b5eecf890cd64ac62f145b3839ee97")
          }
        }
      }
    }

    context("resolving from resource with no container info") {
      before{
        every { repository.deliveryConfigFor(nullContainerResource.id) } returns nullContainerDeliveryConfig
        every { repository.environmentFor(nullContainerResource.id) } returns nullContainerDeliveryConfig.environments.first()
      }

      test("resource is resolved as is") {
        val resolvedResource = this.invoke(nullContainerResource)
        expect {
          that(resolvedResource).isEqualTo(nullContainerResource)
        }
      }
    }
  }
}
