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

public class EcsServerGroupName {

  private Moniker moniker;

  public EcsServerGroupName(String fullName) {
    this(MonikerHelper.applicationNameToMoniker(fullName));
  }

  public EcsServerGroupName(Moniker moniker) {
    this.moniker = moniker;
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
    return String.join("-", getFamilyName(), getContainerName());
  }

  public String getContainerName() {
    return String.format("v%03d", moniker.getSequence());
  }

  @Override
  public String toString() {
    return getServiceName();
  }
}
