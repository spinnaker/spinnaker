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

class StageData {
  String strategy
  String account
  String credentials
  String freeFormDetails
  String application
  String stack
  String providerType = "aws"
  boolean scaleDown
  Map<String, List<String>> availabilityZones
  int maxRemainingAsgs

  Source source

  String getCluster() {
    def builder = new AutoScalingGroupNameBuilder()
    builder.appName = application
    builder.stack = stack
    builder.detail = freeFormDetails

    return builder.buildGroupName()
  }

  String getAccount() {
    if (account && credentials && account != credentials) {
      throw new IllegalStateException("Cannot specify different values for 'account' and 'credentials' (${application})")
    }
    return account ?: credentials
  }

  static class Source {
    String account
    String region
    String asgName
    Boolean useSourceCapacity
  }
}
