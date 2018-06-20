/*
 * Copyright 2018 Netflix, Inc.
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
package com.netflix.spinnaker.keel.asset.pipeline

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.keel.*
import com.netflix.spinnaker.keel.asset.notFound
import com.netflix.spinnaker.keel.dryrun.ChangeSummary
import com.netflix.spinnaker.keel.dryrun.ChangeType
import com.netflix.spinnaker.keel.front50.Front50Service
import com.netflix.spinnaker.keel.front50.model.PipelineConfig
import com.netflix.spinnaker.keel.model.OrchestrationRequest
import com.netflix.spinnaker.keel.model.OrchestrationTrigger
import com.netflix.spinnaker.keel.state.StateInspector
import org.springframework.stereotype.Component
import retrofit.RetrofitError

@Component
class PipelineAssetProcessor(
  private val front50Service: Front50Service,
  private val objectMapper: ObjectMapper,
  private val pipelineConverter: PipelineConverter
): AssetProcessor<PipelineAsset> {

  override fun supports(asset: Asset<AssetSpec>) = asset is PipelineAsset

  override fun converge(asset: PipelineAsset): ConvergeResult {
    val changeSummary = ChangeSummary(asset.id())

    val currentState = getPipelineConfig(asset.spec.application, asset.spec.name)

    if (currentStateUpToDate(asset.id(), currentState, asset.spec, changeSummary)) {
      changeSummary.addMessage(ConvergeReason.UNCHANGED.reason)
      return ConvergeResult(listOf(), changeSummary)
    }

    if (missingApplication(asset.spec.application)) {
      changeSummary.addMessage("The application this pipeline is meant for is missing: ${asset.spec.application}")
      changeSummary.type = ChangeType.FAILED_PRECONDITIONS
      return ConvergeResult(listOf(), changeSummary)
    }

    changeSummary.type = if (currentState == null) ChangeType.CREATE else ChangeType.UPDATE

    return ConvergeResult(listOf(
      OrchestrationRequest(
        name = "Upsert pipeline",
        application = asset.spec.application,
        description = (if (currentState == null) "Create" else "Update") + " pipeline '${asset.spec.name}'",
        job = pipelineConverter.convertToJob(ConvertPipelineToJob(asset.spec, currentState?.id), changeSummary),
        trigger = OrchestrationTrigger(asset.id())
      )
    ), changeSummary)
  }

  private fun currentStateUpToDate(assetId: String,
                                   currentState: PipelineConfig?,
                                   desiredState: PipelineSpec,
                                   changeSummary: ChangeSummary): Boolean {
    val desired = pipelineConverter.convertToState(desiredState)

    if (currentState == null) return false
    val diff = StateInspector(objectMapper).run {
      getDiff(
        assetId = assetId,
        currentState = currentState,
        desiredState = desired,
        modelClass = PipelineConfig::class,
        specClass = PipelineSpec::class
      )
    }
    changeSummary.diff = diff
    return diff.isEmpty()
  }

  private fun getPipelineConfig(application: String, name: String): PipelineConfig? {
    try {
      return front50Service.getPipelineConfigs(application).firstOrNull { it.name == name }
    } catch (e: RetrofitError) {
      if (e.notFound()) {
        return null
      }
      throw e
    }
  }

  private fun missingApplication(application: String): Boolean {
    try {
      front50Service.getApplication(application)
      return false
    } catch (e: RetrofitError) {
      if (e.notFound()) {
        return true
      }
      throw e
    }
  }
}
