/*
 * Copyright 2016 Netflix, Inc.
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

package com.netflix.spinnaker.clouddriver.core.provider.agent

import com.google.common.base.CaseFormat
import com.google.common.collect.ImmutableSet
import groovy.transform.CompileStatic

@CompileStatic
enum Namespace {
  CERTIFICATES,
  IMAGES,
  NAMED_IMAGES,
  SERVER_GROUPS(["application", "cluster", "account", "region", "serverGroup", "stack", "detail", "sequence"]),
  INSTANCES(["account", "region", "instanceId"]),
  LAUNCH_CONFIGS,
  LISTENERS,
  LOAD_BALANCERS(["account", "region", "loadBalancer", "vpcId", "application", "stack", "detail", "loadBalancerType"]),
  TARGET_GROUPS,
  CLUSTERS(["application", "account", "cluster", "stack", "detail"]),
  APPLICATIONS,
  HEALTH,
  ON_DEMAND,
  RESERVATION_REPORTS,
  RESERVED_INSTANCES,
  PROJECT_CLUSTERS

  public final String ns
  final Set<String> fields

  private Namespace(List<String> keyFields = []) {
    ns = CaseFormat.UPPER_UNDERSCORE.to(CaseFormat.LOWER_CAMEL, name()) // FOO_BAR -> fooBar
    fields = ImmutableSet.copyOf(["provider", "type"] + keyFields);
  }

  String toString() {
    ns
  }

  String getNs() {
    return ns
  }
}
