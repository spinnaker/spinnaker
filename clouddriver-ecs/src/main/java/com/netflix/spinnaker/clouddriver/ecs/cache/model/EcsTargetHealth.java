/*
 * Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * A copy of the License is located at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
 * CONDITIONS OF ANY KIND, either express or implied. See the License
 * for the specific language governing permissions and limitations under
 * the License.
 */

package com.netflix.spinnaker.clouddriver.ecs.cache.model;

import com.amazonaws.services.elasticloadbalancingv2.model.TargetHealthDescription;
import java.util.List;
import lombok.Data;

@Data
public class EcsTargetHealth {
  String targetGroupArn;
  List<TargetHealthDescription> targetHealthDescriptions;
}
