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
package com.netflix.spinnaker.keel.front50.model

import com.fasterxml.jackson.annotation.JsonAnyGetter
import com.fasterxml.jackson.annotation.JsonAnySetter
import com.netflix.spinnaker.keel.annotation.Computed
import com.netflix.spinnaker.keel.state.ComputedPropertyProvider

data class Application(
  val name: String,
  val description: String,
  val email: String,
  @Computed val updateTs: String? = null,
  @Computed val createTs: String? = null,
  val platformHealthOnly: Boolean,
  val platformHealthOnlyShowOverride: Boolean,
  val owner: String
) : ComputedPropertyProvider {

  val details: MutableMap<String, Any?> = mutableMapOf()

  @JsonAnySetter
  fun set(name: String, value: Any?) {
    details[name] = value
  }

  @JsonAnyGetter
  fun details() = details

  override fun toString(): String {
    return "Application(name='$name', description='$description', email='$email', " +
      "updateTs='$updateTs', createTs='$createTs', platformHealthOnly=$platformHealthOnly, " +
      "platformHealthOnlyShowOverride=$platformHealthOnlyShowOverride, " +
      "owner=$owner, details=$details)"
  }

  /*
   * Need to identify additional ignored properties that are in
   * the 'details' map
   */
  @Deprecated(message = "use method from ComputedPropertyProvider")
  fun computedPropertiesToIgnore(): List<String> {
    return listOf("user","lastModifiedBy","requiredGroupMembership")
  }

  override fun additionalComputedProperties(): List<String> = computedPropertiesToIgnore()
}
