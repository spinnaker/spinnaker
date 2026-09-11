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

package com.netflix.spinnaker.clouddriver.google.provider.agent;

import static com.netflix.spinnaker.clouddriver.google.model.loadbalancing.GoogleTargetProxyType.HTTP;
import static com.netflix.spinnaker.clouddriver.google.model.loadbalancing.GoogleTargetProxyType.HTTPS;

import com.google.api.services.compute.model.ForwardingRule;
import com.netflix.spinnaker.clouddriver.google.model.callbacks.Utils;
import com.netflix.spinnaker.clouddriver.google.model.loadbalancing.GoogleTargetProxyType;
import java.util.Set;

final class GoogleLoadBalancerCacheSupport {
  private GoogleLoadBalancerCacheSupport() {}

  /**
   * Regional managed HTTP(S) load balancers are proxy LBs: forwarding rules own a target HTTP(S)
   * proxy and use a managed load-balancing scheme.
   */
  static boolean isRegionalManagedHttpForwardingRule(
      ForwardingRule forwardingRule, String loadBalancingScheme) {
    GoogleTargetProxyType type =
        forwardingRule.getTarget() != null
            ? Utils.getTargetProxyType(forwardingRule.getTarget())
            : null;
    return loadBalancingScheme.equals(forwardingRule.getLoadBalancingScheme())
        && (type == HTTP || type == HTTPS);
  }

  /**
   * Regional passthrough load balancers point directly at a backend service and never at a target
   * proxy. The optional protocol allow-list separates external TCP/UDP NLBs from broader internal
   * passthrough support.
   */
  static boolean isRegionalPassthroughForwardingRule(
      ForwardingRule forwardingRule, String loadBalancingScheme, Set<String> allowedProtocols) {
    if (forwardingRule == null
        || forwardingRule.getBackendService() == null
        || forwardingRule.getTarget() != null
        || !loadBalancingScheme.equals(forwardingRule.getLoadBalancingScheme())) {
      return false;
    }
    return allowedProtocols == null || allowedProtocols.contains(forwardingRule.getIPProtocol());
  }
}
