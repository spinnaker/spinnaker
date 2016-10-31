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

package com.netflix.spinnaker.clouddriver.google.model.loadbalancing

import com.netflix.spinnaker.clouddriver.google.model.GoogleHealthCheck
import groovy.transform.Canonical
import groovy.transform.EqualsAndHashCode

@EqualsAndHashCode(excludes="backends")
class GoogleBackendService {
  String name
  String region

  /**
   * Specifies the GCP endpoint 'family' this backend service originated from.
   *
   * There are currently two different sets of backend service endpoints:
   * 1. /{project}/global/backendServices/{backendServiceName}
   * 2. /{project}/regions/{region}/backendServices/{backendServiceName}
   *
   * Since we cache BackendService objects from both endpoints, we need to distinguish
   * between the two 'kinds'. That's what this field does.
   */
  BackendServiceKind kind
  String healthCheckLink
  GoogleHealthCheck healthCheck
  List<GoogleLoadBalancedBackend> backends
  GoogleSessionAffinity sessionAffinity
  Integer affinityCookieTtlSec
  GoogleLoadBalancingScheme loadBalancingScheme

  // Note: This enum has non-standard style constants because we use these constants as strings directly
  // in the redis cache keys for backend services, where we want to avoid underscores and camelcase is the norm.
  static enum BackendServiceKind {
    globalBackendService,
    regionBackendService
  }
}
