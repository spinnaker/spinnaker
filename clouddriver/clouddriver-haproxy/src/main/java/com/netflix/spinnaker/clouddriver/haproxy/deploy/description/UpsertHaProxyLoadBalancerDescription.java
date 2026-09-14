/*
 * Copyright 2026 McIntosh.farm
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
 */
package com.netflix.spinnaker.clouddriver.haproxy.deploy.description;

import java.util.Map;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** Creates or updates an HAProxy frontend (with its binds) as a load balancer. */
@Data
@EqualsAndHashCode(callSuper = true)
public class UpsertHaProxyLoadBalancerDescription extends HaProxyBaseDescription {
  private String name;

  /** {@code http} (default) or {@code tcp}. */
  private String mode = "http";

  private String defaultBackend;

  /** Bind name mapped to its listen address/port. */
  private Map<String, BindSpec> binds;

  /** Data Plane metadata to attach (e.g. spinnaker-app) for moniker derivation. */
  private Map<String, Object> metadata;

  @Data
  public static class BindSpec {
    private String address;
    private Integer port;
    private Boolean ssl;
  }
}
