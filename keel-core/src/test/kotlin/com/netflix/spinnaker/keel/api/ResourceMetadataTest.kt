package com.netflix.spinnaker.keel.api

import com.fasterxml.jackson.module.kotlin.readValue
import com.netflix.spinnaker.keel.serialization.configuredYamlMapper
import de.huxhorn.sulky.ulid.ULID
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import strikt.api.expectThat
import strikt.assertions.hasEntry
import strikt.assertions.isEqualTo

internal object ResourceMetadataTest : JUnit5Minutests {

  val mapper = configuredYamlMapper()

  fun tests() = rootContext<Unit> {
    derivedContext<ResourceMetadata>("serialization") {
      fixture {
        ResourceMetadata(
          ResourceName("my-new-cron-object"),
          285,
          ULID.parseULID("1423255B460011E7AF6A28D244"),
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
          |uid: "1423255B460011E7AF6A28D244"
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
        |uid: 1423255b460011e7af6a28d244
      """.trimMargin()
      }

      test("deserializes properties") {
        expectThat(mapper.readValue<ResourceMetadata>(this)) {
          get { name }.isEqualTo(ResourceName("my-new-cron-object"))
          get { uid }.isEqualTo(ULID.parseULID("1423255B460011E7AF6A28D244"))
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
