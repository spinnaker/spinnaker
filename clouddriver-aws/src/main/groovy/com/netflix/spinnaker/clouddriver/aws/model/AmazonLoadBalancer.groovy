/*
 * Copyright 2014 Netflix, Inc.
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

package com.netflix.spinnaker.clouddriver.aws.model

import com.fasterxml.jackson.annotation.JsonAnyGetter
import com.fasterxml.jackson.annotation.JsonAnySetter
import com.netflix.spinnaker.clouddriver.model.LoadBalancer
import groovy.transform.CompileStatic

@CompileStatic
class AmazonLoadBalancer implements LoadBalancer {

  String account
  String name
  String region
  String vpcId
  Set<Map<String, Object>> serverGroups = []

  private Map<String, Object> dynamicProperties = new HashMap<String, Object>()

  @JsonAnyGetter
  public Map<String,Object> any() {
    return dynamicProperties;
  }

  @JsonAnySetter
  public void set(String name, Object value) {
    dynamicProperties.put(name, value);
  }

  @Override
  String getType() {
    return "aws"
  }

  @Override
  boolean equals(Object o) {
    if (!(o instanceof AmazonLoadBalancer)) {
      return false
    }
    AmazonLoadBalancer other = (AmazonLoadBalancer)o
    other.getAccount() == this.getAccount() && other.getName() == this.getName() && other.getType() == this.getType() && other.getServerGroups() == this.getServerGroups() && other.getRegion() == this.getRegion()
  }

  @Override
  int hashCode() {
    getAccount().hashCode() + getName().hashCode() + getType().hashCode() + getServerGroups().hashCode() + getRegion().hashCode()
  }
}
