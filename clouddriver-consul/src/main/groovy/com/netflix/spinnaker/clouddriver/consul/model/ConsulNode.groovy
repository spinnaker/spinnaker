/*
 * Copyright 2016 Google, Inc.
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

package com.netflix.spinnaker.clouddriver.consul.model

class ConsulNode {
  boolean running
  List<ConsulHealth> healths
  List<ConsulService> services

  boolean isDisabled() {
    healths.any { health -> // If a check is registered as "_node_maintanence" by consul, it's been disabled.
      health.isSystemHealth()
    }
  }

  List<ConsulHealth> getHealths() {
    if (isDisabled()) { // If the node is disabled, we only return the system health to properly report to Spinnaker that the node should be treated as disabled.
      return healths.findAll { health ->
        health.isSystemHealth()
      }
    } else {
      return healths
    }
  }
}
