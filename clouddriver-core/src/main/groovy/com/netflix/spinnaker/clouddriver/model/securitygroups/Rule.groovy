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

package com.netflix.spinnaker.clouddriver.model.securitygroups

import com.fasterxml.jackson.annotation.JsonTypeInfo
import groovy.transform.Canonical
import groovy.transform.Sortable

/**
 * An abstract interface representing a security rule.
 *
 * @see IpRangeRule
 * @see SecurityGroupRule
 */
@JsonTypeInfo(use=JsonTypeInfo.Id.CLASS, include=JsonTypeInfo.As.PROPERTY, property='class')
interface Rule {
  /**
   * The port ranges associated with this rule
   *
   * @return
   */
  SortedSet<PortRange> getPortRanges()

  String getProtocol()

  @Sortable
  @Canonical
  static class PortRange {
    Integer startPort
    Integer endPort
  }
}
