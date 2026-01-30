/*
 * Copyright 2018 Netflix, Inc.
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

package com.netflix.spinnaker.config;

import com.netflix.spectator.api.Id;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus;
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionType;
import com.netflix.spinnaker.orca.api.pipeline.models.PipelineExecution;
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository;
import io.reactivex.rxjava3.schedulers.Schedulers;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Scheduled;

@Configuration
@EnableConfigurationProperties(EnhancedMonitoringConfigurationProperties.class)
@ConditionalOnExpression(value = "${pollers.enhanced-monitoring.enabled:false}")
public class EnhancedMonitoringConfiguration {
  private final Logger log = LoggerFactory.getLogger(getClass());

  private final Registry registry;
  private final ExecutionRepository executionRepository;
  private final EnhancedMonitoringConfigurationProperties configuration;

  private final Map<String, AtomicLong> orchestrationCountPerApplication = new HashMap<>();

  @Autowired
  public EnhancedMonitoringConfiguration(
      Registry registry,
      ExecutionRepository executionRepository,
      EnhancedMonitoringConfigurationProperties configuration) {
    this.registry = registry;
    this.executionRepository = executionRepository;
    this.configuration = configuration;

    Id runningOrchestrationsId =
        registry
            .createId("executions.running")
            .withTag(
                "executionType",
                ExecutionType.ORCHESTRATION
                    .toString()); // similar to what MetricsTagHelper is doing

    for (String application : configuration.getApplications()) {
      Id applicationSpecificId = runningOrchestrationsId.withTag("application", application);
      orchestrationCountPerApplication.put(
          application, registry.gauge(applicationSpecificId, new AtomicLong(0)));
    }
  }

  @Scheduled(fixedDelayString = "${pollers.enhanced-monitoring.interval-ms:60000}")
  void refresh() {
    log.info("Refreshing Running Orchestration Counts ({})", orchestrationCountPerApplication);

    for (String application : configuration.getApplications()) {
      try {
        List<PipelineExecution> executions =
            executionRepository
                .retrieveOrchestrationsForApplication(
                    application,
                    new ExecutionRepository.ExecutionCriteria()
                        .setStatuses(ExecutionStatus.RUNNING))
                .subscribeOn(Schedulers.io())
                .toList()
                .blockingGet();
        orchestrationCountPerApplication.get(application).set(executions.size());
      } catch (Exception e) {
        log.error(
            "Unable to refresh running orchestration count (application: {})", application, e);
      }
    }

    log.info("Refreshed Running Orchestration Counts ({})", orchestrationCountPerApplication);
  }
}
