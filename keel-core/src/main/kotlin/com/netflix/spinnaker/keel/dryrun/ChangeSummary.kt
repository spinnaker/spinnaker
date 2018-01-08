/*
 * Copyright 2017 Netflix, Inc.
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

package com.netflix.spinnaker.keel.dryrun

import com.netflix.spinnaker.keel.state.FieldState

/**
 * A value-object that describes the calculated changes as a result of a de-convergence in desired and actual system
 * state.
 *
 * TODO rz - Need to include an ID for the summary; in multi-intent requests, unknown what is referring to what
 */
data class ChangeSummary(
  val message: MutableList<String> = mutableListOf()
) {
  var type: ChangeType = ChangeType.NO_CHANGE
  var diff: List<FieldState> = emptyList()

  fun addMessage(msg: String) {
    message.add(msg)
  }

  override fun toString(): String {
    var msg = "ChangeSummary(type=$type, message=$message"
    if (type.includesDiff()) msg += ", diff=$diff"
    msg += ")"
    return msg
  }
}

enum class ChangeType {
  CREATE,
  UPDATE,
  DELETE,
  NO_CHANGE,
  FAILED_PRECONDITIONS;

  fun includesDiff() = !listOf(CREATE, NO_CHANGE, FAILED_PRECONDITIONS).contains(this)
}
