/*
 * Copyright 2026 Spinnaker contributors.
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

package com.netflix.spinnaker.clouddriver.ecs.security;

import com.netflix.spinnaker.credentials.CredentialsRepository;
import java.util.Set;

/**
 * Resolves ECS credentials by exact name first, then by case-insensitive name.
 *
 * <p>Only used by the ecs-native deployment-status endpoint. Do not use it on shared/classic {@code
 * ecs} read paths: account names are otherwise matched exactly across clouddriver, and a
 * case-insensitive fallback there would silently change classic behavior.
 */
public final class EcsCredentialsResolver {

  private EcsCredentialsResolver() {}

  public static NetflixECSCredentials resolve(
      CredentialsRepository<NetflixECSCredentials> credentialsRepository, String account) {
    if (account == null) {
      return null;
    }
    NetflixECSCredentials exact = credentialsRepository.getOne(account);
    if (exact != null) {
      return exact;
    }
    Set<NetflixECSCredentials> all = credentialsRepository.getAll();
    if (all == null) {
      return null;
    }
    return all.stream()
        .filter(
            credentials -> credentials != null && account.equalsIgnoreCase(credentials.getName()))
        .findFirst()
        .orElse(null);
  }
}
