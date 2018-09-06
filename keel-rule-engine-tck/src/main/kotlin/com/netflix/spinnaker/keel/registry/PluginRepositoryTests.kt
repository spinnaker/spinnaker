package com.netflix.spinnaker.keel.registry

import com.netflix.spinnaker.keel.api.TypeMetadata
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.given
import org.jetbrains.spek.api.dsl.it
import strikt.api.Assertion
import strikt.api.expect
import strikt.assertions.containsExactlyInAnyOrder
import strikt.assertions.isEqualTo
import strikt.assertions.isNotNull
import strikt.assertions.isNull

abstract class PluginRepositoryTests<T : PluginRepository>(
  factory: () -> T,
  shutdownHook: () -> Unit = {}
) : Spek({

  val subject = factory()

  val securityGroup = TypeMetadata.newBuilder().apply {
    apiVersion = "1.0"
    kind = "aws:SecurityGroup"
  }.build()
  val loadBalancer = TypeMetadata.newBuilder().apply {
    apiVersion = "1.0"
    kind = "aws:LoadBalancer"
  }.build()

  given("no plugins are stored") {
    it("returns null from assetPluginsFor") {
      expect(subject.assetPluginFor(securityGroup)) {
        isNull()
      }
    }

    it("returns an empty iterator from assetPluginsFor") {
      expect(subject.vetoPlugins()) {
        isEmpty()
      }
    }
  }

  given("an asset plugin is registered") {

    val address = PluginAddress("${securityGroup.kind}.vip", 6565)

    beforeGroup {
      subject.addAssetPluginFor(securityGroup, address)
    }

    it("returns the plugin address by type") {
      expect(subject.assetPluginFor(securityGroup)) {
        isNotNull().isEqualTo(address)
      }
    }

    it("does not return the plugin address for a different type") {
      expect(subject.assetPluginFor(loadBalancer)) {
        isNull()
      }
    }
  }

  given("a veto plugin is registered") {

    val address1 = PluginAddress("veto1.vip", 6565)
    val address2 = PluginAddress("veto2.vip", 6565)

    beforeGroup {
      with(subject) {
        addVetoPlugin(address1)
        addVetoPlugin(address2)
      }
    }

    it("returns the plugin") {
      expect(subject.vetoPlugins()) {
        containsExactlyInAnyOrder(address1, address2)
      }
    }
  }
})

fun <T : Iterable<E>, E> Assertion.Builder<T>.isEmpty() =
  assert("is empty") {
    if (it.iterator().hasNext()) fail() else pass()
  }
