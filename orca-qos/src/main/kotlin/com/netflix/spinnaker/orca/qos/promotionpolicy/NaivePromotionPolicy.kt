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
package com.netflix.spinnaker.orca.qos.promotionpolicy

import com.netflix.spinnaker.kork.dynamicconfig.DynamicConfigService
import com.netflix.spinnaker.orca.pipeline.model.Execution
import com.netflix.spinnaker.orca.qos.PromotionPolicy
import com.netflix.spinnaker.orca.qos.PromotionResult
import org.springframework.stereotype.Component

@Component
class NaivePromotionPolicy(
  private val configService: DynamicConfigService
) : PromotionPolicy {

  override fun apply(candidates: List<Execution>): PromotionResult {
    if (!configService.isEnabled("qos.promotionPolicy.naive", true)) {
      return PromotionResult(candidates, false, "Naive policy is disabled")
    }

    val maxPromoteSize = configService.getConfig(Int::class.java, "qos.promotionPolicy.naive.size", 1)
    val promoteSize = if (maxPromoteSize > candidates.size) candidates.size else maxPromoteSize

    return PromotionResult(
      candidates = candidates.subList(0, promoteSize),
      finalized = false,
      reason = "Naive policy promotes $maxPromoteSize execution every cycle"
    )
  }
}
