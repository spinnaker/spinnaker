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
package com.netflix.spinnaker.keel.filter

import com.netflix.spinnaker.keel.Asset
import com.netflix.spinnaker.keel.AssetPriority
import com.netflix.spinnaker.keel.AssetSpec
import com.netflix.spinnaker.keel.PriorityMatcher
import com.netflix.spinnaker.keel.attribute.PriorityAttribute
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.stereotype.Component

@Component
@EnableConfigurationProperties(PriorityFilter.Configuration::class)
class PriorityFilter
@Autowired constructor(
  private val config: Configuration
): Filter {

  override fun getOrder() = 100

  override fun filter(asset: Asset<AssetSpec>): Boolean {
    if (!config.enabled) {
      return true
    }

    val priority = asset.getAttribute(PriorityAttribute::class) ?: return true

    return when (config.matcher) {
      PriorityMatcher.EQUAL -> priority.value == config.threshold
      PriorityMatcher.EQUAL_GT -> priority.value >= config.threshold
      PriorityMatcher.EQUAL_LT -> priority.value <= config.threshold
    }
  }

  @ConfigurationProperties
  open class Configuration {
    val enabled: Boolean = false
    val threshold: AssetPriority = AssetPriority.LOW
    val matcher: PriorityMatcher = PriorityMatcher.EQUAL_GT
  }
}
