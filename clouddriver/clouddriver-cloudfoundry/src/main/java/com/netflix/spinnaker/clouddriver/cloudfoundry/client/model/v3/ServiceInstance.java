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

import java.util.List;
import java.util.Map;
import lombok.Data;

@Data
public class ServiceInstance {
  private String guid;
  private String name;
  private String type;
  private List<String> tags;
  private String syslogDrainUrl;
  private String routeServiceUrl;
  private Map<String, Object> credentials;
  private LastOperation lastOperation;
  private Map<String, ToOneRelationship> relationships;
  private Map<String, Link> links;

  public boolean isUserProvided() {
    return "user-provided".equalsIgnoreCase(type);
  }

  public boolean isManaged() {
    return "managed".equalsIgnoreCase(type);
  }

  public String getServicePlanGuid() {
    return getRelationshipGuid("service_plan");
  }

  public String getSpaceGuid() {
    return getRelationshipGuid("space");
  }

  private String getRelationshipGuid(String key) {
    ToOneRelationship rel = relationships == null ? null : relationships.get(key);
    return rel == null || rel.getData() == null ? null : rel.getData().getGuid();
  }

  @Data
  public static class LastOperation {
    private String type;
    private String state;
    private String description;
  }
}
