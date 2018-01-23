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

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.annotation.JsonTypeName
import com.github.jonpeterson.jackson.module.versioning.JsonVersionedModel
import com.netflix.spinnaker.keel.ApplicationAwareIntentSpec
import com.netflix.spinnaker.keel.Intent
import com.netflix.spinnaker.keel.annotation.ForcesNew

private const val KIND = "Pipeline"
private const val CURRENT_SCHEMA = "0"

@JsonTypeName(KIND)
@JsonVersionedModel(currentVersion = CURRENT_SCHEMA, propertyName = SCHEMA_PROPERTY)
class PipelineIntent
@JsonCreator constructor(spec: PipelineSpec) : Intent<PipelineSpec>(
  kind = KIND,
  schema = CURRENT_SCHEMA,
  spec = spec
) {
  @JsonIgnore override val defaultId = "$KIND:${spec.application}:${spec.slug}"
}

@JsonTypeName("pipeline")
data class PipelineSpec(
  override val application: String,
  // Used for idempotency of id
  @ForcesNew val slug: String,
  val stages: List<Map<String, Any?>>,
  val triggers: List<Trigger>,
  val flags: Flags,
  val properties: Properties
) : ApplicationAwareIntentSpec()

class Flags : HashMap<String, Boolean>() {

  val keepWaitingPipelines: Boolean
    get() = get("keepWaitingPipelines") ?: true

  val limitConcurrent: Boolean
    get() = get("limitConcurrent") ?: false
}

class Properties : HashMap<String, String>() {

  val executionEngine: String?
    get() = get("executionEngine")

  val spelEvaluator: String?
    get() = get("spelEvaluator")
}

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "kind")
abstract class Trigger : HashMap<String, Any?>()

@JsonTypeName("cron")
class CronTrigger : Trigger()

@JsonTypeName("docker")
class DockerTrigger : Trigger()

@JsonTypeName("dryRun")
class DryRunTrigger : Trigger()

@JsonTypeName("git")
class GitTrigger : Trigger()

@JsonTypeName("jenkins")
class JenkinsTrigger : Trigger()

@JsonTypeName("manual")
class ManualTrigger : Trigger()

@JsonTypeName("pipeline")
class PipelineTrigger : Trigger()
