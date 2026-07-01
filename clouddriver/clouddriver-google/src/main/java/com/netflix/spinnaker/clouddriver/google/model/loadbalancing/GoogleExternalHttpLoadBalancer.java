/*
 * Copyright 2026 Harness, Inc.
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

/**
 * Spinnaker view of a regional external Application Load Balancer.
 *
 * <p>GCP represents this load balancer as `EXTERNAL_MANAGED` regional forwarding-rule listeners
 * backed by a regional URL map. Deck treats the URL map as the logical load balancer and folds each
 * forwarding rule into a listener row.
 */
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class GoogleExternalHttpLoadBalancer extends GoogleLoadBalancer {
  final GoogleLoadBalancerType type = GoogleLoadBalancerType.EXTERNAL_MANAGED;
  final GoogleLoadBalancingScheme loadBalancingScheme = GoogleLoadBalancingScheme.EXTERNAL_MANAGED;

  /** Default backend service a request is sent to if no host rules are matched. */
  GoogleBackendService defaultService;

  /** List of host rules that map incoming requests to GooglePathMatchers based on host header. */
  List<GoogleHostRule> hostRules;

  /** SSL certificate. This is populated only if this load balancer is a HTTPS load balancer. */
  String certificate;

  /** The name of the UrlMap this load balancer uses to route traffic. */
  String urlMapName;

  String network;

  @JsonIgnore
  public ExternalHttpLbView getView() {
    return new ExternalHttpLbView();
  }

  @Value
  @EqualsAndHashCode(callSuper = true)
  @ToString(callSuper = true)
  public class ExternalHttpLbView extends GoogleLoadBalancerView {
    GoogleLoadBalancerType loadBalancerType = GoogleExternalHttpLoadBalancer.this.type;
    GoogleLoadBalancingScheme loadBalancingScheme =
        GoogleExternalHttpLoadBalancer.this.loadBalancingScheme;

    String name = GoogleExternalHttpLoadBalancer.this.getName();
    String account = GoogleExternalHttpLoadBalancer.this.getAccount();
    String region = GoogleExternalHttpLoadBalancer.this.getRegion();
    Long createdTime = GoogleExternalHttpLoadBalancer.this.getCreatedTime();
    String ipAddress = GoogleExternalHttpLoadBalancer.this.getIpAddress();
    String ipProtocol = GoogleExternalHttpLoadBalancer.this.getIpProtocol();
    String portRange = GoogleExternalHttpLoadBalancer.this.getPortRange();

    GoogleBackendService defaultService = GoogleExternalHttpLoadBalancer.this.defaultService;
    List<GoogleHostRule> hostRules = GoogleExternalHttpLoadBalancer.this.hostRules;
    String certificate = GoogleExternalHttpLoadBalancer.this.certificate;
    String urlMapName = GoogleExternalHttpLoadBalancer.this.urlMapName;
    String network = GoogleExternalHttpLoadBalancer.this.network;
  }
}
