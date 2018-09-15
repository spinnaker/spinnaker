/*
 * Copyright 2018 Pivotal, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.cloudfoundry.model;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.netflix.spinnaker.clouddriver.cloudfoundry.servicebroker.Service;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Value;

import java.util.Collection;

@Value
@EqualsAndHashCode(callSuper = false)
@Builder
@JsonDeserialize(builder = CloudFoundryService.CloudFoundryServiceBuilder.class)
public class CloudFoundryService extends CloudFoundryModel implements Service {
  String name;
  Collection<CloudFoundryServicePlan> servicePlans;
}
