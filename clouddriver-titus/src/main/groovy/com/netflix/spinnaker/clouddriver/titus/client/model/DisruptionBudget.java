/*
 * Copyright 2019 Netflix, Inc.
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

package com.netflix.spinnaker.clouddriver.titus.client.model;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.netflix.spinnaker.clouddriver.titus.client.model.disruption.*;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

@Data
public class DisruptionBudget implements Serializable {
  AvailabilityPercentageLimit availabilityPercentageLimit;
  UnhealthyTasksLimit unhealthyTasksLimit;
  RelocationLimit relocationLimit;
  RatePercentagePerHour ratePercentagePerHour;

  @JsonSerialize
  RateUnlimited rateUnlimited;
  List<TimeWindow> timeWindows;
  List<ContainerHealthProvider> containerHealthProviders;
  SelfManaged selfManaged;
  RatePerInterval ratePerInterval;
  RatePercentagePerInterval ratePercentagePerInterval;
}
