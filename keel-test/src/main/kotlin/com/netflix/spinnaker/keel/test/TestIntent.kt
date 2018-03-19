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
package com.netflix.spinnaker.keel.test

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.annotation.JsonTypeName
import com.github.jonpeterson.jackson.module.versioning.JsonVersionedModel
import com.netflix.spinnaker.keel.ApplicationAwareIntentSpec
import com.netflix.spinnaker.keel.Intent
import com.netflix.spinnaker.keel.IntentSpec
import com.netflix.spinnaker.keel.IntentStatus
import com.netflix.spinnaker.keel.attribute.Attribute
import com.netflix.spinnaker.keel.policy.Policy
import com.netflix.spinnaker.keel.policy.PolicySpec

@JsonTypeName("Test")
@JsonVersionedModel(currentVersion = "0", propertyName = "schema")
class TestIntent
@JsonCreator constructor(
  spec: TestIntentSpec,
  labels: MutableMap<String, String> = mutableMapOf(),
  attributes: List<Attribute<Any>> = listOf(),
  policies: List<Policy<PolicySpec>> = listOf()
) : Intent<TestIntentSpec>("1", "Test", spec, IntentStatus.ACTIVE, labels, attributes, policies) {
  @JsonIgnore override val defaultId = "test:${spec.id}"
}

// Using minimal class for ease in testing
@JsonTypeInfo(use = JsonTypeInfo.Id.MINIMAL_CLASS)
abstract class TestIntentSpec: IntentSpec {
  abstract val id: String
}

data class GenericTestIntentSpec(
  override val id: String,
  val data: Map<String, Any> = mapOf()
) : TestIntentSpec()

data class ApplicationAwareTestIntentSpec(
  override val id: String,
  override val application: String
) : TestIntentSpec(), ApplicationAwareIntentSpec
