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

import com.google.common.base.Strings;
import com.netflix.spinnaker.orca.api.pipeline.models.PipelineExecution;
import com.netflix.spinnaker.orca.api.pipeline.models.Trigger;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Deprecated
public class PipelineBuilder {
  private boolean includeAllowedAccounts;

  public PipelineBuilder(String application) {
    pipeline = PipelineExecutionImpl.newPipeline(application);
  }

  public PipelineBuilder withIncludeAllowedAccounts(boolean includeAllowedAccounts) {
    this.includeAllowedAccounts = includeAllowedAccounts;
    return this;
  }

  public PipelineBuilder withId(String id) {
    if (!Strings.isNullOrEmpty(id)) {
      pipeline.setId(id);
    }
    return this;
  }

  /**
   * If the rootId is present, use it. Otherwise assume this is a top-level pipeline, so the rootId
   * is the pipeline's own id. Yes, this logic only works if pipeline's id is correct. It gets a
   * dynamically generated id at construction time, but if there is a call to withId, it needs to
   * happen before withRootId.
   */
  public PipelineBuilder withRootId(String rootId) {
    pipeline.setRootId(Strings.isNullOrEmpty(rootId) ? pipeline.getId() : rootId);
    return this;
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

  public PipelineBuilder withInitialConfig(Map<String, Object> initialConfig) {
    ((PipelineExecutionImpl) pipeline).getInitialConfig().clear();
    if (initialConfig != null) {
      ((PipelineExecutionImpl) pipeline).getInitialConfig().putAll(initialConfig);
    }

    return this;
  }

  public PipelineBuilder withPipelineConfigId(String id) {
    pipeline.setPipelineConfigId(id);
    return this;
  }

  public PipelineBuilder withStage(String type, String name, Map<String, Object> context) {
    if (context.get("providerType") != null
        && !(Arrays.asList("aws", "titus")).contains(context.get("providerType"))) {
      type += "_" + context.get("providerType");
    }
    pipeline.getStages().add(new StageExecutionImpl(pipeline, type, name, context));
    return this;
  }

  public PipelineBuilder withStage(String type, String name) {
    return withStage(type, name, new HashMap<>());
  }

  public PipelineBuilder withStage(String type) {
    return withStage(type, type, new HashMap<>());
  }

  public PipelineBuilder withStages(List<Map<String, Object>> stages) {
    if (stages == null) {
      throw new IllegalArgumentException(
          "null stages in pipeline '"
              + pipeline.getName()
              + "' in application '"
              + pipeline.getApplication()
              + "'");
    }
    stages.forEach(
        it -> {
          String name = it.containsKey("name") ? it.remove("name").toString() : null;
          if (!it.containsKey("type")) {
            throw new IllegalArgumentException(
                "type missing from pipeline '"
                    + pipeline.getName()
                    + "' in application '"
                    + pipeline.getApplication()
                    + "', stage name: '"
                    + name
                    + "'");
          }
          String type = it.remove("type").toString();
          withStage(type, name != null ? name : type, it);
        });
    return this;
  }

  public PipelineExecution build() {
    pipeline.setBuildTime(System.currentTimeMillis());
    if (this.includeAllowedAccounts) {
      pipeline.setAuthentication(
          PipelineExecutionImpl.AuthenticationHelper.build()
              .orElse(new PipelineExecution.AuthenticationDetails()));
    } else {
      pipeline.setAuthentication(
          PipelineExecutionImpl.AuthenticationHelper.buildWithoutAccounts()
              .orElse(new PipelineExecution.AuthenticationDetails()));
    }

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

  public PipelineBuilder withMaxConcurrentExecutions(int maxConcurrentExecutions) {
    pipeline.setMaxConcurrentExecutions(maxConcurrentExecutions);
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

  public PipelineBuilder withSource(PipelineExecution.PipelineSource source) {
    pipeline.setSource(source);
    return this;
  }

  public PipelineBuilder withStartTimeExpiry(String startTimeExpiry) {
    if (startTimeExpiry != null) {
      pipeline.setStartTimeExpiry(Long.valueOf(startTimeExpiry));
    }
    return this;
  }

  public PipelineBuilder withSpelEvaluator(String spelEvaluatorVersion) {
    pipeline.setSpelEvaluator(spelEvaluatorVersion);

    return this;
  }

  public PipelineBuilder withTemplateVariables(Map<String, Object> templateVariables) {
    pipeline.setTemplateVariables(templateVariables);

    return this;
  }

  private final PipelineExecution pipeline;
}
