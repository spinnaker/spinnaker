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
}
