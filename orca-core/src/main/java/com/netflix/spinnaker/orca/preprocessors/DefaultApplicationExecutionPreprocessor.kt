/*
 * Copyright 2019 Netflix, Inc.
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
package com.netflix.spinnaker.orca.preprocessors

import com.netflix.spinnaker.orca.config.DefaultApplicationConfigurationProperties
import com.netflix.spinnaker.orca.extensionpoint.pipeline.ExecutionPreprocessor
import org.springframework.stereotype.Component
import javax.annotation.Nonnull

/**
 * Populates an Execution config payload with a default application value if one is not provided.
 */
@Component
class DefaultApplicationExecutionPreprocessor(
  private val properties: DefaultApplicationConfigurationProperties
) : ExecutionPreprocessor {

  override fun supports(@Nonnull execution: MutableMap<String, Any>,
                        @Nonnull type: ExecutionPreprocessor.Type): Boolean = true

  override fun process(execution: MutableMap<String, Any>): MutableMap<String, Any> {
    if (!execution.containsKey("application")) {
      execution["application"] = properties.defaultApplicationName
    }
    return execution
  }
}
