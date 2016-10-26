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

package com.netflix.spinnaker.clouddriver.core

import groovy.transform.Canonical
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.NestedConfigurationProperty

@Canonical
@ConfigurationProperties('redis')
class RedisConfigurationProperties {

  @Canonical
  static class PollConfiguration {
    int intervalSeconds = 30
    int timeoutSeconds = 300
  }

  @NestedConfigurationProperty
  final PollConfiguration poll = new PollConfiguration()

  String connection = "redis://localhost:6379"
  String connectionPrevious = null

  int timeout = 2000

  String scheduler = 'default'
  int parallelism = -1
}
