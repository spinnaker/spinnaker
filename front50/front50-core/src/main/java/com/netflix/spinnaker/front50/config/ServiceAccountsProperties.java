/*
 * Copyright 2026 DoorDash, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.front50.config;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Operators declare API-capable service accounts in application.yml. These are the only identities
 * that may be used when minting service-account API tokens. The initializer upserts them on every
 * startup so that the config remains the authoritative source of truth.
 *
 * <pre>
 * service-accounts:
 *   - name: deploy-bot
 *     memberOf:
 *       - deploy-team
 * </pre>
 */
@Data
@ConfigurationProperties
public class ServiceAccountsProperties {

  private List<ServiceAccountDefinition> serviceAccounts = new ArrayList<>();

  @Data
  public static class ServiceAccountDefinition {
    /** Service account name (must match the Fiat identity). */
    private String name;

    /**
     * Fiat roles this account should belong to. Config is authoritative — overwritten on startup.
     */
    private List<String> memberOf = new ArrayList<>();
  }
}
