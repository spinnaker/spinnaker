/*
 * Copyright 2016 Netflix, Inc.
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

package com.netflix.spinnaker.orca.clouddriver.tasks.cluster;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface PipelineClusterExtractor {

  String getStageType();

  void updateStageClusters(Map stage, List<Map> replacements);

  List<Map> extractClusters(Map stage);

  static Optional<PipelineClusterExtractor> getExtractor(
      Map stage, List<PipelineClusterExtractor> extractors) {
    return extractors.stream().filter(e -> e.getStageType().equals(stage.get("type"))).findFirst();
  }
}
