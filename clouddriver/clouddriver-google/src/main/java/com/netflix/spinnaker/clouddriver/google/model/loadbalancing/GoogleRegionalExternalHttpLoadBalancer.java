/*
 * Copyright 2024 Harness, Inc.
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

package com.netflix.spinnaker.clouddriver.google.model.loadbalancing;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.Value;

@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class GoogleRegionalExternalHttpLoadBalancer extends GoogleLoadBalancer {
  final GoogleLoadBalancerType type = GoogleLoadBalancerType.HTTP;
  final GoogleLoadBalancingScheme loadBalancingScheme = GoogleLoadBalancingScheme.EXTERNAL;

  /** Default backend service a request is sent to if no host rules are matched. */
  GoogleBackendService defaultService;

  /** List of host rules that map incoming requests to GooglePathMatchers based on host header. */
  List<GoogleHostRule> hostRules;

  /** SSL certificate. This is populated only if this load balancer is a HTTPS load balancer. */
  String certificate;

  /**
   * Certificate map name. This is populated only if this load balancer is a HTTPS load balancer
   * using Certificate Manager.
   */
  String certificateMap;

  /**
   * The name of the UrlMap this load balancer uses to route traffic. In the Google Cloud Console,
   * the L7 load balancer name is the same as this name.
   */
  String urlMapName;

  String network;
  String subnet;

  @JsonIgnore
  public RegionalExternalHttpLbView getView() {
    return new RegionalExternalHttpLbView();
  }

  @Value
  @EqualsAndHashCode(callSuper = true)
  @ToString(callSuper = true)
  public class RegionalExternalHttpLbView extends GoogleLoadBalancerView {
    GoogleLoadBalancerType loadBalancerType = GoogleRegionalExternalHttpLoadBalancer.this.type;
    GoogleLoadBalancingScheme loadBalancingScheme =
        GoogleRegionalExternalHttpLoadBalancer.this.loadBalancingScheme;

    String name = GoogleRegionalExternalHttpLoadBalancer.this.getName();
    String account = GoogleRegionalExternalHttpLoadBalancer.this.getAccount();
    String region = GoogleRegionalExternalHttpLoadBalancer.this.getRegion();
    Long createdTime = GoogleRegionalExternalHttpLoadBalancer.this.getCreatedTime();
    String ipAddress = GoogleRegionalExternalHttpLoadBalancer.this.getIpAddress();
    String ipProtocol = GoogleRegionalExternalHttpLoadBalancer.this.getIpProtocol();
    String portRange = GoogleRegionalExternalHttpLoadBalancer.this.getPortRange();

    GoogleBackendService defaultService =
        GoogleRegionalExternalHttpLoadBalancer.this.defaultService;
    List<GoogleHostRule> hostRules = GoogleRegionalExternalHttpLoadBalancer.this.hostRules;
    String certificate = GoogleRegionalExternalHttpLoadBalancer.this.certificate;
    String certificateMap = GoogleRegionalExternalHttpLoadBalancer.this.certificateMap;
    String urlMapName = GoogleRegionalExternalHttpLoadBalancer.this.urlMapName;
    String network = GoogleRegionalExternalHttpLoadBalancer.this.network;
    String subnet = GoogleRegionalExternalHttpLoadBalancer.this.subnet;
  }
}
