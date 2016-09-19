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

import com.netflix.spinnaker.fiat.config.UnrestrictedResourceConfig;
import com.netflix.spinnaker.fiat.permissions.PermissionsRepository;
import com.netflix.spinnaker.fiat.permissions.PermissionsResolver;
import com.netflix.spinnaker.fiat.providers.ProviderException;
import com.netflix.spinnaker.fiat.providers.ServiceAccountProvider;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.backoff.BackOffExecution;
import org.springframework.util.backoff.FixedBackOff;

import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Component
public class UserRolesSyncer {

  @Autowired
  @Setter
  private PermissionsRepository permissionsRepository;

  @Autowired
  @Setter
  private PermissionsResolver permissionsResolver;

  @Autowired(required = false)
  @Setter
  private ServiceAccountProvider serviceAccountProvider;

  @Value("${auth.userSync.retryIntervalMs:10000}")
  @Setter
  private long retryIntervalMs;

  // TODO(ttomsu): Acquire a lock in order to make this scale to multiple instances.
  @Scheduled(fixedDelayString = "${auth.userSync.intervalMs:600000}")
  public void sync() {
    syncAndReturn();
  }

  public long syncAndReturn() {
    updateServiceAccounts();
    return updateUserPermissions();
  }

  private void updateServiceAccounts() {
    AtomicInteger updateCount = new AtomicInteger(0);

    FixedBackOff backoff = new FixedBackOff();
    backoff.setInterval(retryIntervalMs);
    BackOffExecution backOffExec = backoff.start();

    while (true) {
      try {
        serviceAccountProvider
            .getAll()
            .forEach(serviceAccount -> permissionsResolver
                .resolve(serviceAccount.getName())
                .ifPresent(permission -> {
                  permissionsRepository.put(permission);
                  updateCount.incrementAndGet();
                })
            );
        log.info("Synced " + updateCount.get() + " service accounts");
        return;
      } catch (ProviderException pe) {
        long waitTime = backOffExec.nextBackOff();
        if (waitTime == BackOffExecution.STOP) {
          return;
        }
        log.warn("Service account resolution failed. Trying again in " + waitTime + "ms.", pe);

        try {
          Thread.sleep(waitTime);
        } catch (InterruptedException ignored) {
          return;
        }
      }
    }
  }

  private long updateUserPermissions() {
    val permissionMap = permissionsRepository.getAllById();

    if (permissionMap.remove(UnrestrictedResourceConfig.UNRESTRICTED_USERNAME) != null) {
      permissionsResolver.resolveUnrestrictedUser().ifPresent(permission -> {
        permissionsRepository.put(permission);
        log.info("Synced anonymous user role.");
      });
    }

    long count = permissionsResolver.resolve(permissionMap.keySet())
                                   .values()
                                   .stream()
                                   .map(permission -> permissionsRepository.put(permission))
                                   .count();
    log.info("Synced {} non-anonymous user roles.", count);
    return count;
  }
}
