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
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Replaces the v2 {@code route_mappings} resource. In CAPI v3, an app is mapped to a route by
 * creating a "destination" on that route rather than a separate route-mapping resource.
 */
@NoArgsConstructor
@Data
public class Destination {
  private String guid;
  private App app;
  private Integer port;
  private String protocol;

  public Destination(App app) {
    this.app = app;
  }

  @NoArgsConstructor
  @AllArgsConstructor
  @Data
  public static class App {
    private String guid;
    private Process process;

    public App(String guid) {
      this.guid = guid;
    }
  }

  @NoArgsConstructor
  @AllArgsConstructor
  @Data
  public static class Process {
    private String type;
  }

  /** Request/response envelope for listing or inserting destinations on a route. */
  @NoArgsConstructor
  @AllArgsConstructor
  @Data
  public static class Page {
    private List<Destination> destinations;
  }
}
