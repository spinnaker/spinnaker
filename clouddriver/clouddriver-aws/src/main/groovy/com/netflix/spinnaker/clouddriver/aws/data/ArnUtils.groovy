/*
 * Copyright 2017 Netflix, Inc.
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

package com.netflix.spinnaker.clouddriver.aws.data

import java.util.regex.Pattern

class ArnUtils {
  private static final Pattern ELBV2_ARN_PATTERN = Pattern.compile(/^arn:aws(?:-cn|-us-gov)?:elasticloadbalancing:[^:]+:[^:]+:loadbalancer\/([^:]+)\/([^\/]+)\/.+$/)
  private static final Pattern TARGET_GROUP_ARN_PATTERN = Pattern.compile(/^arn:aws(?:-cn|-us-gov)?:elasticloadbalancing:[^:]+:[^:]+:targetgroup\/([^\/]+)\/.+$/)

  static Optional<String> extractLoadBalancerName(String loadBalancerArn) {
    def m = ELBV2_ARN_PATTERN.matcher(loadBalancerArn)
    if (m.matches() && m.groupCount() > 1) {
      return Optional.of(m.group(2))
    }
    return Optional.empty()
  }

  static String extractLoadBalancerType(String loadBalancerArn) {
    def m = ELBV2_ARN_PATTERN.matcher(loadBalancerArn)
    if (m.matches()) {
      String typeShort = m.group(1)
      if (typeShort.equals("app")) {
        return "application"
      } else if (typeShort.equals("net")) {
        return "network"
      }
    }
    return "unknown"
  }

  static Optional<String> extractTargetGroupName(String targetGroupArn) {
    def m = TARGET_GROUP_ARN_PATTERN.matcher(targetGroupArn)
    if (m.matches()) {
      return Optional.of(m.group(1))
    }
    return Optional.empty()
  }
}
