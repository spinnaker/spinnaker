package com.netflix.spinnaker.keel.api

import com.fasterxml.jackson.dataformat.yaml.YAMLMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.google.gson.GsonBuilder
import com.oneeyedmen.minutest.junit.junitTests
import org.junit.jupiter.api.TestFactory
import strikt.api.expectThat
import strikt.assertions.containsKey
import strikt.assertions.hasEntry
import strikt.assertions.isEqualTo
import java.util.*

internal object AssetMetadataTest {

  val mapper = YAMLMapper()
    .registerKotlinModule()

  val gson = GsonBuilder().setPrettyPrinting().serializeNulls().create()

  @TestFactory
  fun serialize() = junitTests<AssetMetadata> {
    fixture {
      AssetMetadata(
        AssetName("my-new-cron-object"),
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

    test("serializes as a hash using Gson") {
      expectThat(gson.toJson(this)).isEqualTo(
        """{
          |  "name": "my-new-cron-object",
          |  "uid": "9423255b-4600-11e7-af6a-28d2447dc82b",
          |  "resourceVersion": 285,
          |  "clusterName": "",
          |  "creationTimestamp": "2017-05-31T12:56:35Z",
          |  "deletionGracePeriodSeconds": null,
          |  "deletionTimestamp": null,
          |  "namespace": "default",
          |  "selfLink": "/apis/stable.example.com/v1/namespaces/default/crontabs/my-new-cron-object"
          |}""".trimMargin())
    }
  }

  @TestFactory
  fun deserializeWithJackson() = junitTests<String> {
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
      expectThat(mapper.readValue<AssetMetadata>(this)) {
        get { name }.isEqualTo(AssetName("my-new-cron-object"))
        get { uid }.isEqualTo(UUID.fromString("9423255b-4600-11e7-af6a-28d2447dc82b"))
        get { resourceVersion }.isEqualTo(285L)
      }
    }

    test("deserializes extra data") {
      expectThat(mapper.readValue<AssetMetadata>(this))
        .get { data }
        .hasEntry("namespace", "default")
    }
  }

  @TestFactory
  fun deserializeWithGson() = junitTests<String> {
    fixture {
      """{
        |  "annotations" : {
        |    "kubectl.kubernetes.io/last-applied-configuration" : "{\"apiVersion\":\"file.spinnaker.netflix.com/v1\",\"kind\":\"message\",\"metadata\":{\"annotations\":{},\"name\":\"my-message\"},\"spec\":{\"text\":\"OMFG\"}}\n"
        |  },
        |  "clusterName" : "",
        |  "creationTimestamp" : "2018-12-05T22:49:56Z",
        |  "generation" : 1.0,
        |  "name" : "my-message",
        |  "namespace" : "",
        |  "resourceVersion" : "280688",
        |  "selfLink" : "/apis/file.spinnaker.netflix.com/v1/messages/my-message",
        |  "uid" : "16742412-f8e0-11e8-8396-025000000001"
        |}""".trimMargin()
    }
    test("deserializes properties using Gson") {
      expectThat(gson.fromJson(this, AssetMetadata::class.java)) {
        get { name }.isEqualTo(AssetName("my-message"))
        get { uid }.isEqualTo(UUID.fromString("16742412-f8e0-11e8-8396-025000000001"))
        get { resourceVersion }.isEqualTo(280688L)
      }
    }

    test("deserializes extra data using Gson") {
      expectThat(gson.fromJson(this, AssetMetadata::class.java))
        .get { data }
        .hasEntry("namespace", "")
        .containsKey("annotations")
    }
  }
}
