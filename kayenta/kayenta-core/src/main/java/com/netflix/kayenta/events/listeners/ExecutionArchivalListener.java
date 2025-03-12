/*
 * Copyright (c) 2019 Netflix, inc.
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
package com.netflix.kayenta.events.listeners;

import com.netflix.kayenta.events.CanaryExecutionCompletedEvent;
import com.netflix.kayenta.security.AccountCredentials;
import com.netflix.kayenta.security.AccountCredentialsRepository;
import com.netflix.kayenta.storage.ObjectType;
import com.netflix.kayenta.storage.StorageServiceRepository;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@ConditionalOnProperty(
    name = "kayenta.default-archivers.enabled",
    havingValue = "true",
    matchIfMissing = true)
@Component
public class ExecutionArchivalListener {
  private static final Logger log = LoggerFactory.getLogger(ExecutionArchivalListener.class);

  private final AccountCredentialsRepository accountCredentialsRepository;
  private final StorageServiceRepository storageServiceRepository;

  public ExecutionArchivalListener(
      AccountCredentialsRepository accountCredentialsRepository,
      StorageServiceRepository storageServiceRepository) {
    this.accountCredentialsRepository = Objects.requireNonNull(accountCredentialsRepository);
    this.storageServiceRepository = Objects.requireNonNull(storageServiceRepository);
    log.info("Loaded ExecutionArchivalListener");
  }

  @EventListener
  public void onApplicationEvent(CanaryExecutionCompletedEvent event) {
    var response = event.getCanaryExecutionStatusResponse();
    var storageAccountName = response.getStorageAccountName();
    if (storageAccountName != null) {
      var resolvedStorageAccountName =
          accountCredentialsRepository
              .getRequiredOneBy(storageAccountName, AccountCredentials.Type.OBJECT_STORE)
              .getName();

      var storageService = storageServiceRepository.getRequiredOne(resolvedStorageAccountName);

      storageService.storeObject(
          resolvedStorageAccountName,
          ObjectType.CANARY_RESULT_ARCHIVE,
          response.getPipelineId(),
          response);
    }
  }
}
