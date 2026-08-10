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

import com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.RouteId;
import java.util.Map;
import javax.annotation.Nullable;
import lombok.Data;

/**
 * Request body for {@code POST /v3/routes}.
 *
 * @see <a href="https://v3-apidocs.cloudfoundry.org/index.html#create-a-route">CAPI v3 Create
 *     Route</a>
 */
@Data
public class CreateRoute {
  @Nullable private String host;
  @Nullable private String path;
  @Nullable private Integer port;
  private Map<String, ToOneRelationship> relationships;

  public static CreateRoute fromRouteId(RouteId routeId, String spaceGuid) {
    CreateRoute body = new CreateRoute();
    body.setHost(routeId.getHost());
    body.setPath(routeId.getPath());
    body.setPort(routeId.getPort());
    body.setRelationships(
        Map.of(
            "space", new ToOneRelationship(new Relationship(spaceGuid)),
            "domain", new ToOneRelationship(new Relationship(routeId.getDomainGuid()))));
    return body;
  }
}
