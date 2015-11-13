/*
 * Copyright 2015 The original authors.
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

package com.netflix.spinnaker.oort.cf.model

import com.netflix.spinnaker.oort.model.HealthState
import com.netflix.spinnaker.oort.model.Instance
import groovy.transform.EqualsAndHashCode
import org.cloudfoundry.client.lib.domain.CloudApplication
import org.cloudfoundry.client.lib.domain.InstanceInfo

/**
 * @author Greg Turnquist
 */
@EqualsAndHashCode(includes = ["name"])
class CloudFoundryApplicationInstance implements Instance, Serializable {

  String name
  HealthState healthState
  CloudApplication nativeApplication
  List<Map<String, String>> health = []
  InstanceInfo nativeInstance
  String logsLink

  @Override
  Long getLaunchTime() {
    nativeInstance.since.time
  }

  @Override
  String getZone() {
    nativeApplication.space?.organization?.name
  }

}
