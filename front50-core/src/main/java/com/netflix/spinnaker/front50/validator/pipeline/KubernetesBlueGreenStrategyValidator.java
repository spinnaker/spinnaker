/*
 * Copyright 2022 Armory, Inc.
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

package com.netflix.spinnaker.front50.validator.pipeline;

import com.netflix.spinnaker.front50.api.model.pipeline.Pipeline;
import com.netflix.spinnaker.front50.api.validator.PipelineValidator;
import com.netflix.spinnaker.front50.api.validator.ValidatorErrors;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class KubernetesBlueGreenStrategyValidator implements PipelineValidator {
  @Override
  public void validate(Pipeline pipeline, ValidatorErrors errors) {

    List<Map<String, Object>> stages = pipeline.getStages();

    if (stages == null || stages.isEmpty()) {
      return;
    }

    boolean redBlackStrategy =
        stages.stream()
            .filter(KubernetesBlueGreenStrategyValidator::kubernetesProvider)
            .filter(KubernetesBlueGreenStrategyValidator::deployManifestStage)
            .map(KubernetesBlueGreenStrategyValidator::getTrafficManagement)
            .filter(KubernetesBlueGreenStrategyValidator::trafficManagementEnabled)
            .map(KubernetesBlueGreenStrategyValidator::getTrafficManagementOptions)
            .anyMatch(KubernetesBlueGreenStrategyValidator::redBlackStrategy);

    if (redBlackStrategy) {
      log.warn(
          "Kubernetes traffic management redblack strategy is deprecated and will be removed soon. Please use bluegreen instead");
      // TODO uncomment the line below when we decide to fail the process. also update the test
      //      errors.reject("Kubernetes traffic management redblack strategy is deprecated and will
      // be removed soon. Please use bluegreen instead");
    }
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
