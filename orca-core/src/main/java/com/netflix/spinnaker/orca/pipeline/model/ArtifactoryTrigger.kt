/*
 * Copyright 2019 Pivotal, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.orca.pipeline.model

import com.netflix.spinnaker.kork.artifacts.model.Artifact
import com.netflix.spinnaker.kork.artifacts.model.ExpectedArtifact

data class ArtifactoryTrigger
@JvmOverloads constructor(
  override val type: String = "artifactory",
  override val correlationId: String? = null,
  override val user: String? = "[anonymous]",
  override val parameters: Map<String, Any> = mutableMapOf(),
  override val artifacts: List<Artifact> = mutableListOf(),
  override val notifications: List<Map<String, Any>> = mutableListOf(),
  override var isRebake: Boolean = false,
  override var isDryRun: Boolean = false,
  override var isStrategy: Boolean = false,
  val artifactorySearchName: String
) : Trigger {
  override var other: Map<String, Any> = mutableMapOf()
  override var resolvedExpectedArtifacts: List<ExpectedArtifact> = mutableListOf()
}
