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

package com.netflix.spinnaker.kork.plugins.sdk.yaml

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.netflix.spinnaker.kork.plugins.api.yaml.YamlResourceLoader

/**
 * A Jackson-backed [YamlResourceLoader].
 */
class JacksonYamlResourceLoader(
  private val pluginClass: Class<*>
) : YamlResourceLoader {

  private val mapper: ObjectMapper = ObjectMapper(YAMLFactory())
    .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)

  override fun <T : Any?> loadResource(resourceName: String, toValueType: Class<T>): T? {
    pluginClass.classLoader.getResourceAsStream(resourceName).use { inputStream ->
      if (inputStream != null) {
        return mapper.readValue(inputStream, toValueType)
      }
      throw IllegalArgumentException(
        "Cannot load specified resource '$resourceName' for '${pluginClass.simpleName}'"
      )
    }
  }
}
