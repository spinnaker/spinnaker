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
package com.netflix.spinnaker.kork.plugins.sdk

import com.netflix.spinnaker.kork.exceptions.SystemException
import com.netflix.spinnaker.kork.plugins.api.PluginSdks
import com.netflix.spinnaker.kork.plugins.api.httpclient.HttpClientRegistry
import com.netflix.spinnaker.kork.plugins.api.yaml.YamlResourceLoader

/**
 * The implementation of the [PluginSdks] SDK.
 */
class PluginSdksImpl(
  private val sdkServices: List<Any>
) : PluginSdks {

  override fun http(): HttpClientRegistry =
    service(HttpClientRegistry::class.java)

  override fun yamlResourceLoader(): YamlResourceLoader =
    service(YamlResourceLoader::class.java)

  private fun <T> service(serviceClass: Class<T>): T =
    sdkServices.filterIsInstance(serviceClass).firstOrNull()
      // This should never happen: If it does, it means that the [serviceClass] did not get initialized.
      ?: throw SystemException("$serviceClass is not configured")
}
