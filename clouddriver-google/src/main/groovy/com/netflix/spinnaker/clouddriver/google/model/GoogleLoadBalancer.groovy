/*
 * Copyright 2015 Google, Inc.
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

package com.netflix.spinnaker.clouddriver.google.model

import com.fasterxml.jackson.annotation.JsonAnyGetter
import com.fasterxml.jackson.annotation.JsonAnySetter
import com.netflix.spinnaker.clouddriver.google.model.callbacks.Utils
import com.netflix.spinnaker.clouddriver.model.LoadBalancer

@Deprecated
class GoogleLoadBalancer implements LoadBalancer {

  private static final String GOOGLE_LOAD_BALANCER_TYPE = "gce"

  String account
  String name
  String region
  Set<Map<String, Object>> serverGroups = new HashSet<>()

  Long createdTime
  String ipAddress
  String ipProtocol
  String portRange
  Map<String, Object> healthCheck

  private Map<String, Object> dynamicProperties = new HashMap<String, Object>()

  // Used as a deep copy-constructor.
  public static GoogleLoadBalancer newInstance(GoogleLoadBalancer originalGoogleLoadBalancer) {
    GoogleLoadBalancer copyGoogleLoadBalancer = new GoogleLoadBalancer()

    // Don't want to copy 'class'.
    def keySet = originalGoogleLoadBalancer.properties.keySet() - "class"

    keySet += originalGoogleLoadBalancer.anyProperty().keySet()

    keySet.each { key ->
      def valueCopy = Utils.getImmutableCopy(originalGoogleLoadBalancer.hasProperty(key) ? originalGoogleLoadBalancer[key] : originalGoogleLoadBalancer.anyProperty()[key])

      if (valueCopy) {
        copyGoogleLoadBalancer[key] = valueCopy
      }
    }

    copyGoogleLoadBalancer
  }

  @JsonAnyGetter
  public Map<String, Object> anyProperty() {
    return dynamicProperties;
  }

  @JsonAnySetter
  public void set(String name, Object value) {
    dynamicProperties.put(name, value);
  }

  @Override
  String getType() {
    return GOOGLE_LOAD_BALANCER_TYPE
  }

  @Override
  boolean equals(Object o) {
    if (!(o instanceof GoogleLoadBalancer)) {
      return false
    }
    GoogleLoadBalancer other = (GoogleLoadBalancer)o
    other.getAccount() == this.getAccount() && other.getName() == this.getName() && other.getType() == this.getType() && other.getServerGroups() == this.getServerGroups() && other.getRegion() == this.getRegion()
  }

  @Override
  int hashCode() {
    getAccount().hashCode() + getName().hashCode() + getType().hashCode() + getServerGroups().hashCode() + getRegion().hashCode()
  }
}
