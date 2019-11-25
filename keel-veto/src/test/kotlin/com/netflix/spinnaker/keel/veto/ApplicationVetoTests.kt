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
package com.netflix.spinnaker.keel.veto

import com.netflix.spinnaker.keel.api.ArtifactStatus
import com.netflix.spinnaker.keel.api.Resource
import com.netflix.spinnaker.keel.api.ResourceId
import com.netflix.spinnaker.keel.api.SPINNAKER_API_V1
import com.netflix.spinnaker.keel.bakery.api.BaseLabel
import com.netflix.spinnaker.keel.bakery.api.ImageSpec
import com.netflix.spinnaker.keel.bakery.api.StoreType
import com.netflix.spinnaker.keel.persistence.memory.InMemoryApplicationVetoRepository
import com.netflix.spinnaker.keel.persistence.memory.InMemoryResourceRepository
  import com.netflix.spinnaker.keel.plugin.SupportedKind
import com.netflix.spinnaker.keel.serialization.configuredObjectMapper
import com.netflix.spinnaker.keel.veto.application.ApplicationVeto
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import strikt.api.expectThat
import strikt.assertions.contains
import strikt.assertions.hasSize
import strikt.assertions.isFalse
import strikt.assertions.isTrue

class ApplicationVetoTests : JUnit5Minutests {
  private val appName = "keeldemo"
  private val friggaAppName = "test"
  private val clusterResourceId = ResourceId("ec2:securityGroup:test:us-west-2:$appName-managed")

  private val imageId = "bakery:image:$friggaAppName-$appName"
  private val imageResourceId = ResourceId(imageId)
  private val imageSpec = ImageSpec(
    artifactName = "$friggaAppName-$appName",
    baseLabel = BaseLabel.RELEASE,
    baseOs = "bionic",
    regions = setOf("us-west-1"),
    storeType = StoreType.EBS,
    artifactStatuses = listOf(ArtifactStatus.FINAL),
    application = appName
  )
  private val imageKind = SupportedKind(SPINNAKER_API_V1.subApi("bakery"), "image", ImageSpec::class.java)
  private val imageResource: Resource<ImageSpec> = Resource(
    apiVersion = imageKind.apiVersion,
    kind = imageKind.kind,
    metadata = mapOf(
      "application" to appName,
      "id" to imageId,
      "serviceAccount" to "fnord@keel"
    ),
    spec = imageSpec
  )

  internal

  class Fixture {
    val vetoRepository = InMemoryApplicationVetoRepository()
    val resourceRepository = InMemoryResourceRepository()
    val subject = ApplicationVeto(vetoRepository, resourceRepository, configuredObjectMapper())
  }

  fun tests() = rootContext<Fixture> {
    fixture { Fixture() }

    context("testing opt in flow") {
      after {
        vetoRepository.flush()
      }

      test("when no applications are opted out we allow any app") {
        val response = subject.check(clusterResourceId)
        expectThat(response.allowed).isTrue()
      }

      test("when myapp is excluded, we don't allow it") {
        subject.passMessage(mapOf(
          "application" to appName,
          "optedOut" to true
        ))

        val response = subject.check(clusterResourceId)
        expectThat(response.allowed).isFalse()
      }

      test("opting in/out works") {
        subject.passMessage(mapOf(
          "application" to appName,
          "optedOut" to true
        ))

        expectThat(subject.currentRejections())
          .hasSize(1)
          .contains(appName)

        subject.passMessage(mapOf(
          "application" to appName,
          "optedOut" to false
        ))

        expectThat(subject.currentRejections())
          .hasSize(0)
      }
    }

    context("testing an image with a different appname according to frigga") {
      after {
        vetoRepository.flush()
      }

      before {
        resourceRepository.store(imageResource)
      }

      test("resource application differs from frigga application, where resource app is vetoed") {
        subject.passMessage(mapOf(
          "application" to appName,
          "optedOut" to true
        ))

        val responseById = subject.check(imageResourceId)
        expectThat(responseById.allowed).isFalse()

        val responseBySpec = subject.check(imageResource)
        expectThat(responseBySpec.allowed).isFalse()
      }

      test("resource application differs from frigga application, where only frigga app is vetoed") {
        subject.passMessage(mapOf(
          "application" to friggaAppName,
          "optedOut" to true
        ))

        val responseById = subject.check(imageResourceId)
        expectThat(responseById.allowed).isTrue()

        val responseBySpec = subject.check(imageResource)
        expectThat(responseBySpec.allowed).isTrue()
      }

      test("fallback to frigga for unpersisted resources") {
        resourceRepository.dropAll()

        subject.passMessage(mapOf(
          "application" to friggaAppName,
          "optedOut" to true
        ))

        val responseBy = subject.check(imageResourceId)
        expectThat(responseBy.allowed).isFalse()
      }
    }
  }
}
