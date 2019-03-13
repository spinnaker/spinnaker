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

import com.fasterxml.jackson.annotation.JsonView;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v2.ServiceInstance.Type;
import com.netflix.spinnaker.clouddriver.model.ServiceInstance;
import lombok.Builder;
import lombok.Value;

import java.util.Set;

/**
 * "Service" in this context refers to an Open Service Broker service.
 */
@Value
@Builder
@JsonDeserialize(builder = CloudFoundryServiceInstance.CloudFoundryServiceInstanceBuilder.class)
public class CloudFoundryServiceInstance implements ServiceInstance {
  @JsonView(Views.Cache.class)
  String serviceInstanceName;

  @JsonView(Views.Cache.class)
  String name;

  @JsonView(Views.Cache.class)
  String id;

  @JsonView(Views.Cache.class)
  String plan;

  @JsonView(Views.Cache.class)
  String planId;

  @JsonView(Views.Cache.class)
  String status;

  @JsonView(Views.Cache.class)
  Set<String> tags;

  @JsonView(Views.Cache.class)
  String type;
}
