/*
 * Copyright 2018 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.spinnaker.keel.persistence

import com.netflix.spinnaker.keel.api.ApiVersion
import com.netflix.spinnaker.keel.api.Asset
import com.netflix.spinnaker.keel.api.AssetMetadata
import com.netflix.spinnaker.keel.api.AssetName
import com.netflix.spinnaker.keel.api.SPINNAKER_API_V1
import com.netflix.spinnaker.keel.persistence.AssetState.Diff
import com.netflix.spinnaker.keel.persistence.AssetState.Ok
import com.netflix.spinnaker.keel.persistence.AssetState.Unknown
import com.nhaarman.mockito_kotlin.argumentCaptor
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.reset
import com.nhaarman.mockito_kotlin.times
import com.nhaarman.mockito_kotlin.verify
import com.nhaarman.mockito_kotlin.verifyZeroInteractions
import com.oneeyedmen.minutest.junit.junitTests
import org.junit.jupiter.api.TestFactory
import strikt.api.expectThat
import strikt.api.expectThrows
import strikt.assertions.containsExactlyInAnyOrder
import strikt.assertions.first
import strikt.assertions.hasSize
import strikt.assertions.isEqualTo
import strikt.assertions.isNotNull
import strikt.assertions.map
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.util.UUID.randomUUID

abstract class AssetRepositoryTests<T : AssetRepository> {

  abstract fun factory(clock: Clock): T

  open fun flush() {}

  data class Fixture<T : AssetRepository>(
    val subject: T,
    val callback: (Triple<AssetName, ApiVersion, String>) -> Unit
  )

  @TestFactory
  fun `an asset repository`() = junitTests<Fixture<T>>() {

    fixture {
      val clock = Clock.fixed(Instant.now(), ZoneId.systemDefault())
      Fixture(
        subject = factory(clock),
        callback = mock()
      )
    }

    after { reset(callback) }
    after { flush() }

    context("no assets exist") {
      test("allAssets is a no-op") {
        subject.allAssets(callback)

        verifyZeroInteractions(callback)
      }
    }

    context("an asset exists") {
      val asset = Asset(
        apiVersion = SPINNAKER_API_V1,
        metadata = AssetMetadata(
          name = AssetName("SecurityGroup:ec2:test:us-west-2:fnord"),
          resourceVersion = 1234L,
          uid = randomUUID()
        ),
        kind = "ec2:SecurityGroup",
        spec = randomData()
      )

      before {
        subject.store(asset)
      }

      test("it is returned by allAssets") {
        subject.allAssets(callback)

        verify(callback).invoke(Triple(asset.metadata.name, asset.apiVersion, asset.kind))
      }

      test("it can be retrieved by id") {
        val retrieved = subject.get<Map<String, Any>>(asset.metadata.name)
        expectThat(retrieved).isEqualTo(asset)
      }

      test("its id can be retrieved by name") {

      }

      test("its state is unknown") {
        expectThat(subject.lastKnownState(asset.metadata.name))
          .isNotNull()
          .first
          .isEqualTo(Unknown)
      }

      context("storing another asset with a different id") {
        val anotherAsset = Asset(
          metadata = AssetMetadata(
            name = AssetName("SecurityGroup:ec2:test:us-east-1:fnord"),
            resourceVersion = 1234L,
            uid = randomUUID()
          ),
          apiVersion = SPINNAKER_API_V1,
          kind = "ec2:SecurityGroup",
          spec = randomData()
        )

        before {
          subject.store(anotherAsset)
        }

        test("it does not overwrite the first asset") {
          subject.allAssets(callback)

          argumentCaptor<Triple<AssetName, ApiVersion, String>>().apply {
            verify(callback, times(2)).invoke(capture())
            expectThat(allValues)
              .hasSize(2)
              .map { it.first }
              .containsExactlyInAnyOrder(asset.metadata.name, anotherAsset.metadata.name)
          }
        }
      }


      context("storing a new version of the asset") {
        val updatedAsset = asset.copy(
          spec = randomData()
        )

        before {
          subject.store(updatedAsset)
        }

        test("it replaces the original asset") {
          expectThat(subject.get<Map<String, Any>>(asset.metadata.name))
            .get(Asset<*>::spec)
            .isEqualTo(updatedAsset.spec)
        }
      }

      context("updating the state of the asset") {
        before {
          subject.updateState(asset.metadata.name, Ok)
        }

        test("it reports the new state") {
          expectThat(subject.lastKnownState(asset.metadata.name))
            .isNotNull()
            .first
            .isEqualTo(Ok)
        }

        context("updating the state again") {
          before {
            subject.updateState(asset.metadata.name, Diff)
          }

          test("it reports the newest state") {
            expectThat(subject.lastKnownState(asset.metadata.name))
              .isNotNull()
              .first
              .isEqualTo(Diff)
          }
        }
      }

      context("deleting the asset") {
        before {
          subject.delete(asset.metadata.name)
        }

        test("the asset is no longer returned by all assets") {
          subject.allAssets(callback)

          verifyZeroInteractions(callback)
        }

        test("the asset can no longer be retrieved by id") {
          expectThrows<NoSuchAssetException> {
            subject.get<Map<String, Any>>(asset.metadata.name)
          }
        }
      }
    }
  }
}

fun randomData(length: Int = 4): Map<String, Any> {
  val map = mutableMapOf<String, Any>()
  (0 until length).forEach { _ ->
    map[randomString()] = randomString()
  }
  return map
}

fun randomString(length: Int = 8) =
  randomUUID()
    .toString()
    .map { it.toInt().toString(16) }
    .joinToString("")
    .substring(0 until length)
