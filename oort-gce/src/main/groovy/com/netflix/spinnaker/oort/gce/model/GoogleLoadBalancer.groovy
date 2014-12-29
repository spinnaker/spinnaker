/*
 * Copyright 2014 Google, Inc.
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

package com.netflix.spinnaker.oort.gce.model

import com.netflix.spinnaker.oort.gce.model.callbacks.Utils
import com.netflix.spinnaker.oort.model.LoadBalancer
import groovy.transform.CompileStatic

@CompileStatic
class GoogleLoadBalancer extends HashMap implements LoadBalancer {

  GoogleLoadBalancer() {
    this(null, null)
  }

  GoogleLoadBalancer(String name, String region) {
    setProperty "name", name
    setProperty "type", "gce"
    setProperty "region", region
    setProperty "serverGroups", new HashSet<>()
  }

  // Used as a deep copy-constructor.
  public static GoogleLoadBalancer newInstance(GoogleLoadBalancer originalGoogleLoadBalancer) {
    GoogleLoadBalancer copyGoogleLoadBalancer = new GoogleLoadBalancer()

    originalGoogleLoadBalancer.keySet().each { key ->
      def valueCopy = Utils.getImmutableCopy(originalGoogleLoadBalancer[key])

      if (valueCopy) {
        copyGoogleLoadBalancer[key] = valueCopy
      }
    }

    copyGoogleLoadBalancer
  }

  @Override
  String getName() {
    getProperty "name"
  }

  @Override
  String getType() {
    getProperty "type"
  }

  @Override
  Set<String> getServerGroups() {
    (Set<String>) getProperty("serverGroups")
  }

  String getRegion() {
    (String) getProperty("region")
  }

  Map<String, Object> getHealthCheck() {
    (Map<String, Object>) getProperty("healthCheck")
  }

  Long getCreatedTime() {
    (Long) getProperty("createdTime")
  }

  String getIpAddress() {
    (String) getProperty("ipAddress")
  }

  String getIpProtocol() {
    (String) getProperty("ipProtocol")
  }

  String getPortRange() {
    (String) getProperty("portRange")
  }

  @Override
  boolean equals(Object o) {
    if (!(o instanceof GoogleLoadBalancer)) {
      return false
    }
    GoogleLoadBalancer other = (GoogleLoadBalancer)o
    other.getName() == this.getName() && other.getType() == this.getType() && other.getServerGroups() == this.getServerGroups() && other.getRegion() == this.getRegion()
  }

  @Override
  int hashCode() {
    getName().hashCode() + getType().hashCode() + getServerGroups().hashCode() + getRegion().hashCode()
  }
}
