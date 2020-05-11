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
import com.netflix.spinnaker.keel.api.artifacts.DockerArtifact
import com.netflix.spinnaker.keel.api.artifacts.TagVersionStrategy.SEMVER_TAG
import com.netflix.spinnaker.keel.api.id
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
      kind = SAMPLE_API_VERSION.qualify("sample"),
      spec = spec,
      metadata = mapOf(
        "id" to "${SAMPLE_API_VERSION.group}:sample:sample-resource",
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
      SampleDockerImageResolver(repository)
    }

    context("resolving from versioned tag provider") {
      before {
        every { repository.deliveryConfigFor(versionedContainerResource.id) } returns versionedDeliveryConfig
        every { repository.environmentFor(versionedContainerResource.id) } returns versionedDeliveryConfig.environments.first()
      }

      context("no versions are approved yet") {
        before {
          every { repository.latestVersionApprovedIn(versionedDeliveryConfig, artifact, "test") } returns null
        }

        test("the resolver throws an exception") {
          expectThrows<NoDockerImageSatisfiesConstraints> { this.invoke(versionedContainerResource) }
        }
      }

      context("there is a version approved") {
        before {
          every { repository.latestVersionApprovedIn(versionedDeliveryConfig, artifact, "test") } returns "v0.0.1"
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
          every { repository.latestVersionApprovedIn(referenceDeliveryConfig, artifact, "test") } returns null
        }

        test("the resolver throws an exception") {
          expectThrows<NoDockerImageSatisfiesConstraints> { this.invoke(versionedContainerResource) }
        }
      }

      context("there is a version approved") {
        before {
          every { repository.latestVersionApprovedIn(referenceDeliveryConfig, artifact, "test") } returns "v0.0.1"
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
  }
}
