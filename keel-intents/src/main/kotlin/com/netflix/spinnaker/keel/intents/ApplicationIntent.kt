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
import com.fasterxml.jackson.annotation.JsonTypeInfo
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
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "kind")
abstract class BaseApplicationSpec : IntentSpec {
  abstract val name: String
  abstract val description: String?
  abstract val email: String
  abstract val lastModifiedBy: String?
  abstract val owner: String
  abstract val chaosMonkey: ChaosMonkeySpec?
  abstract val enableRestartRunningExecutions: Boolean
  abstract val instanceLinks: List<InstanceLinkSpec>
  abstract val instancePort: Int?
  abstract val appGroup: String?
  abstract val cloudProviders: String?
  abstract val accounts: String?
  abstract val user: String?
  abstract val dataSources: DataSourcesSpec
  abstract val requiredGroupMembership: List<String>
  abstract val group: String?
  abstract val providerSettings: Map<String, Map<String, Any>>
  abstract val trafficGuards: List<TrafficGuardSpec>
  abstract val platformHealthOnlyShowOverride: Boolean
  abstract val platformHealthOnly: Boolean
  abstract val notifications: NotificationSpec?
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

data class DataSourcesSpec(
  val enabled: List<String>,
  val disabled: List<String>
)

data class TrafficGuardSpec(
  val account: String?,
  val location: String?,
  val stack: String?,
  val detail: String?
)

data class NotificationSpec(
  val slack: List<SlackNotificationSpec>?,
  val email: List<EmailNotificationSpec>?,
  val sms: List<SmsNotificationSpec>?
)

data class SlackNotificationSpec(
  val type: String = "slack",
  val address: String,
  val level: String,
  val `when`: List<String> = listOf()
)

data class EmailNotificationSpec(
  val type: String = "email",
  val address: String,
  val cc: String,
  val level: String,
  val `when`: List<String> = listOf()
)

data class SmsNotificationSpec(
  val type: String = "sms",
  val sms: String,
  val level: String,
  val `when`: List<String> = listOf()
)

@JsonTypeName("Application")
data class ApplicationSpec(
  override val name: String,
  override val description: String?,
  override val email: String,
  override val lastModifiedBy: String?,
  override val owner: String,
  override val chaosMonkey: ChaosMonkeySpec?,
  override val enableRestartRunningExecutions: Boolean,
  override val instanceLinks: List<InstanceLinkSpec> = listOf(),
  override val instancePort: Int?,
  override val appGroup: String?,
  override val cloudProviders: String?,
  override val accounts: String?,
  override val user: String?,
  override val dataSources: DataSourcesSpec = DataSourcesSpec(listOf(), listOf()),
  override val requiredGroupMembership: List<String> = listOf(),
  override val group: String?,
  override val providerSettings: Map<String, Map<String, Any>>,
  override val trafficGuards: List<TrafficGuardSpec>,
  override val platformHealthOnlyShowOverride: Boolean,
  override val platformHealthOnly: Boolean,
  override val notifications: NotificationSpec?
) : BaseApplicationSpec()

// TODO rz - Move to -nflx, figure out a better wiring strategy?
@JsonTypeName("NetflixApplication")
data class NetflixApplicationSpec(
  override val name: String,
  override val description: String?,
  override val email: String,
  override val lastModifiedBy: String?,
  override val owner: String,
  override val chaosMonkey: ChaosMonkeySpec?,
  override val enableRestartRunningExecutions: Boolean,
  override val instanceLinks: List<InstanceLinkSpec> = listOf(),
  override val instancePort: Int?,
  override val appGroup: String?,
  override val cloudProviders: String?,
  override val accounts: String?,
  override val user: String?,
  override val dataSources: DataSourcesSpec = DataSourcesSpec(listOf(), listOf()),
  override val requiredGroupMembership: List<String> = listOf(),
  override val group: String?,
  override val providerSettings: Map<String, Map<String, Any>>,
  override val trafficGuards: List<TrafficGuardSpec>,
  override val platformHealthOnlyShowOverride: Boolean,
  override val platformHealthOnly: Boolean,
  override val notifications: NotificationSpec?,
  val repoSlug: String?,
  val repoProjectKey: String?,
  val repoType: String?,
  val pdApiKey: String,
  val propertyRolloutConfigId: String?,
  val legacyUdf: Boolean,
  val monitorBucketType: String?,
  val criticalityRules: List<CriticalityRuleSpec>,
  val ccpService: String?,
  val timelines: List<TimelineSpec>?
) : BaseApplicationSpec()

data class CriticalityRuleSpec(
  val account: String,
  val location: String,
  val stack: String,
  val detail: String,
  val priority: Int
)

data class TimelineSpec(
  val name: String,
  val description: String,
  val url: String,
  val rowLabel: String,
  val startEvent: String,
  val endEvent: String,
  val rowLabelRegex: String,
  val rowLabelReplacement: String
)
