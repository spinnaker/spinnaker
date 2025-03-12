/*
 * Copyright 2017 Netflix, Inc.
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

package com.netflix.spinnaker.clouddriver.aws.lifecycle;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public class NotificationMessage {
  @JsonProperty("LifecycleActionToken")
  String lifecycleActionToken;

  @JsonProperty("AccountID")
  Integer accountId;

  @JsonProperty("Description")
  String description;

  @JsonProperty("StatusMessage")
  String statusMessage;

  @JsonProperty("Details")
  Map<String, Object> details;

  @JsonProperty("RequestID")
  Integer requestId;

  @JsonProperty("AutoScalingGroupName")
  String autoScalingGroupName;

  @JsonProperty("AutoScalingGroupARN")
  String autoScalingGroupARN;

  @JsonProperty("EC2InstanceID")
  String ec2InstanceId;

  @JsonProperty("Event")
  String event;
}
