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

/** Account properties for Proxmox cloud provider. */
@Data
@ConfigurationProperties("proxmox")
public class ProxmoxConfigurationProperties {
  private boolean enabled = false;

  private List<ProxmoxManagedAccount> accounts = new ArrayList<>();

  /** Managed account definition for Proxmox. */
  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  public static class ProxmoxManagedAccount implements AccessControlledAccountDefinition {

    private String name;
    private String server;
    private int port = 8006;
    private String scheme = "https";
    // OPTIONAL
    private String userName;
    private String password;
    // PREFERRED
    private String apiToken;
    // Whether to ignore certificates.
    private boolean insecure = false;
    // 30 seconds is the default but probably too often for most usages.
    private Long cacheIntervalSeconds = 60l;
    private String lastModified;
    private Map<Authorization, Set<String>> permissions = new HashMap<>();
  }
}
