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

import com.netflix.spinnaker.keel.dryrun.ChangeSummary
import com.netflix.spinnaker.keel.model.OrchestrationRequest

/**
 * Processes an specific desired state asset, producing a ConvergeResult, which can then be converted into a
 * human-/api-friendly plan, or into actual Spinnaker tasks & executions.
 */
interface AssetProcessor<in I : Asset<AssetSpec>> {

  fun supports(asset: Asset<AssetSpec>): Boolean

  fun converge(asset: I): ConvergeResult
}

data class ConvergeResult(
  val orchestrations: List<OrchestrationRequest>,
  val changeSummary: ChangeSummary
)

enum class ConvergeReason(val reason: String) {
  UNCHANGED("System state matches desired state"),
  CHANGED("System state does not match desired state")
}
