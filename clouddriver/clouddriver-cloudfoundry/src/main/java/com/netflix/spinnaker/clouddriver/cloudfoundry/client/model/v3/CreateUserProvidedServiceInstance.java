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
 * Request body for {@code POST /v3/service_instances} (type=user-provided) and {@code PATCH
 * /v3/service_instances/{guid}}, replacing the v2 {@code CreateUserProvidedServiceInstance} model.
 * On update the {@code type} and {@code space} relationship are omitted.
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CreateUserProvidedServiceInstance {
  @Nullable private String type;
  private String name;
  @Nullable private String syslogDrainUrl;
  @Nullable private Set<String> tags;
  @Nullable private Map<String, Object> credentials;
  @Nullable private String routeServiceUrl;
  @Nullable private Map<String, ToOneRelationship> relationships;

  public static CreateUserProvidedServiceInstance create(
      String name,
      String spaceGuid,
      String syslogDrainUrl,
      Set<String> tags,
      Map<String, Object> credentials,
      String routeServiceUrl) {
    CreateUserProvidedServiceInstance body = new CreateUserProvidedServiceInstance();
    body.setType("user-provided");
    body.setName(name);
    body.setSyslogDrainUrl(syslogDrainUrl);
    body.setTags(tags);
    body.setCredentials(credentials);
    body.setRouteServiceUrl(routeServiceUrl);
    body.setRelationships(Map.of("space", new ToOneRelationship(new Relationship(spaceGuid))));
    return body;
  }

  public static CreateUserProvidedServiceInstance forUpdate(
      String name,
      String syslogDrainUrl,
      Set<String> tags,
      Map<String, Object> credentials,
      String routeServiceUrl) {
    CreateUserProvidedServiceInstance body = new CreateUserProvidedServiceInstance();
    body.setName(name);
    body.setSyslogDrainUrl(syslogDrainUrl);
    body.setTags(tags);
    body.setCredentials(credentials);
    body.setRouteServiceUrl(routeServiceUrl);
    return body;
  }
}
