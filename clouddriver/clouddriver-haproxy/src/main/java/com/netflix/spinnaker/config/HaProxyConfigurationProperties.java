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
package com.netflix.spinnaker.config;

import com.netflix.spinnaker.clouddriver.security.AccessControlledAccountDefinition;
import com.netflix.spinnaker.fiat.model.Authorization;
import java.util.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Account properties for the HAProxy cloud provider. */
@Data
@ConfigurationProperties("haproxy")
public class HaProxyConfigurationProperties {
  private boolean enabled = false;

  private List<HaProxyManagedAccount> accounts = new ArrayList<>();

  /**
   * Managed account definition for HAProxy. Each account points at a single HAProxy Data Plane API
   * endpoint (v3).
   */
  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  public static class HaProxyManagedAccount implements AccessControlledAccountDefinition {

    private String name;
    private String server;
    // Data Plane API defaults.
    private int port = 5555;
    private String scheme = "http";
    // Data Plane API userlist credentials (basic auth).
    private String userName;
    private String password;
    // Whether to ignore certificates when scheme is https.
    private boolean insecure = false;
    // 30 seconds is the default but probably too often for most usages.
    private Long cacheIntervalSeconds = 60L;

    /**
     * Logical region label for this HAProxy endpoint; HAProxy itself has no region concept, but
     * Spinnaker's load balancer and security group models are region-scoped.
     */
    private String region = "default";

    /**
     * Optional name of the Proxmox account whose instances sit behind this HAProxy. Used to
     * correlate backend servers to instances for health reporting.
     */
    private String proxmoxAccount;

    private String lastModified;
    private Map<Authorization, Set<String>> permissions = new HashMap<>();
  }
}
