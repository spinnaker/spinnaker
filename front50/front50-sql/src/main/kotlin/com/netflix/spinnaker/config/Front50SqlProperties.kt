/*
 * Copyright 2023 Salesforce, Inc.
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
package com.netflix.spinnaker.config

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

/**
 * Note that kork's SqlProperties class also uses the "sql" prefix.
 *
 * @param healthIntervalMillis The period to refresh health information (e.g. in the health endpoint).
 */
@ConfigurationProperties("sql")
class Front50SqlProperties {
  /**
   * How frequently to refresh health information (e.g. for the health endpoint).
   */
  var healthIntervalMillis: Long = Duration.ofSeconds(30).toMillis()
}
