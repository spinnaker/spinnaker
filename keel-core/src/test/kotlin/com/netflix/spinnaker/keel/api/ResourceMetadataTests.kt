package com.netflix.spinnaker.keel.api

import com.fasterxml.jackson.module.kotlin.readValue
import com.netflix.spinnaker.keel.serialization.configuredYamlMapper
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import strikt.api.expectThat
import strikt.assertions.get
import strikt.assertions.hasEntry
import strikt.assertions.isEqualTo

internal class ResourceMetadataTests : JUnit5Minutests {

  val mapper = configuredYamlMapper()

  fun tests() = rootContext<Unit> {
    derivedContext<Map<String, Any?>>("serialization") {
      fixture {
        mapOf(
          "name" to "my-new-cron-object",
          "uid" to "1423255B460011E7AF6A28D244",
          "serviceAccount" to "keel@spinnaker",
          "clusterName" to "",
          "creationTimestamp" to "2017-05-31T12:56:35Z",
          "deletionGracePeriodSeconds" to null,
          "deletionTimestamp" to null,
          "namespace" to "default",
          "selfLink" to "/apis/stable.example.com/v1/namespaces/default/crontabs/my-new-cron-object"
        )
      }

      test("serializes as a hash using Jackson") {
        expectThat(mapper.writeValueAsString(this)).isEqualTo(
          """---
          |name: "my-new-cron-object"
          |uid: "1423255B460011E7AF6A28D244"
          |serviceAccount: "keel@spinnaker"
          |clusterName: ""
          |creationTimestamp: "2017-05-31T12:56:35Z"
          |namespace: "default"
          |selfLink: "/apis/stable.example.com/v1/namespaces/default/crontabs/my-new-cron-object"
          |""".trimMargin())
      }
    }

    derivedContext<String>("deserialization with Jackson") {
      fixture {
        """---
        |clusterName: ""
        |creationTimestamp: 2017-05-31T12:56:35Z
        |name: my-new-cron-object
        |namespace: default
        |selfLink: /apis/stable.example.com/v1/namespaces/default/crontabs/my-new-cron-object
        |uid: 1423255b460011e7af6a28d244
        |serviceAccount: "keel@spinnaker"
      """.trimMargin()
      }

      test("deserializes properties") {
        expectThat(mapper.readValue<Map<String, Any?>>(this)) {
          get("name").isEqualTo("my-new-cron-object")
          get("uid").isEqualTo("1423255b460011e7af6a28d244")
        }
      }

      test("deserializes extra data") {
        expectThat(mapper.readValue<Map<String, Any?>>(this))
          .hasEntry("namespace", "default")
      }
    }
  }
}
