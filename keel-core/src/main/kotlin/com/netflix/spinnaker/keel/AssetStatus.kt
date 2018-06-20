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
package com.netflix.spinnaker.keel

/**
 * ACTIVE: An Asset is currently being enforced and will be regularly checked for any state to converge on.
 * INACTIVE: An Asset that is not currently being enforced, but whose resource may still exist.
 * ABSENT: An Asset whose underlying resource has been deleted, and its expected state is to remain deleted.
 * ISOLATED_ACTIVE: An Asset that is meant to be applied once, but has not been yet.
 * ISOLATED_INACTIVE: An Asset that is meant to be applied once, and has been.
 * ISOLATED_ABSENT: An Asset whose underlying resource has been deleted, and its absence unenforced.
 */
enum class AssetStatus {
  ACTIVE,
  INACTIVE,
  ABSENT,
  ISOLATED_ACTIVE,
  ISOLATED_INACTIVE,
  ISOLATED_ABSENT;

  fun shouldSchedule() = scheduleValues().contains(this)
  fun shouldIsolate() = isolateValues().contains(this)
  fun shouldDeleteResource() = absentValues().contains(this)

  companion object {
    fun scheduleValues() = listOf(ACTIVE, ABSENT, ISOLATED_ACTIVE, ISOLATED_ABSENT)
    fun isolateValues() = listOf(ISOLATED_ACTIVE, ISOLATED_INACTIVE, ISOLATED_ABSENT)
    fun absentValues() = listOf(ABSENT, ISOLATED_ABSENT)
  }
}
