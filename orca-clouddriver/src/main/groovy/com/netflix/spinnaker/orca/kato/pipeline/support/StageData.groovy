/*
 * Copyright 2015 Netflix, Inc.
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

package com.netflix.spinnaker.orca.kato.pipeline.support

import com.netflix.frigga.autoscaling.AutoScalingGroupNameBuilder
import com.netflix.spinnaker.moniker.Moniker
import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString

class StageData {
  public static final EMPTY_SOURCE = new Source()

  String strategy
  String account
  String credentials
  String region
  String namespace
  String freeFormDetails
  String application
  String stack
  Moniker moniker
  @Deprecated String providerType = "aws"
  String cloudProvider = "aws"
  boolean scaleDown
  Map<String, List<String>> availabilityZones
  int maxRemainingAsgs
  Boolean useSourceCapacity
  Boolean preferSourceCapacity
  Source source

  @Deprecated
  long delayBeforeDisableSec
  long delayBeforeScaleDownSec

  long delayBeforeCleanup
  PipelineBeforeCleanup pipelineBeforeCleanup

  String getCluster() {
    if (moniker?.cluster) {
      return moniker.cluster
    } else {
      def builder = new AutoScalingGroupNameBuilder()
      builder.appName = application
      builder.stack = stack
      builder.detail = freeFormDetails
      return builder.buildGroupName()
    }
  }

  String getAccount() {
    if (account && credentials && account != credentials) {
      throw new IllegalStateException("Cannot specify different values for 'account' and 'credentials' (${application})")
    }
    return account ?: credentials
  }

  long getDelayBeforeCleanup() {
    return this.delayBeforeCleanup ?: this.delayBeforeDisableSec
  }

  long getDelayBeforeScaleDown() {
    return this.delayBeforeScaleDownSec
  }

  @Deprecated
  List<String> getRegions() {
    availabilityZones?.keySet()?.toList() ?: (region ? [region] : [])
  }

  String getRegion() {
    region ?: availabilityZones?.keySet()?.getAt(0)
  }

  Boolean getUseSourceCapacity() {
    if (source?.useSourceCapacity != null) {
      return source.useSourceCapacity
    }

    return useSourceCapacity ?: false
  }

  Boolean getPreferSourceCapacity() {
    if (source?.preferSourceCapacity != null) {
      return source.preferSourceCapacity
    }

    return preferSourceCapacity ?: false
  }

  @EqualsAndHashCode
  @ToString
  static class Source {
    String account
    String region
    String asgName
    String serverGroupName
    Boolean useSourceCapacity
    Boolean preferSourceCapacity
  }

  static class PipelineBeforeCleanup {
    String application
    String pipelineId
    Map<String, Object> pipelineParameters = [:]
  }
}
