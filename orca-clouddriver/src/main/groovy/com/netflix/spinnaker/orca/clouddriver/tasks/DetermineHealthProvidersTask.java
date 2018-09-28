/*
 * Copyright 2016 Netflix, Inc.
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

package com.netflix.spinnaker.orca.clouddriver.tasks;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import com.netflix.frigga.Names;
import com.netflix.spinnaker.moniker.Moniker;
import com.netflix.spinnaker.orca.ExecutionStatus;
import com.netflix.spinnaker.orca.RetryableTask;
import com.netflix.spinnaker.orca.TaskResult;
import com.netflix.spinnaker.orca.clouddriver.tasks.servergroup.InterestingHealthProviderNamesSupplier;
import com.netflix.spinnaker.orca.clouddriver.tasks.servergroup.ServerGroupCreator;
import com.netflix.spinnaker.orca.clouddriver.utils.CloudProviderAware;
import com.netflix.spinnaker.orca.clouddriver.utils.MonikerHelper;
import com.netflix.spinnaker.orca.front50.Front50Service;
import com.netflix.spinnaker.orca.front50.model.Application;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import java.util.List;
import java.util.HashMap;

@Component
public class DetermineHealthProvidersTask implements RetryableTask, CloudProviderAware {
  private static final Logger log = LoggerFactory.getLogger(DetermineHealthProvidersTask.class);

  private final Front50Service front50Service;

  private final Map<String, String> healthProviderNamesByPlatform;
  private final Collection<InterestingHealthProviderNamesSupplier> interestingHealthProviderNamesSuppliers;

  @Autowired
  public DetermineHealthProvidersTask(Optional<Front50Service> front50Service,
                                      Collection<InterestingHealthProviderNamesSupplier> interestingHealthProviderNamesSuppliers,
                                      Collection<ServerGroupCreator> serverGroupCreators) {
    this.front50Service = front50Service.orElse(null);
    this.interestingHealthProviderNamesSuppliers = interestingHealthProviderNamesSuppliers;
    this.healthProviderNamesByPlatform = serverGroupCreators
      .stream()
      .filter(serverGroupCreator ->  serverGroupCreator.getHealthProviderName().isPresent())
      .collect(
        Collectors.toMap(
          ServerGroupCreator::getCloudProvider,
          serverGroupCreator -> serverGroupCreator.getHealthProviderName().orElse(null)
        )
      );
  }

  @Override
  public TaskResult execute(Stage stage) {
    Optional<InterestingHealthProviderNamesSupplier> healthProviderNamesSupplierOptional = interestingHealthProviderNamesSuppliers
      .stream()
      .filter(supplier -> supplier.supports(getCloudProvider(stage), stage))
      .findFirst();

    if (healthProviderNamesSupplierOptional.isPresent()) {
      List<String> interestingHealthProviderNames = healthProviderNamesSupplierOptional.get().process(getCloudProvider(stage), stage);
      Map<String, List<String>> results = new HashMap<>();

      if (interestingHealthProviderNames != null) {
        // avoid a `null` value that may cause problems with ImmutableMap usage downstream
        results.put("interestingHealthProviderNames", interestingHealthProviderNames);
      }

      return new TaskResult(ExecutionStatus.SUCCEEDED, results);
    }

    if (stage.getContext().containsKey("interestingHealthProviderNames")) {
      // should not override any stage-specified health providers
      return new TaskResult(ExecutionStatus.SUCCEEDED);
    }

    String platformSpecificHealthProviderName = healthProviderNamesByPlatform.get(getCloudProvider(stage));
    if (platformSpecificHealthProviderName == null) {
      log.warn("Unable to determine platform health provider for unknown cloud provider '{}'", getCloudProvider(stage));
      return new TaskResult(ExecutionStatus.SUCCEEDED);
    }

    try {
      String applicationName = (String) stage.getContext().get("application");
      Moniker moniker = MonikerHelper.monikerFromStage(stage);
      if (applicationName == null && moniker != null && moniker.getApp() != null) {
        applicationName = moniker.getApp();
      } else if (applicationName == null && stage.getContext().containsKey("serverGroupName")) {
        applicationName = Names.parseName((String) stage.getContext().get("serverGroupName")).getApp();
      } else if (applicationName == null && stage.getContext().containsKey("asgName")) {
        applicationName = Names.parseName((String) stage.getContext().get("asgName")).getApp();
      } else if (applicationName == null && stage.getContext().containsKey("cluster")) {
        applicationName = Names.parseName((String) stage.getContext().get("cluster")).getApp();
      }

      if (front50Service == null) {
        log.warn("Unable to determine health providers for an application without front50 enabled.");
        return new TaskResult(ExecutionStatus.SUCCEEDED);
      }

      Application application = front50Service.get(applicationName);

      if (application.platformHealthOnly == Boolean.TRUE && application.platformHealthOnlyShowOverride != Boolean.TRUE) {
        // if `platformHealthOnlyShowOverride` is true, the expectation is that `interestingHealthProviderNames` will
        // be included in the request if it's desired ... and that it should NOT be automatically added.
        return new TaskResult(ExecutionStatus.SUCCEEDED, Collections.singletonMap(
          "interestingHealthProviderNames", Collections.singletonList(platformSpecificHealthProviderName)
        ));
      }
    } catch (Exception e) {
      log.error("Unable to determine platform health provider (executionId: {}, stageId: {})", stage.getExecution().getId(), stage.getId(), e);
    }

    return new TaskResult(ExecutionStatus.SUCCEEDED);
  }

  @Override
  public long getBackoffPeriod() {
    return TimeUnit.SECONDS.toMillis(5);
  }

  @Override
  public long getTimeout() {
    return TimeUnit.MINUTES.toMillis(5);
  }
}
