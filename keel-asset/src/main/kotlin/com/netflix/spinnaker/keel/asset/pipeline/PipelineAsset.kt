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

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.annotation.JsonTypeName
import com.github.jonpeterson.jackson.module.versioning.JsonVersionedModel
import com.netflix.spinnaker.keel.ApplicationAwareAssetSpec
import com.netflix.spinnaker.keel.Asset
import com.netflix.spinnaker.keel.annotation.ForcesNew
import com.netflix.spinnaker.keel.asset.SCHEMA_PROPERTY

private const val KIND = "Pipeline"
private const val CURRENT_SCHEMA = "0"

@JsonTypeName(KIND)
@JsonVersionedModel(currentVersion = CURRENT_SCHEMA, propertyName = SCHEMA_PROPERTY)
class PipelineAsset
@JsonCreator constructor(spec: PipelineSpec) : Asset<PipelineSpec>(
  kind = KIND,
  schema = CURRENT_SCHEMA,
  spec = spec
) {
  @JsonIgnore override val id = "${KIND}:${spec.application}:${spec.name}"
}

@JsonTypeName("pipeline")
data class PipelineSpec(
  override val application: String,
  // TODO rz - Support renaming without re-creation. Probably require getting all pipelines for an
  // application and finding a match on name before writes happen?
  @ForcesNew val name: String,
  val stages: List<PipelineStage>,
  val triggers: List<Trigger>,
  val parameters: List<Map<String, Any?>>,
  val notifications: List<Map<String, Any?>>,
  val flags: PipelineFlags,
  val properties: PipelineProperties
) : ApplicationAwareAssetSpec

class PipelineFlags : HashMap<String, Boolean>() {

  val keepWaitingPipelines: Boolean?
    get() = get("keepWaitingPipelines")

  val limitConcurrent: Boolean?
    get() = get("limitConcurrent")
}

class PipelineProperties : HashMap<String, String>() {

  val executionEngine: String?
    get() = get("executionEngine")

  val spelEvaluator: String?
    get() = get("spelEvaluator")
}

class PipelineStage : HashMap<String, Any>() {

  val kind: String
    get() = get("kind").toString()

  val refId: String
    get() = get("refId").toString()

  @Suppress("UNCHECKED_CAST")
  val dependsOn: List<String>
    get() = if (containsKey("dependsOn")) this["dependsOn"] as List<String> else listOf()
}

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "kind")
interface Trigger
//abstract class Trigger : HashMap<String, Any> {
//  @JsonCreator constructor(m: Map<String, Any>) : super(m)
//}

@JsonTypeName("cron")
class CronTrigger(m: Map<String, Any>) : HashMap<String, Any>(m), Trigger

@JsonTypeName("docker")
class DockerTrigger(m: Map<String, Any>) : HashMap<String, Any>(m), Trigger

@JsonTypeName("dryRun")
class DryRunTrigger(m: Map<String, Any>) : HashMap<String, Any>(m), Trigger

@JsonTypeName("git")
class GitTrigger(m: Map<String, Any>) : HashMap<String, Any>(m), Trigger

@JsonTypeName("jenkins")
class JenkinsTrigger(m: Map<String, Any>) : HashMap<String, Any>(m), Trigger

@JsonTypeName("manual")
class ManualTrigger(m: Map<String, Any>) : HashMap<String, Any>(m), Trigger

@JsonTypeName("pipeline")
class PipelineTrigger(m: Map<String, Any>) : HashMap<String, Any>(m), Trigger
