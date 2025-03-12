/*
 * Copyright 2019 Alibaba Group.
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

package com.netflix.spinnaker.clouddriver.alicloud.model;

import com.netflix.spinnaker.clouddriver.model.securitygroups.Rule;
import java.util.Map;
import java.util.SortedSet;

public class AliCloudSecurityGroupRule implements Rule {

  private String protocol;

  private SortedSet<PortRange> portRanges;

  private Map<String, Object> permissions;

  public AliCloudSecurityGroupRule(
      String protocol, SortedSet<PortRange> portRanges, Map<String, Object> permissions) {
    this.protocol = protocol;
    this.portRanges = portRanges;
    this.permissions = permissions;
  }

  @Override
  public SortedSet<PortRange> getPortRanges() {
    return portRanges;
  }

  @Override
  public String getProtocol() {
    return protocol;
  }

  public Map<String, Object> getPermissions() {
    return permissions;
  }
}
