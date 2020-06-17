/*
 * Copyright 2020 Netflix, Inc.
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
 *
 */

package com.netflix.spinnaker.clouddriver.model

class SimpleServerGroup implements ServerGroup {
  String name
  String type
  String cloudProvider
  String region
  Boolean disabled
  Long createdTime
  Set<String> zones
  Set<? extends Instance> instances
  Set<String> loadBalancers
  Set<String> securityGroups
  Map<String, Object> launchConfig
  InstanceCounts instanceCounts
  Capacity capacity
  ImagesSummary imagesSummary
  ImageSummary imageSummary

  @Override
  Boolean isDisabled() {
    return disabled
  }
}
