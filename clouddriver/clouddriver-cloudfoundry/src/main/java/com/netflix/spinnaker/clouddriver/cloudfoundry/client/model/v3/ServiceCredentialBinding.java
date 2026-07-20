/*
 * Copyright 2026 Harness, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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
 *
 */

package com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v3;

import java.util.Map;
import lombok.Data;

/**
 * Replaces the v2 {@code service_bindings} and {@code service_keys} resources, which CAPI v3
 * unifies into a single {@code service_credential_bindings} resource distinguished by {@link #type}
 * ({@code app} or {@code key}).
 */
@Data
public class ServiceCredentialBinding {
  private String guid;
  private String name;
  private String type;
  private Map<String, ToOneRelationship> relationships;
  private Map<String, Link> links;

  public String getAppGuid() {
    ToOneRelationship appRelationship = relationships == null ? null : relationships.get("app");
    return appRelationship == null || appRelationship.getData() == null
        ? null
        : appRelationship.getData().getGuid();
  }

  public String getServiceInstanceGuid() {
    ToOneRelationship serviceInstanceRelationship =
        relationships == null ? null : relationships.get("service_instance");
    return serviceInstanceRelationship == null || serviceInstanceRelationship.getData() == null
        ? null
        : serviceInstanceRelationship.getData().getGuid();
  }

  public boolean isKey() {
    return "key".equalsIgnoreCase(type);
  }

  public boolean isAppBinding() {
    return "app".equalsIgnoreCase(type);
  }
}
