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

package com.netflix.spinnaker.kato.helpers

import com.netflix.frigga.Names
import com.netflix.frigga.autoscaling.AutoScalingGroupNameBuilder
import groovy.transform.CompileStatic

@CompileStatic
abstract class AbstractServerGroupNameResolver {

  /**
   * Returns the ancestor server group name for the given cluster name
   * @param clusterName
   * @return
   */
  abstract String getPreviousServerGroupName(String clusterName)

  String resolveNextServerGroupName(String application, String stack, String details, Boolean ignoreSequence) {
    Integer nextSequence = 0
    String clusterName
    if (!stack && !details) {
      clusterName = application
    } else if (!stack && details) {
      clusterName = "${application}--${details}"
    } else if (stack && !details) {
      clusterName = "${application}-${stack}"
    } else {
      clusterName = "${application}-${stack}-${details}"
    }
    String previousServerGroupName = getPreviousServerGroupName(clusterName)
    if (previousServerGroupName) {
      Names parts = Names.parseName(previousServerGroupName)
      nextSequence = ((parts.sequence ?: 0) + 1) % 1000
    }
    return generateServerGroupName(application, stack, details, nextSequence, ignoreSequence)
  }

  static String generateServerGroupName(String application, String stack, String details, Integer sequence, Boolean ignoreSequence) {
    def builder = new AutoScalingGroupNameBuilder(appName: application, stack: stack, detail: details)
    def groupName = builder.buildGroupName(true)
    if (ignoreSequence) {
      return groupName
    }
    String.format("%s-v%03d", groupName, sequence)
  }
}
