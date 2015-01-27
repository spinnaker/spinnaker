/*
 * Copyright 2014 Netflix, Inc.
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
package com.netflix.spinnaker.kato.aws.model

/**
 * http://docs.aws.amazon.com/AWSJavaSDK/latest/javadoc/com/amazonaws/services/autoscaling/model/ProcessType.html
 *
 * There are two primary Auto Scaling process types-- Launch and Terminate.
 * The Launch process creates a new Amazon EC2 instance for an Auto Scaling group, and the Terminate process
 * removes an existing Amazon EC2 instance.
 *
 * The remaining Auto Scaling process types relate to specific Auto Scaling features:
 * AddToLoadBalancer
 * AlarmNotification
 * AZRebalance
 * HealthCheck
 * ReplaceUnhealthy
 * ScheduledActions
 */
enum AutoScalingProcessType {
  Launch,
  Terminate,
  AddToLoadBalancer,
  AlarmNotification,
  AZRebalance,
  HealthCheck,
  ReplaceUnhealthy,
  ScheduledActions

  static AutoScalingProcessType parse(String value) {
    values().find { it.name().equalsIgnoreCase(value) }
  }

  static Set<AutoScalingProcessType> getDisableProcesses() {
    EnumSet.of(Launch, Terminate, AddToLoadBalancer)
  }
}
