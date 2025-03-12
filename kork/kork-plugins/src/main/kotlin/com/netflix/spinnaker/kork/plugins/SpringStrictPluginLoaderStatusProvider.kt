/*
 * Copyright 2020 Armory, Inc.
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
package com.netflix.spinnaker.kork.plugins

import org.springframework.core.env.Environment

/**
 * Backs strict plugin loading by the Spring environment, instead of using text files.
 */
class SpringStrictPluginLoaderStatusProvider(
  private val environment: Environment
) {

  /**
   * Returns whether or not the service is configured to be strict about plugin loading or not.
   *
   * When in strict mode, the service will fail to start if any plugin cannot be loaded.
   *
   * Defaults to true.
   */
  fun isStrictPluginLoading(): Boolean =
    environment.getProperty("spinnaker.extensibility.strict-plugin-loading")?.toBoolean() ?: true
}
