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
package com.netflix.spinnaker.keel.diff

import com.netflix.spinnaker.keel.api.Resource

/**
 * The result of diffing a submitted resource
 *
 * @status: a general status
 * @resourceId: the auto-generated resourceId
 * @resource: the fully resolved resource after any internal expansion
 * @diff: if present, the json view of the diff between current and desired
 * @errorMsg: if the desired state fails to be calculated this will contain a reason why
 */
data class DiffResult(
  val status: DiffStatus,
  val resourceId: String? = null,
  val resource: Resource<*>? = null,
  val diff: Map<String, Any?>? = null,
  val errorMsg: String? = null
)

data class EnvironmentDiff(
  val name: String,
  val manifestName: String,
  val resourceDiffs: List<DiffResult>
)
