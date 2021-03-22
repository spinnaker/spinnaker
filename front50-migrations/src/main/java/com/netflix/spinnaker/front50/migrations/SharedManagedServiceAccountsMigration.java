/*
 * Copyright 2021 Netflix, Inc.
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

package com.netflix.spinnaker.front50.migrations;

import static net.logstash.logback.argument.StructuredArguments.value;

import com.google.common.hash.Hashing;
import com.netflix.spinnaker.front50.model.pipeline.Pipeline;
import com.netflix.spinnaker.front50.model.pipeline.PipelineDAO;
import com.netflix.spinnaker.front50.model.pipeline.Trigger;
import com.netflix.spinnaker.front50.model.serviceaccount.ServiceAccount;
import com.netflix.spinnaker.front50.model.serviceaccount.ServiceAccountDAO;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@ConditionalOnProperty("migrations.migrate-to-shared-managed-service-accounts")
public class SharedManagedServiceAccountsMigration implements Migration {
  private static final String SERVICE_ACCOUNT_SUFFIX = "@managed-service-account";
  private static final String SHARED_SERVICE_ACCOUNT_SUFFIX = "@shared-managed-service-account";
  private static final String RUN_AS_USER = "runAsUser";
  private static final String ROLES = "roles";

  private final ServiceAccountDAO serviceAccountDAO;
  private final PipelineDAO pipelineDAO;

  @Autowired
  public SharedManagedServiceAccountsMigration(
      PipelineDAO pipelineDAO, ServiceAccountDAO serviceAccountDAO) {
    this.pipelineDAO = pipelineDAO;
    this.serviceAccountDAO = serviceAccountDAO;
  }

  @Override
  public boolean isValid() {
    return true;
  }

  @Override
  public void run() {
    log.info(
        "Starting migration to shared managed service accounts ({})",
        this.getClass().getSimpleName());

    Map<String, ServiceAccount> serviceAccounts =
        serviceAccountDAO.all().stream()
            .collect(Collectors.toMap(ServiceAccount::getName, Function.identity()));

    pipelineDAO.all().parallelStream()
        .filter(
            p ->
                p.getTriggers().stream()
                    .anyMatch(
                        trigger -> hasManagedServiceAccountUser((String) trigger.get(RUN_AS_USER))))
        .forEach(pipeline -> migrate(pipeline, serviceAccounts));
  }

  private void migrate(Pipeline pipeline, Map<String, ServiceAccount> serviceAccounts) {
    log.info(
        "Starting migration of pipeline '{}' with id '{}' for application '{}'",
        value("pipelineName", pipeline.getName()),
        value("application", pipeline.getApplication()),
        value("pipelineId", pipeline.getId()));

    Set<String> newRoles = new HashSet<>();

    List<String> existingRoles = (List) pipeline.get(ROLES);
    if (existingRoles != null) {
      existingRoles.stream().map(String::toLowerCase).forEach(newRoles::add);
    }

    Collection<Trigger> triggers = pipeline.getTriggers();

    triggers.forEach(
        trigger -> {
          String runAsUser = (String) trigger.get(RUN_AS_USER);
          if (hasManagedServiceAccountUser(runAsUser)) {
            ServiceAccount managedServiceAccount = serviceAccounts.get(runAsUser);
            if (managedServiceAccount != null && !managedServiceAccount.getMemberOf().isEmpty()) {
              managedServiceAccount.getMemberOf().stream()
                  .map(String::toLowerCase)
                  .forEach(newRoles::add);
            }
          }
        });

    String sharedManagedServiceAccountName = generatedSharedManagedServiceAccountName(newRoles);

    ServiceAccount sharedManagedServiceAccount = new ServiceAccount();
    sharedManagedServiceAccount.setName(sharedManagedServiceAccountName);

    triggers.forEach(
        trigger -> {
          String runAsUser = (String) trigger.get(RUN_AS_USER);
          log.info(
              "Replacing '{}' with automatic service user '{}' (application: '{}', pipelineName: '{}', "
                  + "pipelineId: '{}')",
              value("oldServiceUser", runAsUser),
              value("newServiceUser", sharedManagedServiceAccountName),
              value("application", pipeline.getApplication()),
              value("pipelineName", pipeline.getName()),
              value("pipelineId", pipeline.getId()));
          trigger.put(RUN_AS_USER, sharedManagedServiceAccountName);
        });

    log.info("Creating service user '{}' wih roles {}", sharedManagedServiceAccountName, newRoles);
    sharedManagedServiceAccount.getMemberOf().addAll(newRoles);
    pipeline.put(ROLES, new ArrayList<>(newRoles));
    pipeline.setTriggers(triggers);

    serviceAccountDAO.create(sharedManagedServiceAccount.getId(), sharedManagedServiceAccount);
    pipelineDAO.update(pipeline.getId(), pipeline);
  }

  private String generatedSharedManagedServiceAccountName(Set<String> roles) {
    String roleString =
        roles.stream()
            .map(String::toLowerCase)
            .distinct()
            .sorted()
            .collect(Collectors.joining("\0"));
    return Hashing.sha256().hashString(roleString, StandardCharsets.UTF_8).toString()
        + SHARED_SERVICE_ACCOUNT_SUFFIX;
  }

  private boolean hasManagedServiceAccountUser(String runAsUser) {
    return runAsUser != null && runAsUser.endsWith(SERVICE_ACCOUNT_SUFFIX);
  }
}
