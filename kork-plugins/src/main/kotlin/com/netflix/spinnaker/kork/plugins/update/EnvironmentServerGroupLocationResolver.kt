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
package com.netflix.spinnaker.kork.plugins.update

import org.springframework.core.env.Environment

/**
 * Sources the running service's server group location from the environment.
 */
class EnvironmentServerGroupLocationResolver(
  private val environment: Environment
) : ServerGroupLocationResolver {
  override fun get(): String? =
    SEARCH_CHAIN.firstOrNull { environment.getProperty(it) != null }

  private companion object {
    val SEARCH_CHAIN = listOf(
      "EC2_REGION",
      "spinnaker.extensibility.serverGroupLocation"
    )
  }
}
