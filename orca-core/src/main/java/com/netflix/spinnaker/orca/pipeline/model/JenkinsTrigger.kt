/*
 * Copyright 2018 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.orca.pipeline.model

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonProperty
import com.netflix.spinnaker.kork.artifacts.model.Artifact
import com.netflix.spinnaker.kork.artifacts.model.ExpectedArtifact

data class JenkinsTrigger
@JvmOverloads constructor(
  override val type: String = "jenkins",
  override val correlationId: String? = null,
  override val user: String? = "[anonymous]",
  override val parameters: Map<String, Any> = mutableMapOf(),
  override val artifacts: List<Artifact> = mutableListOf(),
  override val notifications: List<Map<String, Any>> = mutableListOf(),
  override var isRebake: Boolean = false,
  override var isDryRun: Boolean = false,
  override var isStrategy: Boolean = false,
  val master: String,
  val job: String,
  val buildNumber: Int,
  val propertyFile: String?
) : Trigger {

  override var other: Map<String, Any> = mutableMapOf()
  override var resolvedExpectedArtifacts: List<ExpectedArtifact> = mutableListOf()
  var buildInfo: BuildInfo? = null
  var properties: Map<String, Any> = mutableMapOf()

  data class BuildInfo
  @JsonCreator constructor(
    @param:JsonProperty("name") val name: String,
    @param:JsonProperty("number") val number: Int,
    @param:JsonProperty("url") val url: String,
    @JsonProperty("artifacts") val artifacts: List<JenkinsArtifact>? = emptyList(),
    @JsonProperty("scm") val scm: List<SourceControl>? = emptyList(),
    @param:JsonProperty("building") val isBuilding: Boolean,
    @param:JsonProperty("result") val result: String?
  ) {
    @get:JsonIgnore
    val fullDisplayName: String
      get() = name + " #" + number
  }

  data class SourceControl
  @JsonCreator constructor(
    @param:JsonProperty("name") val name: String,
    @param:JsonProperty("branch") val branch: String,
    @param:JsonProperty("sha1") val sha1: String
  )

  data class JenkinsArtifact
  @JsonCreator constructor(
    @param:JsonProperty("fileName") val fileName: String,
    @param:JsonProperty("relativePath") val relativePath: String
  )
}
