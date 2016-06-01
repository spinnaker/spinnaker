/*
 * Copyright 2015 Google, Inc.
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

package com.netflix.spinnaker.clouddriver.google.deploy.description

class CreateGoogleHttpLoadBalancerDescription extends AbstractGoogleCredentialsDescription {
  // Really, this is the ForwardingRule name (but be aware that the UrlMap name will be the name of the
  // load balancer in the Google Compute console).
  String loadBalancerName

  // For backend service.
  List<Backend> backends
  HealthCheck healthCheck
  Integer backendPort
  Integer backendTimeoutSec

  // For URL Map.
  List<HostRule> hostRules
  List<PathMatcher> pathMatchers

  // For forwarding rule.
  String ipAddress
  String portRange

  // For authorization.
  String accountName

  // Note that in the classes below, wrapper types are used for all the fields instead of
  // primitives. This is because many of these fields are optional and wrapper fields can
  // simply be null when not present. This makes the optional fields easier to handle.

  static class HealthCheck {
    Integer checkIntervalSec
    Integer healthyThreshold
    Integer unhealthyThreshold
    Integer port
    Integer timeoutSec
    String requestPath
  }

  static class Backend {
    String group
    String balancingMode
    Float maxUtilization
    Float capacityScaler
  }

  // It is not necessary to have pathMatchers to construct an HTTP load balancer. But it is
  // one of the key features that distinguishes it from a network load balancer, so we add
  // support for them even in this basic stage of development.
  static class HostRule {
    List<String> hosts
    String pathMatcher
  }

  static class PathMatcher {
    String name
    String defaultService
    List<PathRule> pathRules
  }

  static class PathRule {
    List<String> paths
    String service
  }
}
