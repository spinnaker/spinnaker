/*
 * Copyright 2018 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.spinnaker.keel.intent

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.convertValue
import com.fasterxml.jackson.module.kotlin.readValue
import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.should.shouldMatch
import com.netflix.spinnaker.config.KeelConfiguration
import com.netflix.spinnaker.config.KeelProperties
import com.netflix.spinnaker.config.KeelSubTypeLocator
import com.netflix.spinnaker.hamkrest.shouldEqual
import org.junit.jupiter.api.Test

object PipelineIntentTest {

  val mapper = KeelConfiguration()
    .apply { properties = KeelProperties() }
    .objectMapper(
      ObjectMapper(), listOf(KeelSubTypeLocator(Trigger::class.java, listOf("com.netflix.spinnaker.keel.intent")))
    )

  @Test
  fun `can serialize to expected JSON format`() {
    val serialized = mapper.convertValue<Map<String, Any>>(pipeline)
    val deserialized = mapper.readValue<Map<String, Any>>(json)

    serialized shouldMatch equalTo(deserialized)
  }

  @Test
  fun `can deserialize from expected JSON format`() {
    mapper.readValue<PipelineIntent>(json).apply {
      spec shouldEqual pipeline.spec
    }
  }

  val pipeline = PipelineIntent(
    PipelineSpec(
      application = "keel",
      name = "Bake",
      stages = listOf(
        PipelineStage().apply {
          this["showAdvancedOptions"] = false
          this["baseLabel"] = "release"
          this["user"] = "example@example.com"
          this["kind"] = "bake"
          this["storeType"] = "ebs"
          this["refId"] = "bake1"
          this["dependsOn"] = listOf<String>()
          this["enhancedNetworking"] = false
          this["regions"] = listOf("us-east-1", "us-west-2")
          this["extendedAttributes"] = mapOf<Any, Any>()
          this["cloudProviderType"] = "aws"
          this["vmType"] = "hvm"
          this["package"] = "keel"
          this["baseOs"] = "xenial"
        }
      ),
      triggers = listOf(
        JenkinsTrigger(mapOf(
          "enabled" to true,
          "master" to "spinnaker",
          "job" to "SPINNAKER-package-keel",
          "propertyFile" to ""
        ))
      ),
      parameters = listOf(),
      notifications = listOf(),
      flags = PipelineFlags().apply {
        this["limitConcurrent"] = true
        this["keepWaitingPipelines"] = true
      },
      properties = PipelineProperties().apply {
        this["executionEngine"] = "v3"
      }
    )
  )

  val json = """
{
  "kind": "Pipeline",
  "spec": {
    "application": "keel",
    "name": "Bake",
    "flags": {
      "limitConcurrent": true,
      "keepWaitingPipelines": true
    },
    "properties": {
      "executionEngine": "v3"
    },
    "parameters": [],
    "notifications": [],
    "triggers": [
      {
        "kind": "jenkins",
        "enabled": true,
        "master": "spinnaker",
        "job": "SPINNAKER-package-keel",
        "propertyFile": ""
      }
    ],
    "stages": [
      {
        "kind": "bake",
        "refId": "bake1",
        "dependsOn": [],
        "showAdvancedOptions": false,
        "baseLabel": "release",
        "user": "example@example.com",
        "storeType": "ebs",
        "enhancedNetworking": false,
        "regions": [
          "us-east-1",
          "us-west-2"
        ],
        "extendedAttributes": {},
        "cloudProviderType": "aws",
        "vmType": "hvm",
        "package": "keel",
        "baseOs": "xenial"
      }
    ]
  },
  "status": "ACTIVE",
  "labels": {},
  "attributes": [],
  "policies": [],
  "id": "Pipeline:keel:Bake",
  "schema": "0"
}
"""
}
