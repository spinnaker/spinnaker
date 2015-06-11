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

package com.netflix.spinnaker.oort.aws.model

import com.netflix.spinnaker.oort.model.LoadBalancer
import groovy.transform.CompileStatic

@CompileStatic
class AmazonLoadBalancer extends HashMap implements LoadBalancer {

  AmazonLoadBalancer() {
    this(null, null, null)
  }

  AmazonLoadBalancer(String name, String account, String region) {
    setProperty "account", account
    setProperty "name", name
    setProperty "type", "aws"
    setProperty "region", region
    setProperty "serverGroups", new HashSet<>()
  }

  String getAccount() {
    getProperty "account"
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
