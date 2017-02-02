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

package com.netflix.spinnaker.clouddriver.appengine.model

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.google.api.services.appengine.v1.model.Service
import com.google.api.services.appengine.v1.model.Version
import com.netflix.spinnaker.clouddriver.model.Health
import com.netflix.spinnaker.clouddriver.model.HealthState

class AppengineHealth implements Health {
  HealthState state
  String source
  String type
  String healthClass = "platform"
  String description

  AppengineHealth(Version version, Service service) {
    source = "Service ${service.getId()}"
    type = "App Engine Service"

    def allocations = service.getSplit()?.getAllocations()
    state = allocations?.containsKey(version.getId()) ? HealthState.Up : HealthState.OutOfService
  }

  Map<String, String> toMap() {
    new ObjectMapper().convertValue(this, new TypeReference<Map<String, String>>() {})
  }
}
