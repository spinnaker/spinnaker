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

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;
import lombok.Data;

/**
 * Request body for {@code POST /v3/service_instances} (type=managed) and {@code PATCH
 * /v3/service_instances/{guid}}, replacing the v2 {@code CreateServiceInstance} model. On update,
 * {@code type} and the {@code space} relationship are omitted (v3 rejects them on PATCH), so this
 * class is reused for both verbs and callers simply leave those fields unset for an update.
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CreateServiceInstance {
  @Nullable private String type;
  private String name;
  @Nullable private Set<String> tags;
  @Nullable private Map<String, Object> parameters;
  private Map<String, ToOneRelationship> relationships;

  public static CreateServiceInstance create(
      String name,
      String spaceGuid,
      String servicePlanGuid,
      Set<String> tags,
      Map<String, Object> parameters) {
    CreateServiceInstance body = new CreateServiceInstance();
    body.setType("managed");
    body.setName(name);
    body.setTags(tags);
    body.setParameters(parameters);
    body.setRelationships(
        Map.of(
            "space", new ToOneRelationship(new Relationship(spaceGuid)),
            "service_plan", new ToOneRelationship(new Relationship(servicePlanGuid))));
    return body;
  }

  public static CreateServiceInstance forUpdate(
      String name, String servicePlanGuid, Set<String> tags, Map<String, Object> parameters) {
    CreateServiceInstance body = new CreateServiceInstance();
    body.setName(name);
    body.setTags(tags);
    body.setParameters(parameters);
    if (servicePlanGuid != null) {
      body.setRelationships(
          Map.of("service_plan", new ToOneRelationship(new Relationship(servicePlanGuid))));
    }
    return body;
  }
}
