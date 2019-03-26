/*
 * Copyright 2016 Netflix, Inc.
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

package com.netflix.spinnaker.clouddriver.aws

import groovy.transform.Canonical
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.NestedConfigurationProperty

@Canonical
@ConfigurationProperties('aws')
class AwsConfigurationProperties {

  @Canonical
  static class ClientConfig {
    int maxErrorRetry = 3
    int maxConnections = 200
    int maxConnectionsPerRoute = 20
    boolean useGzip = true
    boolean addSpinnakerUserToUserAgent = false
  }

  @Canonical
  static class CleanupConfig {
    @Canonical
    static class AlarmsConfig {
      boolean enabled = false
      int daysToKeep = 90
    }

    @NestedConfigurationProperty
    final AlarmsConfig alarms = new AlarmsConfig()
  }

  @NestedConfigurationProperty
  final ClientConfig client = new ClientConfig()
  @NestedConfigurationProperty
  final CleanupConfig cleanup = new CleanupConfig()
}
