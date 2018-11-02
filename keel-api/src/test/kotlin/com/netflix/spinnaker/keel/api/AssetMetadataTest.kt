package com.netflix.spinnaker.keel.api

import com.fasterxml.jackson.dataformat.yaml.YAMLMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.oneeyedmen.minutest.junit.junitTests
import org.junit.jupiter.api.TestFactory
import strikt.api.expectThat
import strikt.assertions.hasEntry
import strikt.assertions.isEqualTo

internal object AssetMetadataTest {

  val mapper = YAMLMapper()
    .registerKotlinModule()

  @TestFactory
  fun serialize() = junitTests<AssetMetadata> {
    fixture {
      AssetMetadata(AssetName("my-new-cron-object"), mapOf(
        "clusterName" to "",
        "creationTimestamp" to "2017-05-31T12:56:35Z",
        "deletionGracePeriodSeconds" to null,
        "deletionTimestamp" to null,
        "namespace" to "default",
        "resourceVersion" to "285",
        "selfLink" to "/apis/stable.example.com/v1/namespaces/default/crontabs/my-new-cron-object",
        "uid" to "9423255b-4600-11e7-af6a-28d2447dc82b"
      ))
    }

    test("serializes as a hash") {
      expectThat(mapper.writeValueAsString(this)).isEqualTo(
        """---
          |name: "my-new-cron-object"
          |clusterName: ""
          |creationTimestamp: "2017-05-31T12:56:35Z"
          |deletionGracePeriodSeconds: null
          |deletionTimestamp: null
          |namespace: "default"
          |resourceVersion: "285"
          |selfLink: "/apis/stable.example.com/v1/namespaces/default/crontabs/my-new-cron-object"
          |uid: "9423255b-4600-11e7-af6a-28d2447dc82b"
          |""".trimMargin())
    }
  }

  @TestFactory
  fun deserialize() = junitTests<String> {
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

    test("deserializes name") {
      expectThat(mapper.readValue<AssetMetadata>(this))
        .get { name }
        .isEqualTo(AssetName("my-new-cron-object"))
    }

    test("deserializes extra data") {
      expectThat(mapper.readValue<AssetMetadata>(this))
        .get { data }
        .hasEntry("namespace", "default")
        .hasEntry("uid", "9423255b-4600-11e7-af6a-28d2447dc82b")
    }
  }
}
