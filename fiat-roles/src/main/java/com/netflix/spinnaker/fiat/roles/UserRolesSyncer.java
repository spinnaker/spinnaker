/*
 * Copyright 2016 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

package com.netflix.spinnaker.fiat.roles;

import com.diffplug.common.base.Functions;
import com.netflix.spinnaker.cats.agent.RunnableAgent;
import com.netflix.spinnaker.fiat.config.ResourceProvidersHealthIndicator;
import com.netflix.spinnaker.fiat.config.UnrestrictedResourceConfig;
import com.netflix.spinnaker.fiat.model.UserPermission;
import com.netflix.spinnaker.fiat.model.resources.Role;
import com.netflix.spinnaker.fiat.model.resources.ServiceAccount;
import com.netflix.spinnaker.fiat.permissions.ExternalUser;
import com.netflix.spinnaker.fiat.permissions.PermissionResolutionException;
import com.netflix.spinnaker.fiat.permissions.PermissionsRepository;
import com.netflix.spinnaker.fiat.permissions.PermissionsResolver;
import com.netflix.spinnaker.fiat.providers.ProviderException;
import com.netflix.spinnaker.fiat.providers.ResourceProvider;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Status;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.backoff.BackOffExecution;
import org.springframework.util.backoff.FixedBackOff;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Component
@ConditionalOnExpression("${fiat.writeMode.enabled:true}")
public class UserRolesSyncer implements RunnableAgent {

  @Getter
  private final String agentType = "UserRoleSyncer";
  @Getter
  private final String providerName = "Fiat";

  @Autowired
  @Setter
  private PermissionsRepository permissionsRepository;

  @Autowired
  @Setter
  private PermissionsResolver permissionsResolver;

  @Autowired(required = false)
  @Setter
  private ResourceProvider<ServiceAccount> serviceAccountProvider;

  @Autowired
  @Setter
  private ResourceProvidersHealthIndicator healthIndicator;

  @Value("${fiat.writeMode.retryIntervalMs:10000}")
  @Setter
  private long retryIntervalMs;

  public void run() {
    syncAndReturn();
  }

  public long syncAndReturn() {
    FixedBackOff backoff = new FixedBackOff();
    backoff.setInterval(retryIntervalMs);
    BackOffExecution backOffExec = backoff.start();

    if (!isServerHealthy()) {
      log.warn("Server is currently UNHEALTHY. User permission role synchronization and " +
                   "resolution may not complete until this server becomes healthy again.");
    }

    while (true) {
      try {
        Map<String, UserPermission> combo = new HashMap<>();
        Map<String, UserPermission> temp;
        if (!(temp = getUserPermissions()).isEmpty()) {
          combo.putAll(temp);
        }
        if (!(temp = getServiceAccountsAsMap()).isEmpty()) {
          combo.putAll(temp);
        }

        return updateUserPermissions(combo);
      } catch (ProviderException|PermissionResolutionException ex) {
        Status status = healthIndicator.health().getStatus();
        long waitTime = backOffExec.nextBackOff();
        if (waitTime == BackOffExecution.STOP) {
          log.error("Unable to resolve service account permissions.", ex);
          return 0;
        }
        String message = new StringBuilder("User permission sync failed. ")
            .append("Server status is ")
            .append(status)
            .append(". Trying again in ")
            .append(waitTime)
            .append(" ms. Cause:")
            .append(ex.getMessage())
            .toString();
        if (log.isDebugEnabled()) {
          log.debug(message, ex);
        } else {
          log.warn(message);
        }

        try {
          Thread.sleep(waitTime);
        } catch (InterruptedException ignored) {
        }
      } finally {
        isServerHealthy();
      }
    }
  }

  private boolean isServerHealthy() {
    return healthIndicator.health().getStatus() == Status.UP;
  }

  private Map<String, UserPermission> getServiceAccountsAsMap() {
    return serviceAccountProvider
        .getAll()
        .stream()
        .map(ServiceAccount::toUserPermission)
        .collect(Collectors.toMap(UserPermission::getId, Functions.identity()));
  }

  private Map<String, UserPermission> getUserPermissions() {
    return permissionsRepository.getAllById();
  }

  public long updateUserPermissions(Map<String, UserPermission> permissionsById) {
    if (permissionsById.remove(UnrestrictedResourceConfig.UNRESTRICTED_USERNAME) != null) {
      permissionsRepository.put(permissionsResolver.resolveUnrestrictedUser());
      log.info("Synced anonymous user role.");
    }

    List<ExternalUser> extUsers = permissionsById
        .values()
        .stream()
        .map(permission -> new ExternalUser()
            .setId(permission.getId())
            .setExternalRoles(permission.getRoles()
                                        .stream()
                                        .filter(role -> role.getSource() == Role.Source.EXTERNAL)
                                        .collect(Collectors.toList())))
        .collect(Collectors.toList());

    long count = permissionsResolver.resolve(extUsers)
                                    .values()
                                    .stream()
                                    .map(permission -> permissionsRepository.put(permission))
                                    .count();
    log.info("Synced {} non-anonymous user roles.", count);
    return count;
  }
}
