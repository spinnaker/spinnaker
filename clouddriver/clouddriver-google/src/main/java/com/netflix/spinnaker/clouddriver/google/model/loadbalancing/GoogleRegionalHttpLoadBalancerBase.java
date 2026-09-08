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

import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * Base class for regional HTTP load balancers (internal managed and regional external). Contains
 * common properties shared by both types.
 */
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public abstract class GoogleRegionalHttpLoadBalancerBase extends GoogleLoadBalancer {

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
}
