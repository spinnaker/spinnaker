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
import javax.annotation.Nullable;
import lombok.Data;

/**
 * v3 route resource object. Replaces the v2 {@code Resource<Route>} wrapper pattern — fields are
 * top-level rather than nested under {@code entity}/{@code metadata}.
 *
 * @see <a href="https://v3-apidocs.cloudfoundry.org/index.html#the-route-object">CAPI v3 Route</a>
 */
@Data
public class Route {
  private String guid;
  private String host;
  private String path;
  @Nullable private Integer port;
  private String protocol;
  private String url;
  @Nullable private List<Destination> destinations;
  private Map<String, ToOneRelationship> relationships;
  private Map<String, Link> links;

  public String getSpaceGuid() {
    ToOneRelationship spaceRel = relationships == null ? null : relationships.get("space");
    return spaceRel == null || spaceRel.getData() == null ? null : spaceRel.getData().getGuid();
  }

  public String getDomainGuid() {
    ToOneRelationship domainRel = relationships == null ? null : relationships.get("domain");
    return domainRel == null || domainRel.getData() == null ? null : domainRel.getData().getGuid();
  }
}
