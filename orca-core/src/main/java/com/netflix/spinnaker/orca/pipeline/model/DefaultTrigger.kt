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

import com.netflix.spinnaker.kork.artifacts.model.Artifact
import com.netflix.spinnaker.kork.artifacts.model.ExpectedArtifact
import com.netflix.spinnaker.orca.api.pipeline.models.Trigger

data class DefaultTrigger
@JvmOverloads constructor(
  private val type: String,
  private val correlationId: String? = null,
  private val user: String? = "[anonymous]",
  private val parameters: MutableMap<String, Any> = mutableMapOf(),
  private val artifacts: MutableList<Artifact> = mutableListOf(),
  private val notifications: MutableList<MutableMap<String, Any>> = mutableListOf(),
  private var isRebake: Boolean = false,
  private var isDryRun: Boolean = false,
  private var isStrategy: Boolean = false
) : Trigger {
  private var other: MutableMap<String, Any> = mutableMapOf()
  private var resolvedExpectedArtifacts: MutableList<ExpectedArtifact> = mutableListOf()

  override fun getType(): String = type

  override fun getCorrelationId(): String? = correlationId

  override fun getUser(): String? = user

  override fun getParameters(): MutableMap<String, Any> = parameters

  override fun getArtifacts(): MutableList<Artifact> = artifacts

  override fun isRebake(): Boolean = isRebake

  override fun setRebake(rebake: Boolean) {
    isRebake = rebake
  }

  override fun isDryRun(): Boolean = isDryRun

  override fun setDryRun(dryRun: Boolean) {
    isDryRun = dryRun
  }

  override fun isStrategy(): Boolean = isStrategy

  override fun setStrategy(strategy: Boolean) {
    isStrategy = strategy
  }

  override fun setResolvedExpectedArtifacts(resolvedExpectedArtifacts: MutableList<ExpectedArtifact>) {
    this.resolvedExpectedArtifacts = resolvedExpectedArtifacts
  }

  override fun getNotifications(): MutableList<MutableMap<String, Any>> = notifications

  override fun getOther(): MutableMap<String, Any> = other

  override fun getResolvedExpectedArtifacts(): MutableList<ExpectedArtifact> = resolvedExpectedArtifacts

  override fun setOther(key: String, value: Any) {
    this.other[key] = value
  }

  override fun setOther(other: MutableMap<String, Any>) {
    this.other = other
  }
}
