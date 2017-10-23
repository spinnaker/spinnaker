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
@JsonCreator constructor(spec: BaseApplicationSpec) : Intent<BaseApplicationSpec>(
  kind = KIND,
  schema = CURRENT_SCHEMA,
  spec = spec
) {
  override fun getId() = "$KIND:${spec.name}"
}

// Using an abstract class here so that we can override the spec with Netflix-specific values and continue to use the
// same intent & processor.
abstract class BaseApplicationSpec : IntentSpec {
  abstract val name: String
  abstract val description: String?
  abstract val email: String?
  abstract val lastModifiedBy: String?
  abstract val owner: String
  abstract val chaosMonkey: ChaosMonkeySpec
  abstract val enableRestartRunningExecutions: Boolean
  abstract val instanceLinks: List<InstanceLinkSpec>
  abstract val instancePort: Int
  abstract val appGroup: String?
  abstract val cloudProviders: String?
  abstract val accounts: String?
  abstract val user: String?
  abstract val dataSources: Map<String, List<String>>
  abstract val requiredGroupMembership: List<String>
  abstract val group: String?
}

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

data class ApplicationSpec(
  override val name: String,
  override val description: String?,
  override val email: String?,
  override val lastModifiedBy: String?,
  override val owner: String,
  override val chaosMonkey: ChaosMonkeySpec,
  override val enableRestartRunningExecutions: Boolean,
  override val instanceLinks: List<InstanceLinkSpec>,
  override val instancePort: Int,
  override val appGroup: String?,
  override val cloudProviders: String?,
  override val accounts: String?,
  override val user: String?,
  override val dataSources: Map<String, List<String>>,
  override val requiredGroupMembership: List<String>,
  override val group: String?
) : BaseApplicationSpec()

data class NetflixApplicationSpec(
  override val name: String,
  override val description: String?,
  override val email: String?,
  override val lastModifiedBy: String?,
  override val owner: String,
  override val chaosMonkey: ChaosMonkeySpec,
  override val enableRestartRunningExecutions: Boolean,
  override val instanceLinks: List<InstanceLinkSpec> = listOf(),
  override val instancePort: Int,
  override val appGroup: String?,
  override val cloudProviders: String?,
  override val accounts: String?,
  override val user: String?,
  override val dataSources: Map<String, List<String>> = mapOf(),
  override val requiredGroupMembership: List<String> = listOf(),
  override val group: String?,
  val repoSlug: String?,
  val repoProjectKey: String?,
  val repoType: String?,
  val pdApiKey: String
) : BaseApplicationSpec()
