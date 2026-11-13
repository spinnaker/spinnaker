/*
 * Copyright 2026 McIntosh.farm
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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
package com.netflix.spinnaker.clouddriver.haproxy.model;

import com.netflix.spinnaker.clouddriver.haproxy.HaProxyProvider;
import com.netflix.spinnaker.clouddriver.model.SecurityGroup;
import com.netflix.spinnaker.clouddriver.model.SecurityGroupSummary;
import com.netflix.spinnaker.clouddriver.model.securitygroups.Rule;
import com.netflix.spinnaker.moniker.Moniker;
import java.util.HashSet;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.Value;

/**
 * The client-access policy of an HAProxy frontend — its {@code src} ACLs and the {@code
 * http-request allow/deny} rules that reference them — presented as a Spinnaker security group.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HaProxySecurityGroup implements SecurityGroup {
  private String id;
  private String name;
  private String accountName;
  private String region;
  private Moniker moniker;

  @Builder.Default private Set<Rule> inboundRules = new HashSet<>();
  @Builder.Default private Set<Rule> outboundRules = new HashSet<>();

  @Override
  public String getCloudProvider() {
    return HaProxyProvider.ID;
  }

  @Override
  public SecurityGroupSummary getSummary() {
    return new HaProxySecurityGroupSummary(name, id);
  }

  @Value
  public static class HaProxySecurityGroupSummary implements SecurityGroupSummary {
    String name;
    String id;
  }
}
