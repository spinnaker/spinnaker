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
  var buildInfo: JenkinsBuildInfo? = null
  var properties: Map<String, Any> = mutableMapOf()
}

class JenkinsArtifact
@JsonCreator
constructor(@param:JsonProperty("fileName") val fileName: String,
            @param:JsonProperty("relativePath") val relativePath: String)

class JenkinsBuildInfo
@JsonCreator
constructor(@param:JsonProperty("name") override val name: String?,
            @param:JsonProperty("number") override val number: Int,
            @param:JsonProperty("url") override val url: String?,
            @param:JsonProperty("result") override val result: String?,
            @param:JsonProperty("artifacts") override val artifacts: List<JenkinsArtifact>?,
            @param:JsonProperty("scm") override val scm: List<SourceControl>?,
            @param:JsonProperty("building") override var building: Boolean = false,
            @param:JsonProperty("timestamp") val timestamp: Long?)
    : BuildInfo<JenkinsArtifact>(name, number, url, result, artifacts, scm, building) {

    @JvmOverloads
    constructor(name: String,
                number: Int,
                url: String,
                result: String,
                artifacts: List<JenkinsArtifact> = emptyList(),
                scm: List<SourceControl> = emptyList()): this(name, number, url, result, artifacts, scm, false, null)
}
