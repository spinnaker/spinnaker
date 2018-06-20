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
package com.netflix.spinnaker.keel.asset.processor

import com.netflix.spinnaker.keel.Asset
import com.netflix.spinnaker.keel.AssetProcessor
import com.netflix.spinnaker.keel.AssetSpec
import com.netflix.spinnaker.keel.ConvergeResult
import com.netflix.spinnaker.keel.asset.ParrotAsset
import com.netflix.spinnaker.keel.dryrun.ChangeSummary
import com.netflix.spinnaker.keel.model.Job
import com.netflix.spinnaker.keel.model.OrchestrationRequest
import com.netflix.spinnaker.keel.model.OrchestrationTrigger
import org.springframework.stereotype.Component

@Component
class ParrotAssetProcessor : AssetProcessor<ParrotAsset> {

  override fun supports(asset: Asset<AssetSpec>) = asset is ParrotAsset

  override fun converge(asset: ParrotAsset): ConvergeResult {
    val changeSummary = ChangeSummary(asset.id())
    changeSummary.addMessage("Squawk!")
    return ConvergeResult(listOf(
      OrchestrationRequest(
        name = "Squawk!",
        application = asset.spec.application,
        description = asset.spec.description,
        job = listOf(
          Job("wait", mutableMapOf("waitTime" to asset.spec.waitTime))
        ),
        trigger = OrchestrationTrigger(asset.id())
      )
    ), changeSummary)
  }
}
