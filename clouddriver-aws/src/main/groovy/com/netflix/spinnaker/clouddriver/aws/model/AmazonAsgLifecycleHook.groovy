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

package com.netflix.spinnaker.clouddriver.aws.model

import groovy.transform.CompileStatic

@CompileStatic
class AmazonAsgLifecycleHook {

  String name
  String roleARN
  String notificationTargetARN
  String notificationMetadata
  Transition lifecycleTransition
  Integer heartbeatTimeout
  DefaultResult defaultResult

  static enum TransitionType {
    NOTIFICATION,
    LIFECYCLE
  }

  static enum Transition {
    EC2InstanceLaunching("autoscaling:EC2_INSTANCE_LAUNCHING", TransitionType.LIFECYCLE),
    EC2InstanceTerminating("autoscaling:EC2_INSTANCE_TERMINATING", TransitionType.LIFECYCLE),
    EC2InstanceLaunchError("autoscaling:EC2_INSTANCE_LAUNCH_ERROR", TransitionType.NOTIFICATION)

    final String value
    final TransitionType type

    Transition(String value, TransitionType transitionType) {
      this.value = value
      this.type = transitionType
    }

    String toString() {
      value
    }

    static Transition valueOfName(String name) {
      values().find { it.value == name }
    }
  }

  enum DefaultResult {
    CONTINUE,
    ABANDON
  }
}
