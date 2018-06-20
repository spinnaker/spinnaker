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

import com.netflix.spinnaker.keel.ApplicationAwareAssetSpec
import com.netflix.spinnaker.keel.Asset
import com.netflix.spinnaker.keel.AssetSpec
import com.netflix.spinnaker.keel.attribute.EnabledAttribute
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.stereotype.Component

@Component
@EnableConfigurationProperties(EnabledFilter.Configuration::class)
class EnabledFilter
@Autowired constructor(
  private val config: Configuration
): Filter {

  override fun getOrder() = 10

  override fun filter(asset: Asset<AssetSpec>): Boolean {
    // Asset-level overrides overrule all other settings
    if (asset.getAttribute(EnabledAttribute::class).takeIf { it != null }?.value == false) {
      return false
    }

    when (asset.spec) {
      is ApplicationAwareAssetSpec -> {
        if (config.global && config.disabledApplications.contains(asset.spec.application)) {
          return false
        }
        if (!config.global && config.enabledApplications.contains(asset.spec.application)) {
          return true
        }
        return config.global
      }
      else                         -> return config.global
    }
  }

  @ConfigurationProperties
  open class Configuration {
    val global: Boolean = true
    val enabledApplications: List<String> = listOf()
    val disabledApplications: List<String> = listOf()
  }
}
