/*
 * Copyright 2017 Netflix, Inc.
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
package com.netflix.spinnaker.keel.intents

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonTypeName
import com.github.jonpeterson.jackson.module.versioning.JsonVersionedModel
import com.netflix.spinnaker.keel.Intent
import com.netflix.spinnaker.keel.IntentSpec

private const val KIND = "Application"
private const val CURRENT_SCHEMA = "1"

@JsonTypeName(KIND)
@JsonVersionedModel(currentVersion = CURRENT_SCHEMA, propertyName = SCHEMA_PROPERTY)
class ApplicationIntent
@JsonCreator constructor(spec: ApplicationSpec) : Intent<ApplicationSpec>(
  kind = KIND,
  schema = CURRENT_SCHEMA,
  spec = spec
) {
  override fun getId() = "$KIND:${spec.name}"
}

// TODO rz - Not sure what keys are Netflix-only and what aren't. Also not exactly sure what is required and isn't.
data class ApplicationSpec(
  val name: String,
  val description: String?,
  val email: String?,
  val lastModifiedBy: String,
  val repoSlug: String?,
  val repoProjectKey: String?,
  val repoType: String?,
  val owner: String,
  val pdApiKey: String,
  val chaosMonkey: ChaosMonkeySpec,
  val enableRestartRunningExecutions: Boolean,
  val instanceLinks: List<InstanceLinkSpec> = listOf(),
  val instancePort: Int,
  val appGroup: String?,
  val cloudProviders: String?,
  val accounts: String?,
  val user: String?,
  val dataSources: Map<String, List<String>> = mapOf(),
  val requiredGroupMembership: List<String> = listOf(),
  val group: String?
) : IntentSpec

data class ChaosMonkeySpec(
  val enabled: Boolean,
  val meanTimeBetweenKillsInWorkDays: Int,
  val minTimeBetweenKillsInWorkDays: Int,
  val grouping: String,
  val regionsAreIndependent: Boolean,
  val exceptions: List<ChaosMonkeyExceptionSpec>
)

data class ChaosMonkeyExceptionSpec(
  val region: String,
  val account: String,
  val detail: String,
  val stack: String
)

data class InstanceLinkSpec(
  val title: String,
  val links: List<LinkSpec>
)

data class LinkSpec(
  val title: String,
  val path: String
)

// TODO rz - All the Netflix extension attributes. Need to come up with a good strategy for
// switching out the spec model in the cases of Netflix extensions to the data models.
//data class NetflixApplicationSpec() : ApplicationSpec()
