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

package com.netflix.spinnaker.clouddriver.model;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.netflix.spinnaker.clouddriver.model.securitygroups.Rule;
import com.netflix.spinnaker.clouddriver.names.NamerRegistry;
import com.netflix.spinnaker.moniker.Moniker;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * A representation of a security group
 */
@JsonTypeInfo(use=JsonTypeInfo.Id.CLASS, include=JsonTypeInfo.As.PROPERTY, property="class")
public interface SecurityGroup {

  /**
   * The type of this security group. May reference the cloud provider to which it is associated
   * @deprecated use #getCloudProvider
   * @return
   */
  String getType();

  /**
   * Provider-specific identifier
   */
  String getCloudProvider();

  /**
   * The ID associated with this security group
   *
   * @return
   */
  String getId();

  /**
   * The name representing this security group
   *
   * @return
   */
  String getName();

  /**
   * This resource's moniker
   *
   * @return moniker
   */
  default Moniker getMoniker() {
    return NamerRegistry.getDefaultNamer().deriveMoniker(this);
  }

  /**
   * The application associated with this security group.
   *
   * Deprecated in favor of getMoniker().getApp()
   *
   * @return
   */
  @Deprecated
  String getApplication();

  /**
   * The account associated with this security group
   *
   * @return
   */
  String getAccountName();

  /**
   * The region associated with this security group
   *
   * @return
   */
  String getRegion();

  /**
   * A representation of the inbound securityRules
   *
   * @return
   */
  Set<Rule> getInboundRules();

  Set<Rule> getOutboundRules();

  SecurityGroupSummary getSummary();

  default Map<String, String> getLabels() {
    return new HashMap<>();
  }
}
