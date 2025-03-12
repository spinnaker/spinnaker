/*
 * Copyright 2020 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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
 */
package com.netflix.spinnaker.orca.plugins

import com.netflix.spinnaker.kork.plugins.api.PluginConfiguration
import com.netflix.spinnaker.kork.plugins.api.PluginSdks
import com.netflix.spinnaker.orca.api.preconfigured.jobs.PreconfiguredJobConfigurationProvider
import com.netflix.spinnaker.orca.api.preconfigured.jobs.PreconfiguredJobStageProperties
import com.netflix.spinnaker.orca.api.preconfigured.jobs.TitusPreconfiguredJobProperties
import org.pf4j.Extension

@Extension
class PreconfiguredJobConfigurationProviderExtension(
  private val pluginSdks: PluginSdks,
  private val preconfiguredJobConfigProperties: PreconfiguredJobConfigProperties
) : PreconfiguredJobConfigurationProvider {

  override fun getJobConfigurations(): List<PreconfiguredJobStageProperties?>? {
    if (preconfiguredJobConfigProperties.enabled) {
      val preconfiguredJobProperties: MutableList<TitusPreconfiguredJobProperties> = ArrayList()
      val yamlResourceLoader = pluginSdks.yamlResourceLoader()
      val titusRunJobConfigProps = yamlResourceLoader.loadResource("preconfigured.yml", TitusPreconfiguredJobProperties::class.java)
      preconfiguredJobProperties.add(titusRunJobConfigProps)

      return preconfiguredJobProperties
    }
    return null
  }
}

@PluginConfiguration("preconfigured-job-config")
class PreconfiguredJobConfigProperties {
  var enabled = true
}
