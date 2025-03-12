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

package com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v2;

import static com.netflix.spinnaker.clouddriver.cloudfoundry.client.CloudFoundryClientUtils.convertToValidServiceBindingName;

import java.util.Map;
import lombok.Getter;

@Getter
public class CreateServiceBinding {
  private final String serviceInstanceGuid;
  private final String appGuid;
  private final String name;
  private Map<String, Object> parameters;

  public CreateServiceBinding(
      final String serviceInstanceGuid,
      final String appGuid,
      final String name,
      final Map<String, Object> parameters) {
    this.serviceInstanceGuid = serviceInstanceGuid;
    this.appGuid = appGuid;
    this.name = convertToValidServiceBindingName(name);
    this.parameters = parameters;
  }

  public CreateServiceBinding(
      final String serviceInstanceGuid, final String appGuid, final String name) {
    this.serviceInstanceGuid = serviceInstanceGuid;
    this.appGuid = appGuid;
    this.name = convertToValidServiceBindingName(name);
  }
}
