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

import com.fasterxml.jackson.annotation.JsonIgnore
import groovy.transform.Canonical

@Canonical
class BaseGoogleHttpLoadBalancer extends GoogleLoadBalancer {
  /**
   * Default backend service a request is sent to if no host rules are matched.
   */
  String defaultService

  /**
   * List of host rules that map incoming requests to GooglePathMatchers based on host header.
   */
  List<GoogleHostRule> hostRules

  @JsonIgnore
  @Override
  View getView() {
    new View()
  }

  @Canonical
  class View extends GoogleLoadBalancer.View {
    List<GoogleHostRule> hostRules = BaseGoogleHttpLoadBalancer.this.hostRules
  }
}
