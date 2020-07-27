/*
 * Copyright 2020 Schibsted ASA.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 *
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.front50.migrations;

import com.netflix.spinnaker.front50.config.annotations.ConditionalOnAnyProviderExceptRedisIsEnabled;
import com.netflix.spinnaker.front50.model.pipeline.PipelineDAO;
import com.netflix.spinnaker.front50.model.serviceaccount.ServiceAccount;
import com.netflix.spinnaker.front50.model.serviceaccount.ServiceAccountDAO;
import java.time.LocalDate;
import java.time.Month;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@ConditionalOnAnyProviderExceptRedisIsEnabled
public class DeleteDanglingServiceAccountsMigration implements Migration {

  // Only valid until December 31, 2020
  private static final LocalDate VALID_UNTIL = LocalDate.of(2020, Month.DECEMBER, 31);

  private static final String SERVICE_ACCOUNT_SUFFIX = "@managed-service-account";
  private static final String RUN_AS_USER = "runAsUser";

  private final PipelineDAO pipelineDAO;
  private final ServiceAccountDAO serviceAccountDAO;

  @Autowired
  public DeleteDanglingServiceAccountsMigration(
      PipelineDAO pipelineDAO, ServiceAccountDAO serviceAccountDAO) {
    this.pipelineDAO = pipelineDAO;
    this.serviceAccountDAO = serviceAccountDAO;
  }

  @Override
  public boolean isValid() {
    return LocalDate.now().isBefore(VALID_UNTIL);
  }

  @Override
  public void run() {
    log.info(
        "Starting deletion of dangling service accounts ({})", this.getClass().getSimpleName());

    Set<String> serviceAccountsToKeep =
        pipelineDAO.all().parallelStream()
            .flatMap(
                pipeline ->
                    pipeline.getTriggers().stream()
                        .map(trigger -> (String) trigger.get(RUN_AS_USER))
                        .filter(isManagedServiceAccount())
                        .distinct())
            .collect(Collectors.toSet());

    serviceAccountDAO.all().parallelStream()
        .map(ServiceAccount::getName)
        .filter(isManagedServiceAccount())
        .filter(serviceAccount -> !serviceAccountsToKeep.contains(serviceAccount))
        .peek(
            serviceAccount ->
                log.info(
                    "Deleting managed service account '{}' because it is not in use",
                    serviceAccount))
        .forEach(serviceAccountDAO::delete);

    log.info("Finished deletion of dangling service accounts ");
  }

  private Predicate<String> isManagedServiceAccount() {
    return name -> name != null && name.endsWith(SERVICE_ACCOUNT_SUFFIX);
  }
}
