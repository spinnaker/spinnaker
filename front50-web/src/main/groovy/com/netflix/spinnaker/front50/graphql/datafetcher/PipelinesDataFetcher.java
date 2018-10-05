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
package com.netflix.spinnaker.front50.graphql.datafetcher;

import com.netflix.spinnaker.front50.model.pipeline.Pipeline;
import com.netflix.spinnaker.front50.model.pipeline.PipelineDAO;
import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class PipelinesDataFetcher implements DataFetcher<Collection<Pipeline>> {

  PipelineDAO pipelineDAO;

  @Autowired
  public PipelinesDataFetcher(PipelineDAO pipelineDAO) {
    this.pipelineDAO = pipelineDAO;
  }

  @Override
  public Collection<Pipeline> get(DataFetchingEnvironment environment) {
    Collection<Pipeline> pipelines = pipelineDAO.all();

    Object havingTriggerType = environment.getArgument("havingTriggerType");
    if (havingTriggerType != null) {
      return pipelines.stream()
        .filter(pipeline -> {
          List<Map<String, Object>> triggers = (List<Map<String, Object>>) pipeline.getOrDefault("triggers", Collections.EMPTY_LIST);
          return triggers.stream().anyMatch(trigger -> havingTriggerType.equals(trigger.get("type")));
        })
        .collect(Collectors.toList());
    }

    // TODO rz - mapping stuff like `extra` and other nested subtypes correctly.

    return pipelines;
  }
}
