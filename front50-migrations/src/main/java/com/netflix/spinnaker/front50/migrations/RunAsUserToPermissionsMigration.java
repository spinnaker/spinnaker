/*
 * Copyright 2019 Schibsted ASA.
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

import static net.logstash.logback.argument.StructuredArguments.value;

import com.netflix.spinnaker.front50.model.pipeline.Pipeline;
import com.netflix.spinnaker.front50.model.pipeline.PipelineDAO;
import com.netflix.spinnaker.front50.model.pipeline.Trigger;
import com.netflix.spinnaker.front50.model.serviceaccount.ServiceAccount;
import com.netflix.spinnaker.front50.model.serviceaccount.ServiceAccountDAO;
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
@ConditionalOnProperty("migrations.migrate-to-managed-service-accounts")
public class RunAsUserToPermissionsMigration implements Migration {

  private static final String SERVICE_ACCOUNT_SUFFIX = "@managed-service-account";
  private static final String RUN_AS_USER = "runAsUser";
  private static final String ROLES = "roles";

  private final PipelineDAO pipelineDAO;
  private final ServiceAccountDAO serviceAccountDAO;

  @Autowired
  public RunAsUserToPermissionsMigration(
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
        "Starting runAsUser to automatic service user migration ({})",
        this.getClass().getSimpleName());

    Map<String, ServiceAccount> serviceAccounts =
        serviceAccountDAO.all().stream()
            .collect(Collectors.toMap(ServiceAccount::getName, Function.identity()));

    pipelineDAO.all().parallelStream()
        .filter(p -> p.getTriggers().stream().anyMatch(this::hasManualServiceUser))
        .forEach(pipeline -> migrate(pipeline, serviceAccounts));

    log.info("Finished runAsUser to automatic service user migration");
  }

  @SuppressWarnings("unchecked")
  private void migrate(Pipeline pipeline, Map<String, ServiceAccount> serviceAccounts) {
    log.info(
        "Starting migration of pipeline '{}' (application: '{}', pipelineId: '{}')",
        value("pipelineName", pipeline.getName()),
        value("application", pipeline.getApplication()),
        value("pipelineId", pipeline.getId()));

    Set<String> newRoles = new HashSet<>();
    List<String> existingRoles = (List) pipeline.get(ROLES);
    if (existingRoles != null) {
      existingRoles.stream().map(String::toLowerCase).forEach(newRoles::add);
    }

    String serviceAccountName = generateSvcAcctName(pipeline);

    ServiceAccount automaticServiceAccount = new ServiceAccount();
    automaticServiceAccount.setName(serviceAccountName);

    Collection<Trigger> triggers = pipeline.getTriggers();

    triggers.forEach(
        trigger -> {
          String runAsUser = (String) trigger.get(RUN_AS_USER);
          if (runAsUser != null && !runAsUser.endsWith(SERVICE_ACCOUNT_SUFFIX)) {
            ServiceAccount manualServiceAccount = serviceAccounts.get(runAsUser);
            if (manualServiceAccount != null && !manualServiceAccount.getMemberOf().isEmpty()) {
              manualServiceAccount.getMemberOf().stream()
                  .map(String::toLowerCase) // Because roles in Spinnaker are always lowercase
                  .forEach(newRoles::add);
            }
          }
          log.info(
              "Replacing '{}' with automatic service user '{}' (application: '{}', pipelineName: '{}', "
                  + "pipelineId: '{}')",
              value("oldServiceUser", runAsUser),
              value("newServiceUser", serviceAccountName),
              value("application", pipeline.getApplication()),
              value("pipelineName", pipeline.getName()),
              value("pipelineId", pipeline.getId()));
          trigger.put(RUN_AS_USER, serviceAccountName);
        });

    log.info("Creating service user '{}' with roles {}", serviceAccountName, newRoles);
    automaticServiceAccount.getMemberOf().addAll(newRoles);
    pipeline.put(ROLES, new ArrayList<>(newRoles));
    pipeline.setTriggers(triggers);

    serviceAccountDAO.create(automaticServiceAccount.getId(), automaticServiceAccount);
    pipelineDAO.update(pipeline.getId(), pipeline);
  }

  private boolean hasManualServiceUser(Trigger trigger) {
    String runAsUser = (String) trigger.get(RUN_AS_USER);
    return runAsUser != null && !runAsUser.endsWith(SERVICE_ACCOUNT_SUFFIX);
  }

  private String generateSvcAcctName(Pipeline pipeline) {
    if (pipeline.containsKey("serviceAccount")) {
      return (String) pipeline.get("serviceAccount");
    }
    String pipelineName = pipeline.getId();
    return pipelineName.toLowerCase() + SERVICE_ACCOUNT_SUFFIX;
  }
}
