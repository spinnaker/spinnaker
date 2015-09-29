/*
 * Copyright 2015 Netflix, Inc.
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

package com.netflix.spinnaker.orca.deprecation

import com.netflix.spectator.api.ExtendedRegistry
import com.netflix.spectator.api.Id
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

/**
 * @author sthadeshwar
 */
@Component
@CompileStatic
@Slf4j
class DeprecationRegistry {

  private static final String APPLICATION_TAG_KEY = "application"

  private final ExtendedRegistry extendedRegistry

  @Autowired
  DeprecationRegistry(ExtendedRegistry extendedRegistry) {
    this.extendedRegistry = extendedRegistry
  }

  void logDeprecatedUsage(String metricName, String application) {
    if (!metricName) {
      log.warn("Ignoring publish of deprecated usage metric as the metric name is null")
      return
    }
    if (!application) {
      log.warn("Ignoring publish of deprecated usage metric as the application name is null")
      return
    }
    Id id = extendedRegistry.createId(metricName).withTag(APPLICATION_TAG_KEY, application)
    extendedRegistry.counter(id).increment()
  }

}
