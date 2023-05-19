/*
 * Copyright 2023 Armory, Inc.
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
 */
package com.netflix.spinnaker.front50.migrations;

import com.netflix.spinnaker.front50.api.model.pipeline.Pipeline;
import com.netflix.spinnaker.front50.model.pipeline.PipelineDAO;
import java.util.*;
import java.util.function.Predicate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class RedBlackToBlueGreenK8sPipelinesMigration implements Migration {

  private final PipelineDAO pipelineDAO;

  public RedBlackToBlueGreenK8sPipelinesMigration(PipelineDAO pipelineDAO) {
    this.pipelineDAO = pipelineDAO;
  }

  @Override
  public boolean isValid() {
    return true;
  }

  @Override
  public void run() {
    log.info(
        "Starting the migration of K8s pipelines using RED/BLACK traffic management strategy to BLUE/GREEN");
    pipelineDAO.all().stream()
        .filter(RedBlackToBlueGreenK8sPipelinesMigration.pipelineWithRedBlackStrategyPredicate())
        .forEach(this::migrate);
  }

  private void migrate(Pipeline pipeline) {
    List<Map<String, Object>> stages = new ArrayList<>(pipeline.getStages());

    stages.stream()
        .filter(RedBlackToBlueGreenK8sPipelinesMigration::kubernetesProvider)
        .filter(RedBlackToBlueGreenK8sPipelinesMigration::deployManifestStage)
        .map(RedBlackToBlueGreenK8sPipelinesMigration::getTrafficManagement)
        .filter(Objects::nonNull)
        .filter(RedBlackToBlueGreenK8sPipelinesMigration::trafficManagementEnabled)
        .map(RedBlackToBlueGreenK8sPipelinesMigration::getTrafficManagementOptions)
        .forEach(
            trafficManagementOptions -> {
              Object strategy = trafficManagementOptions.get("strategy");
              if ("redblack".equals(strategy)) {
                trafficManagementOptions.put("strategy", "bluegreen");
              }
            });

    this.pipelineDAO.update(pipeline.getId(), pipeline);
    log.info(
        "Migrated pipeline (application: {}, pipelineId: {}, name: {})",
        pipeline.getApplication(),
        pipeline.getId(),
        pipeline.getName());
  }

  private static Predicate<Pipeline> pipelineWithRedBlackStrategyPredicate() {
    return pipeline ->
        Optional.ofNullable(pipeline.getStages()).orElseGet(ArrayList::new).stream()
            .filter(RedBlackToBlueGreenK8sPipelinesMigration::kubernetesProvider)
            .filter(RedBlackToBlueGreenK8sPipelinesMigration::deployManifestStage)
            .map(RedBlackToBlueGreenK8sPipelinesMigration::getTrafficManagement)
            .filter(Objects::nonNull)
            .filter(RedBlackToBlueGreenK8sPipelinesMigration::trafficManagementEnabled)
            .map(RedBlackToBlueGreenK8sPipelinesMigration::getTrafficManagementOptions)
            .anyMatch(RedBlackToBlueGreenK8sPipelinesMigration::redBlackStrategy);
  }

  private static Map<String, Object> getTrafficManagementOptions(
      Map<String, Object> trafficManagement) {
    return (Map<String, Object>) trafficManagement.get("options");
  }

  private static boolean trafficManagementEnabled(Map<String, Object> trafficManagement) {
    return Boolean.TRUE.equals(trafficManagement.get("enabled"));
  }

  private static Map<String, Object> getTrafficManagement(Map<String, Object> stage) {
    return (Map<String, Object>) stage.get("trafficManagement");
  }

  private static boolean kubernetesProvider(Map<String, Object> stage) {
    return "kubernetes".equals(stage.get("cloudProvider"));
  }

  private static boolean deployManifestStage(Map<String, Object> stage) {
    return "deployManifest".equals(stage.get("type"));
  }

  private static boolean redBlackStrategy(Map<String, Object> stage) {
    return "redblack".equals(stage.get("strategy"));
  }
}
