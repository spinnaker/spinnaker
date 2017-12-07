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

data class ChangeSummary(
  private val summary: MutableList<String> = mutableListOf()
) {
  var type: ChangeType = ChangeType.NO_CHANGE
  var diff: List<FieldState> = emptyList()

  fun addMessage(msg: String) {
    summary.add(msg)
  }

  override fun toString(): String {
    // TODO eb: more readable friendly format for logs/response?
    var msg = "ChangeSummary(type=$type, summary=$summary"
    if (!listOf(ChangeType.CREATE, ChangeType.NO_CHANGE, ChangeType.FAILED_PRECONDITIONS).contains(type)) msg += ", diff=$diff"
    msg += ")"
    return msg
  }
}

enum class ChangeType {
  CREATE,
  UPDATE,
  DELETE,
  NO_CHANGE,
  FAILED_PRECONDITIONS
}
