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
package com.netflix.spinnaker.keel.asset.pipeline

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.config.KeelProperties
import com.netflix.spinnaker.config.configureObjectMapper
import com.netflix.spinnaker.hamkrest.shouldEqual
import com.netflix.spinnaker.keel.dryrun.ChangeSummary
import com.netflix.spinnaker.keel.front50.model.PipelineConfig
import com.netflix.spinnaker.kork.jackson.ObjectMapperSubtypeConfigurer.ClassSubtypeLocator
import org.junit.jupiter.api.Test

object PipelineConverterTest {

  val mapper = configureObjectMapper(
    ObjectMapper(),
    KeelProperties(),
    listOf(ClassSubtypeLocator(Trigger::class.java, listOf("com.netflix.spinnaker.keel.asset")))
  )

  val subject = PipelineConverter(mapper)

  @Test
  fun `should convert spec to system state`() {
    subject.convertToState(spec).also {
      it.application shouldEqual spec.application
      it.name shouldEqual spec.name
      it.limitConcurrent shouldEqual spec.flags.limitConcurrent
      it.keepWaitingPipelines shouldEqual spec.flags.keepWaitingPipelines
      it.executionEngine shouldEqual spec.properties.executionEngine
      it.spelEvaluator shouldEqual spec.properties.spelEvaluator
      it.stages shouldEqual listOf(
        mapOf(
          "showAdvancedOptions" to false,
          "baseLabel" to "release",
          "user" to "example@example.com",
          "type" to "bake",
          "storeType" to "ebs",
          "refId" to "bake1",
          "requisiteStageRefIds" to listOf<String>(),
          "enhancedNetworking" to false,
          "regions" to listOf("us-east-1", "us-west-2"),
          "extendedAttributes" to mapOf<Any, Any>(),
          "cloudProviderType" to "ec2",
          "vmType" to "hvm",
          "package" to "keel",
          "baseOs" to "xenial"
        )
      )
      it.triggers?.size shouldEqual 1
      it.triggers?.get(0)?.also { trigger ->
        trigger["type"] shouldEqual "jenkins"
        trigger["enabled"] shouldEqual true
        trigger["master"] shouldEqual "spinnaker"
        trigger["job"] shouldEqual "SPINNAKER-package-keel"
        trigger["propertyFile"] to ""
      }
      it.parameterConfig shouldEqual listOf()
      it.notifications shouldEqual listOf()
    }
  }

  @Test
  fun `should convert system state to spec`() {
    val result = subject.convertFromState(state)
    result shouldEqual spec
  }

  @Test
  fun `should convert spec to orchestration job`() {
    subject.convertToJob(ConvertPipelineToJob(spec, null), ChangeSummary("test")).also {
      it.size shouldEqual 1
      it[0]["type"] shouldEqual "savePipeline"
    }
  }

  val spec = PipelineSpec(
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
        this["cloudProviderType"] = "ec2"
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

  val state = PipelineConfig(
    application = "keel",
    name = spec.name,
    parameterConfig = listOf(),
    triggers = listOf(
      mapOf(
        "type" to "jenkins",
        "enabled" to true,
        "master" to "spinnaker",
        "job" to "SPINNAKER-package-keel",
        "propertyFile" to ""
      )
    ),
    notifications = listOf(),
    stages = listOf(
      mapOf(
        "showAdvancedOptions" to false,
        "baseLabel" to "release",
        "user" to "example@example.com",
        "type" to "bake",
        "storeType" to "ebs",
        "refId" to "bake1",
        "requisiteStageRefIds" to listOf<String>(),
        "enhancedNetworking" to false,
        "regions" to listOf("us-east-1", "us-west-2"),
        "extendedAttributes" to mapOf<Any, Any>(),
        "cloudProviderType" to "ec2",
        "vmType" to "hvm",
        "package" to "keel",
        "baseOs" to "xenial"
      )
    ),
    spelEvaluator = null,
    executionEngine = "v3",
    limitConcurrent = true,
    keepWaitingPipelines = true,
    id = null,
    index = null,
    stageCounter = null,
    lastModifiedBy = null,
    updateTs = null
  )
}
