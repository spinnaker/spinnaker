/*
 * Copyright 2018 Netflix, Inc.
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

package com.netflix.spinnaker.clouddriver.aws.lifecycle

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("aws.lifecycle-subscribers.launch-failure")
class LaunchFailureConfigurationProperties {
  String accountName
  String topicARN
  String queueARN

  int maxMessagesPerCycle = 1000
  int visibilityTimeout = 60
  int waitTimeSeconds = 5

  LaunchFailureConfigurationProperties() {
    // default constructor
  }

  LaunchFailureConfigurationProperties(String accountName,
                                       String topicARN,
                                       String queueARN,
                                       int maxMessagesPerCycle,
                                       int visibilityTimeout,
                                       int waitTimeSeconds) {
    this.accountName = accountName
    this.topicARN = topicARN
    this.queueARN = queueARN
    this.maxMessagesPerCycle = maxMessagesPerCycle
    this.visibilityTimeout = visibilityTimeout
    this.waitTimeSeconds = waitTimeSeconds
  }
}
