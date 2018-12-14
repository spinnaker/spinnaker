/*
 * Copyright 2016 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

package com.netflix.spinnaker.orca.pipeline.model;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Deprecated
public class PipelineBuilder {
  public PipelineBuilder(String application) {
    pipeline = Execution.newPipeline(application);
  }

  public PipelineBuilder withTrigger(Trigger trigger) {
    if (trigger != null) {
      pipeline.setTrigger(trigger);
    }
    return this;
  }

  public PipelineBuilder withNotifications(List<Map<String, Object>> notifications) {
    pipeline.getNotifications().clear();
    if (notifications != null) {
      pipeline.getNotifications().addAll(notifications);
    }
    return this;
  }

  public PipelineBuilder withPipelineConfigId(String id) {
    pipeline.setPipelineConfigId(id);
    return this;
  }

  public PipelineBuilder withStage(String type, String name, Map<String, Object> context) {
    if (context.get("providerType") != null && !(Arrays.asList("aws", "titus")).contains(context.get("providerType"))) {
      type += "_" + context.get("providerType");
    }
    pipeline.getStages().add(new Stage(pipeline, type, name, context));
    return this;
  }

  public PipelineBuilder withStage(String type, String name) {
    return withStage(type, name, new HashMap<>());
  }

  public PipelineBuilder withStage(String type) {
    return withStage(type, type, new HashMap<>());
  }

  public PipelineBuilder withStages(List<Map<String, Object>> stages) {
    stages.forEach(it -> {
      String type = it.remove("type").toString();
      String name = it.containsKey("name")? it.remove("name").toString() : null;
      withStage(type, name != null ? name : type, it);
    });
    return this;
  }

  public Execution build() {
    pipeline.setBuildTime(System.currentTimeMillis());
    pipeline.setAuthentication(Execution.AuthenticationDetails.build().orElse(new Execution.AuthenticationDetails()));

    return pipeline;
  }

  public PipelineBuilder withName(String name) {
    pipeline.setName(name);
    return this;
  }

  public PipelineBuilder withLimitConcurrent(boolean concurrent) {
    pipeline.setLimitConcurrent(concurrent);
    return this;
  }

  public PipelineBuilder withKeepWaitingPipelines(boolean waiting) {
    pipeline.setKeepWaitingPipelines(waiting);
    return this;
  }

  public PipelineBuilder withOrigin(String origin) {
    pipeline.setOrigin(origin);
    return this;
  }

  public PipelineBuilder withSource(Execution.PipelineSource source) {
    pipeline.setSource(source);
    return this;
  }

  public PipelineBuilder withStartTimeExpiry(String startTimeExpiry) {
    if (startTimeExpiry != null) {
      pipeline.setStartTimeExpiry(Long.valueOf(startTimeExpiry));
    }
    return this;
  }

  private final Execution pipeline;
}
