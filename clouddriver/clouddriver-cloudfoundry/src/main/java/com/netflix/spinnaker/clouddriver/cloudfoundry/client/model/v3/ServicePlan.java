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
 * v3 {@code /v3/service_plans} resource, replacing the v2 {@code ServicePlan} model. The parent
 * service offering guid, which lived on a flat {@code serviceGuid} field in v2, now lives under
 * {@code relationships.service_offering.data.guid}.
 */
@Data
public class ServicePlan {
  private String guid;
  private String name;
  private Map<String, ToOneRelationship> relationships;

  public String getServiceOfferingGuid() {
    ToOneRelationship rel = relationships == null ? null : relationships.get("service_offering");
    return rel == null || rel.getData() == null ? null : rel.getData().getGuid();
  }
}
