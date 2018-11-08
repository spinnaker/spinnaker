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
package com.netflix.spinnaker.keel.registry

import com.oneeyedmen.minutest.junit.junitTests
import org.junit.jupiter.api.TestFactory
import strikt.api.Assertion
import strikt.api.expectThat
import strikt.assertions.containsExactly
import strikt.assertions.containsExactlyInAnyOrder
import strikt.assertions.isEqualTo
import strikt.assertions.isFalse
import strikt.assertions.isNotNull
import strikt.assertions.isNull
import strikt.assertions.isTrue

abstract class PluginRepositoryTests<T : PluginRepository> {

  abstract fun factory(): T

  open fun clear(subject: T) {}

  val securityGroup = AssetType(
    kind = "ec2:SecurityGroup",
    apiVersion = "1.0"
  )
  val loadBalancer = AssetType(
    kind = "ec2:LoadBalancer",
    apiVersion = "1.0"
  )

  inner class Fixture(val subject: T)

  @TestFactory
  fun `getting plugins from the registry`() = junitTests<Fixture> {
    fixture {
      Fixture(factory())
    }

    after {
      clear(subject)
    }

    context("no plugins are stored") {
      test("it returns null from assetPluginsFor") {
        expectThat(subject.assetPluginFor(securityGroup)).isNull()
      }

      test("it returns no asset plugins") {
        expectThat(subject.assetPlugins()).isEmpty()
      }

      test("it returns no veto plugins") {
        expectThat(subject.vetoPlugins()).isEmpty()
      }

      test("it returns no plugins") {
        expectThat(subject.allPlugins()).isEmpty()
      }
    }

    context("an asset plugin is registered") {
      val address = PluginAddress("EC2 security group", "${securityGroup.kind}.vip", 6565)

      before {
        subject.addAssetPluginFor(securityGroup, address)
      }

      test("it returns the plugin address in the list of all plugins") {
        expectThat(subject.allPlugins()).containsExactly(address)
      }

      test("it returns the plugin in the list of asset plugins") {
        expectThat(subject.assetPlugins()).containsExactly(address)
      }

      test("it returns the plugin address by type") {
        expectThat(subject.assetPluginFor(securityGroup))
          .isNotNull()
          .isEqualTo(address)
      }

      test("it does not return the plugin address for a different type") {
        expectThat(subject.assetPluginFor(loadBalancer)).isNull()
      }
    }

    context("an asset plugin supporting multiple asset kinds is registered") {
      val address = PluginAddress("Amazon security group", "ec2plugin.vip", 6565)

      before {
        subject.addAssetPluginFor(securityGroup, address)
        subject.addAssetPluginFor(loadBalancer, address)
      }

      test("it returns the plugin only once in the list of all plugins") {
        expectThat(subject.allPlugins()).containsExactly(address)
      }

      test("it returns the plugin only once in the list of asset plugins") {
        expectThat(subject.assetPlugins()).containsExactly(address)
      }
    }

    context("a veto plugin is registered") {
      val address1 = PluginAddress("Veto 1", "veto1.vip", 6565)
      val address2 = PluginAddress("Veto 2", "veto2.vip", 6565)

      before {
        with(subject) {
          addVetoPlugin(address1)
          addVetoPlugin(address2)
        }
      }

      test("it returns the plugin address in the list of all plugins") {
        expectThat(subject.allPlugins()) {
          containsExactlyInAnyOrder(address1, address2)
        }
      }

      test("it returns the plugin") {
        expectThat(subject.vetoPlugins()) {
          containsExactlyInAnyOrder(address1, address2)
        }
      }
    }
  }

  @TestFactory
  fun `registering plugins`() = junitTests<Fixture> {
    val address = PluginAddress("EC2 security group", "${securityGroup.kind}.vip", 6565)

    fixture { Fixture(factory()) }

    after {
      clear(subject)
    }

    context("an asset plugin is not already registered") {
      test("registering it returns true") {
        expectThat(subject.addAssetPluginFor(securityGroup, address)).isTrue()
      }
    }

    context("an asset plugin is already registered") {
      before {
        subject.addAssetPluginFor(securityGroup, address)
      }

      test("registering it returns false") {
        expectThat(subject.addAssetPluginFor(securityGroup, address)).isFalse()
      }
    }
  }
}

fun <T : Iterable<E>, E> Assertion.Builder<T>.isEmpty() =
  assert("is empty") {
    if (it.iterator().hasNext()) fail() else pass()
  }
