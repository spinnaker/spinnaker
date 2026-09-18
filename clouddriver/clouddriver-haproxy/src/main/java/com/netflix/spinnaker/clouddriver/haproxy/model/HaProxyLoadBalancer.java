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
import com.netflix.spinnaker.clouddriver.model.LoadBalancer;
import com.netflix.spinnaker.clouddriver.model.LoadBalancerServerGroup;
import com.netflix.spinnaker.moniker.Moniker;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** An HAProxy frontend section presented as a Spinnaker load balancer. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HaProxyLoadBalancer implements LoadBalancer {
  private String name;
  private String account;
  private String region;
  private Moniker moniker;

  /** Frontend proxy mode ({@code http} or {@code tcp}). */
  private String mode;

  /** Bind name mapped to its configuration (address, port, ssl, ...). */
  private Map<String, Object> binds;

  private String defaultBackend;

  /** Data Plane API metadata attached to the frontend. */
  private Map<String, Object> metadata;

  @Builder.Default private Set<LoadBalancerServerGroup> serverGroups = new HashSet<>();

  @Override
  public String getCloudProvider() {
    return HaProxyProvider.ID;
  }
}
