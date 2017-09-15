/*
 * Copyright 2017 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

package com.netflix.kayenta.atlas.orca

import com.netflix.kayenta.atlas.config.AtlasConfigurationProperties
import spock.lang.Specification
import spock.lang.Unroll

import java.time.Duration

class AtlasFetchTaskSpec extends Specification {

  @Unroll
  void "dynamic backoff period is exponential when durationMS is #durationMS, while respecting mins/maxes"() {
    given:
    AtlasFetchTask atlasFetchTask = new AtlasFetchTask()
    atlasFetchTask.atlasConfigurationProperties = new AtlasConfigurationProperties()

    expect:
    atlasFetchTask.getDynamicBackoffPeriod(Duration.ofMillis(durationMS)) == backoffPeriodMS

    where:
    durationMS || backoffPeriodMS
    250        || 1000
    500        || 1000
    1000       || 1000
    1500       || 1000
    2000       || 2000
    2500       || 2000
    3000       || 2000
    3500       || 2000
    4000       || 4000
    6000       || 4000
    8000       || 8000
    16000      || 16000
    30000      || 16000
    32000      || 32000
    60000      || 32000
    120000     || 32000
    240000     || 32000
  }
}
