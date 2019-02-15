package com.netflix.spinnaker.keel.api

import com.fasterxml.jackson.dataformat.yaml.YAMLMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import strikt.api.expectThat
import strikt.assertions.hasEntry
import strikt.assertions.isEqualTo
import java.util.*

internal object ResourceMetadataTest : JUnit5Minutests {

  val mapper = YAMLMapper()
    .registerKotlinModule()

  fun tests() = rootContext<Unit> {
    derivedContext<ResourceMetadata>("serialization") {
      fixture {
        ResourceMetadata(
          ResourceName("my-new-cron-object"),
          285,
          UUID.fromString("9423255b-4600-11e7-af6a-28d2447dc82b"),
          mapOf(
            "clusterName" to "",
            "creationTimestamp" to "2017-05-31T12:56:35Z",
            "deletionGracePeriodSeconds" to null,
            "deletionTimestamp" to null,
            "namespace" to "default",
            "selfLink" to "/apis/stable.example.com/v1/namespaces/default/crontabs/my-new-cron-object"
          )
        )
      }

      test("serializes as a hash using Jackson") {
        expectThat(mapper.writeValueAsString(this)).isEqualTo(
          """---
          |name: "my-new-cron-object"
          |resourceVersion: 285
          |uid: "9423255b-4600-11e7-af6a-28d2447dc82b"
          |clusterName: ""
          |creationTimestamp: "2017-05-31T12:56:35Z"
          |deletionGracePeriodSeconds: null
          |deletionTimestamp: null
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
        |deletionGracePeriodSeconds: null
        |deletionTimestamp: null
        |name: my-new-cron-object
        |namespace: default
        |resourceVersion: "285"
        |selfLink: /apis/stable.example.com/v1/namespaces/default/crontabs/my-new-cron-object
        |uid: 9423255b-4600-11e7-af6a-28d2447dc82b
      """.trimMargin()
      }

      test("deserializes properties") {
        expectThat(mapper.readValue<ResourceMetadata>(this)) {
          get { name }.isEqualTo(ResourceName("my-new-cron-object"))
          get { uid }.isEqualTo(UUID.fromString("9423255b-4600-11e7-af6a-28d2447dc82b"))
          get { resourceVersion }.isEqualTo(285L)
        }
      }

      test("deserializes extra data") {
        expectThat(mapper.readValue<ResourceMetadata>(this))
          .get { data }
          .hasEntry("namespace", "default")
      }
    }
  }
}
