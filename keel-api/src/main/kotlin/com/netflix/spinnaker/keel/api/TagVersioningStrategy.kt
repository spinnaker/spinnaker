/*
 *
 * Copyright 2019 Netflix, Inc.
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
 *
 */
package com.netflix.spinnaker.keel.api

import com.netflix.spinnaker.keel.api.SortType.INCREASING
import com.netflix.spinnaker.keel.api.SortType.SEMVER

/**
 * Different options for versioning for tags
 */
enum class TagVersionStrategy(val regex: String, val sortType: SortType, val friendlyName: String) {
  INCREASING_TAG("""(^.*$)""", INCREASING, "increasing-tag"),
  SEMVER_TAG("""(^.*$)""", SEMVER, "semver-tag"),
  BRANCH_JOB_COMMIT_BY_JOB("""^master-h(\d+).*$""", INCREASING, "branch-job-commit-by-job"), // popular netflix strategy
  SEMVER_JOB_COMMIT_BY_JOB("""^v.*-h(\d+).*$""", INCREASING, "semver-job-commit-by-job"), // popular netflix strategy
  SEMVER_JOB_COMMIT_BY_SEMVER("""^v(.*)-h\d+.*$""", SEMVER, "semver-job-commit-by-semver"); // popular netflix strategy
}

enum class SortType {
  INCREASING,
  SEMVER;
}
