/*
 * Copyright 2017 Netflix, Inc.
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

package com.netflix.spinnaker.orca.clouddriver;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class FeaturesService {
  private final FeaturesRestService featuresRestService;

  @Autowired
  public FeaturesService(FeaturesRestService featuresRestService) {
    this.featuresRestService = featuresRestService;
  }

  public boolean isStageAvailable(String stageType) {
    return featuresRestService.getStages().stream().anyMatch(s -> s.enabled && stageType.equals(s.name));
  }

  public boolean areEntityTagsAvailable() {
    return isStageAvailable("upsertEntityTags");
  }
}
