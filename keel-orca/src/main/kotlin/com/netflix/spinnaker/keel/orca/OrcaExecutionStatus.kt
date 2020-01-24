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

package com.netflix.spinnaker.keel.orca

enum class OrcaExecutionStatus {
  NOT_STARTED,
  RUNNING,
  PAUSED,
  SUSPENDED,
  SUCCEEDED,
  FAILED_CONTINUE,
  TERMINAL,
  CANCELED,
  REDIRECT,
  STOPPED,
  SKIPPED;

  fun isFailure() = listOf(TERMINAL, FAILED_CONTINUE, STOPPED, CANCELED).contains(this)
  fun isSuccess() = listOf(SUCCEEDED).contains(this)
  fun isIncomplete() = listOf(NOT_STARTED, RUNNING, PAUSED, SUSPENDED).contains(this)
  fun isComplete() = isFailure() || isSuccess()
}
