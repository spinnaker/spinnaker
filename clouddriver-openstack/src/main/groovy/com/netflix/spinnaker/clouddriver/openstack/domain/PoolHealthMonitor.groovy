/*
 * Copyright 2016 Target, Inc.
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

package com.netflix.spinnaker.clouddriver.openstack.domain

import groovy.transform.AutoClone
import groovy.transform.Canonical
import org.openstack4j.api.Builders
import org.openstack4j.model.network.ext.HealthMonitor
import org.openstack4j.model.network.ext.HealthMonitorType
import org.openstack4j.openstack.networking.domain.ext.NeutronHealthMonitor

@AutoClone
@Canonical
class PoolHealthMonitor {
  String id
  PoolHealthMonitorType type
  int delay
  int timeout
  int maxRetries

  /** Used only with HTTP/HTTPS types */
  String httpMethod
  String url
  List<Integer> expectedHttpStatusCodes

  static PoolHealthMonitor from(HealthMonitor healthMonitor) {
    new PoolHealthMonitor(id: healthMonitor.id, type: PoolHealthMonitorType.valueOf(healthMonitor.type.name()),
      delay: healthMonitor.delay, timeout: healthMonitor.timeout, maxRetries: healthMonitor.maxRetries,
      httpMethod: healthMonitor.httpMethod, url: healthMonitor.urlPath,
      expectedHttpStatusCodes: healthMonitor.expectedCodes?.split(',')?.toList()?.collect { Integer.parseInt(it) })
  }
}
