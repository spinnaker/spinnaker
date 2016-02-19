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

package com.netflix.spinnaker.clouddriver.google.model

import com.fasterxml.jackson.annotation.JsonIgnore
import groovy.transform.Canonical

@Canonical
class GoogleHealthCheck {
  String requestPath
  int port

  // Attributes
  int checkIntervalSec
  int timeoutSec
  int unhealthyThreshold
  int healthyThreshold

  @JsonIgnore
  View getView() {
    new View()
  }

  class View implements Serializable {
    int checkIntervalSec = checkIntervalSec
    int timeoutSec = timeoutSec
    int unhealthyThreshold = unhealthyThreshold
    int healthyThreshold = healthyThreshold

    String getTarget() {
      port ? "HTTP:${port}${requestPath ?: '/'}" : null
    }

  }
}
