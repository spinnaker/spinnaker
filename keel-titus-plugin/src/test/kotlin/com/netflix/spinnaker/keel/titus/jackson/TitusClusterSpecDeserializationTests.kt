package com.netflix.spinnaker.keel.titus.jackson

import com.fasterxml.jackson.databind.jsontype.NamedType
import com.fasterxml.jackson.module.kotlin.readValue
import com.netflix.spinnaker.keel.api.titus.TitusClusterSpec
import com.netflix.spinnaker.keel.api.toSimpleLocations
import com.netflix.spinnaker.keel.core.api.SubmittedDeliveryConfig
import com.netflix.spinnaker.keel.test.configuredTestYamlMapper
import com.netflix.spinnaker.titus.jackson.registerKeelTitusApiModule
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import strikt.api.expectThat
import strikt.assertions.isA
import strikt.assertions.isEqualTo

class TitusClusterSpecDeserializationTests : JUnit5Minutests {

  data class Fixture(val manifest: String) {
    val mapper = configuredTestYamlMapper()
      .registerKeelTitusApiModule()
      .apply {
        registerSubtypes(NamedType(TitusClusterSpec::class.java, "titus/cluster@v1"))
      }
  }

  fun tests() = rootContext<Fixture> {
    context("a titus cluster where the locations are derived from the environment") {
      fixture {
        Fixture("""
               |---
               |application: fnord
               |serviceAccount: fzlem@spinnaker.io
               |environments:
               |  - name: test
               |    locations:
               |      account: test
               |      regions:
               |      - name: us-west-2
               |      - name: us-east-1
               |    resources:
               |    - kind: titus/cluster@v1
               |      spec:
               |        moniker:
               |          app: fnord
               |        container:
               |          organization: fnord
               |          image: fnord
               |          digest: sha:9e860d779528ea32b1692cdbb840c66c5d173b2c63aee0e7a75a957e06790de7
               |      """.trimMargin())
      }

      test("locations on the cluster are set based on the environment") {
        val deliveryConfig = mapper.readValue<SubmittedDeliveryConfig>(manifest)

        expectThat(deliveryConfig.environments.first().resources.first().spec)
          .isA<TitusClusterSpec>()
          .get(TitusClusterSpec::locations)
          .isEqualTo(deliveryConfig.environments.first().locations!!.toSimpleLocations())
      }
    }
  }
}
