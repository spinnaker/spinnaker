/*
 * Copyright 2020 Expedia, Inc.
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

package com.netflix.spinnaker.clouddriver.ecs.names;

import com.netflix.spinnaker.moniker.Moniker;
import com.netflix.spinnaker.moniker.MonikerHelper;

public class EcsServerGroupName {

  private Moniker moniker;

  /**
   * When true, the ECS service name is the bare family/cluster name with no {@code -vNNN} version
   * suffix. Used by the ecs-native provider, which keeps a single durable ECS service and rolls new
   * task-definition revisions in place (via ECS-native deployments) rather than creating a new
   * versioned server group per deploy. The classic {@code ecs} provider leaves this false and keeps
   * the versioned red/black naming.
   */
  private final boolean fixedName;

  public EcsServerGroupName(String fullName) {
    this(MonikerHelper.applicationNameToMoniker(fullName));
  }

  public EcsServerGroupName(Moniker moniker) {
    this(moniker, false);
  }

  public EcsServerGroupName(Moniker moniker, boolean fixedName) {
    this.moniker = moniker;
    this.fixedName = fixedName;
  }

  public Moniker getMoniker() {
    return moniker;
  }

  public String getFamilyName() {
    String cluster = moniker.getCluster();
    String detail = moniker.getDetail();
    String stack = moniker.getStack();
    if (cluster == null) {
      cluster = MonikerHelper.getClusterName(moniker.getApp(), stack, detail);
    }
    return cluster;
  }

  public String getServiceName() {
    if (fixedName) {
      return getFamilyName();
    }
    return String.join("-", getFamilyName(), getContainerName());
  }

  public String getContainerName() {
    // A fixed-name server group has no sequence, so there is no vNNN segment to format. Callers in
    // the shared create path still invoke this (container-definition name, load-balancer and
    // service-registry container fallbacks), so return the stable family name instead of formatting
    // a null sequence (which would NPE). For artifact-based ecs-native deploys the real container
    // names come from the task-definition artifact, so this fallback value is not used as an AWS
    // container name in practice.
    if (fixedName || moniker.getSequence() == null) {
      return getFamilyName();
    }
    return String.format("v%03d", moniker.getSequence());
  }

  public boolean isFixedName() {
    return fixedName;
  }

  @Override
  public String toString() {
    return getServiceName();
  }
}
