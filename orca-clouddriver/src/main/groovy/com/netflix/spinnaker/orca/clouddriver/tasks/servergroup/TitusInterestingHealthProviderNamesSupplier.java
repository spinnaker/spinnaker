/*
 * Copyright 2017 Netflix, Inc.
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

package com.netflix.spinnaker.orca.clouddriver.tasks.servergroup;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.orca.clouddriver.OortService;
import com.netflix.spinnaker.orca.kato.pipeline.support.SourceResolver;
import com.netflix.spinnaker.orca.kato.pipeline.support.StageData;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import retrofit.client.Response;

@Component
public class TitusInterestingHealthProviderNamesSupplier implements InterestingHealthProviderNamesSupplier {

  private final Logger log = LoggerFactory.getLogger(getClass());

  private static final String TITUS = "titus";
  private static final String INTERESTING_HEALTH_PROVIDER_NAMES = "interestingHealthProviderNames";
  private static final List<String> SUPPORTED_STAGES = Arrays.asList("cloneservergroup", "enableservergroup");

  private final OortService oortService;
  private final ObjectMapper objectMapper;
  private final SourceResolver sourceResolver;

  @Autowired
  public TitusInterestingHealthProviderNamesSupplier(OortService oortService,
                                                     SourceResolver sourceResolver,
                                                     ObjectMapper objectMapper) {
    this.oortService = oortService;
    this.sourceResolver = sourceResolver;
    this.objectMapper = objectMapper;
  }

  @Override
  public boolean supports(String cloudProvider, Stage stage) {
    if (!cloudProvider.equals(TITUS)) {
      return false;
    }

    return SUPPORTED_STAGES.contains(stage.getType().toLowerCase());
  }

  @Override
  public List<String> process(String cloudProvider, Stage stage) {
    try {
      StageData stageData = (StageData) stage.mapTo(StageData.class);
      Optional<StageData.Source> sourceData = Optional.ofNullable(sourceResolver.getSource(stage));
      if (sourceData.isPresent()) {
        StageData.Source source = sourceData.get();
        String serverGroupName =  source.getServerGroupName() != null ? source.getServerGroupName() : source.getAsgName();

        Response response = oortService.getServerGroupFromCluster(
          stageData.getApplication(),
          source.getAccount(),
          stageData.getCluster(),
          serverGroupName,
          source.getRegion(),
          cloudProvider
        );

        Map serverGroup = objectMapper.readValue(response.getBody().in(), Map.class);
        Map titusServerGroupLabels = (Map) serverGroup.get("labels");

        if (titusServerGroupLabels != null && titusServerGroupLabels.containsKey(INTERESTING_HEALTH_PROVIDER_NAMES)) {
          String healthProviderNames = (String) titusServerGroupLabels.get(INTERESTING_HEALTH_PROVIDER_NAMES);
          return Arrays.asList(healthProviderNames.split(","));
        }
      }
    } catch (Exception e) {
      log.error("Failed to process interesting health provider names for cloud provider {} on stage {} ", cloudProvider, stage, e);
    }

    return null;
  }
}
