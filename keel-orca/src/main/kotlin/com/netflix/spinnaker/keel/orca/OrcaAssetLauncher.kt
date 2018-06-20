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
package com.netflix.spinnaker.keel.orca

import com.netflix.spectator.api.BasicTag
import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.keel.*
import com.netflix.spinnaker.keel.dryrun.ChangeSummary
import net.logstash.logback.argument.StructuredArguments.value
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit

@Component(value = "orcaAssetLauncher")
open class OrcaAssetLauncher(
  private val assetProcessors: List<AssetProcessor<*>>,
  private val orcaService: OrcaService,
  private val registry: Registry,
  private val applicationEventPublisher: ApplicationEventPublisher,
  private val assetActivityRepository: AssetActivityRepository
) : AssetLauncher<OrcaLaunchedAssetResult> {

  private val log = LoggerFactory.getLogger(javaClass)
  private val invocationsId = registry.createId("asset.invocations", listOf(BasicTag("launcher", "orca")))
  private val invocationTimeId = registry.createId("asset.invocationTime", listOf(BasicTag("launcher", "orca")))

  override fun launch(asset: Asset<AssetSpec>) =
    recordedTime(asset) {
      val result = assetProcessor(assetProcessors, asset).converge(asset)


      if (result.orchestrations.isEmpty()) {
        log.info("State matches desired asset for {}", value("assetId", asset.id()))
        registry.counter(invocationsId
          .withTags(asset.getMetricTags())
          .withTag("change", result.changeSummary.type.toString())
        ).increment()
        return@recordedTime OrcaLaunchedAssetResult(emptyList(), result.changeSummary)
      }

      applicationEventPublisher.publishEvent(ConvergenceRequiredEvent(asset))
      val orchestrationIds = result.orchestrations.map {
        orcaService.orchestrate(it).ref
      }

      assetActivityRepository.record(AssetConvergenceRecord(
        assetId = asset.id(),
        actor = "keel:scheduledConverge",
        result = result
      ))

      log.info(
        "Launched orchestrations {} for asset {}",
        value("orchestrations", orchestrationIds),
        value("assetId", asset.id())
      )

      registry.counter(invocationsId
        .withTags(asset.getMetricTags())
        .withTag("change", result.changeSummary.type.toString())
      ).increment()

      OrcaLaunchedAssetResult(orchestrationIds, result.changeSummary)
    }

  private fun recordedTime(asset: Asset<AssetSpec>, callable: () -> OrcaLaunchedAssetResult): OrcaLaunchedAssetResult {
    val start = registry.clock().monotonicTime()
    return callable.invoke()
      .also {
        registry.timer(invocationTimeId.withTags(asset.getResultTags(it))).record(
          registry.clock().monotonicTime() - start,
          TimeUnit.NANOSECONDS
        )
      }
  }

  private fun Asset<AssetSpec>.getResultTags(result: OrcaLaunchedAssetResult) =
    getMetricTags().toMutableList().apply {
      add(BasicTag("change", result.changeSummary.type.toString()))
    }
}

data class OrcaLaunchedAssetResult(
  val orchestrationIds: List<String>,
  val changeSummary: ChangeSummary
) : LaunchedAssetResult
