/*
 * Copyright 2015 Netflix, Inc.
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

package com.netflix.spinnaker.orca.pipeline.util;

import static com.netflix.spinnaker.orca.pipeline.expressions.PipelineExpressionEvaluator.ExpressionEvaluationVersion.V2;
import static com.netflix.spinnaker.orca.pipeline.model.Execution.ExecutionType.PIPELINE;
import static java.util.Collections.EMPTY_MAP;
import static java.util.Collections.emptyList;
import static org.apache.commons.lang3.StringUtils.isNotEmpty;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.orca.config.UserConfiguredUrlRestrictions;
import com.netflix.spinnaker.orca.jackson.OrcaObjectMapper;
import com.netflix.spinnaker.orca.pipeline.expressions.ExpressionEvaluationSummary;
import com.netflix.spinnaker.orca.pipeline.expressions.ExpressionEvaluator;
import com.netflix.spinnaker.orca.pipeline.expressions.PipelineExpressionEvaluator;
import com.netflix.spinnaker.orca.pipeline.expressions.functions.DeployedServerGroupsExpressionFunctionProvider;
import com.netflix.spinnaker.orca.pipeline.expressions.functions.ManifestLabelValueExpressionFunctionProvider;
import com.netflix.spinnaker.orca.pipeline.model.*;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Common methods for dealing with passing context parameters used by both Script and Jenkins stages
 */
public class ContextParameterProcessor {

  private final Logger log = LoggerFactory.getLogger(getClass());

  private static final ObjectMapper mapper = OrcaObjectMapper.getInstance();

  private ExpressionEvaluator expressionEvaluator;

  public ContextParameterProcessor() {
    this(
        new ContextFunctionConfiguration(
            new UserConfiguredUrlRestrictions.Builder().build(),
            Arrays.asList(
                new DeployedServerGroupsExpressionFunctionProvider(),
                new ManifestLabelValueExpressionFunctionProvider()),
            V2));
  }

  public ContextParameterProcessor(ContextFunctionConfiguration contextFunctionConfiguration) {
    expressionEvaluator = new PipelineExpressionEvaluator(contextFunctionConfiguration);
  }

  public Map<String, Object> process(
      Map<String, Object> source, Map<String, Object> context, boolean allowUnknownKeys) {
    ExpressionEvaluationSummary summary = new ExpressionEvaluationSummary();

    return process(source, context, allowUnknownKeys, summary);
  }

  public Map<String, Object> process(
      Map<String, Object> source,
      Map<String, Object> context,
      boolean allowUnknownKeys,
      ExpressionEvaluationSummary summary) {

    if (source == null) {
      return null;
    }

    if (source.isEmpty()) {
      return new HashMap<>();
    }

    Map<String, Object> result =
        expressionEvaluator.evaluate(source, precomputeValues(context), summary, allowUnknownKeys);

    if (summary.getTotalEvaluated() > 0 && context.containsKey("execution")) {
      log.info("Evaluated {}", summary);
    }

    if (summary.getFailureCount() > 0) {
      result.put("expressionEvaluationSummary", summary.getExpressionResult());
    }

    return result;
  }

  public Map<String, Object> buildExecutionContext(Stage stage, boolean includeStageContext) {
    Map<String, Object> augmentedContext = new HashMap<>();
    if (includeStageContext) {
      augmentedContext.putAll(stage.getContext());
    }
    if (stage.getExecution().getType() == PIPELINE) {
      augmentedContext.put(
          "trigger",
          mapper.convertValue(
              stage.getExecution().getTrigger(), new TypeReference<Map<String, Object>>() {}));
      augmentedContext.put("execution", stage.getExecution());
    }

    return augmentedContext;
  }

  public static boolean containsExpression(String value) {
    return isNotEmpty(value) && value.contains("${");
  }

  private Map<String, Object> precomputeValues(Map<String, Object> context) {
    Object rawTrigger = context.get("trigger");
    Trigger trigger;
    if (rawTrigger != null && !(rawTrigger instanceof Trigger)) {
      trigger = mapper.convertValue(rawTrigger, Trigger.class);
    } else {
      trigger = (Trigger) rawTrigger;
    }

    if (trigger != null && !trigger.getParameters().isEmpty()) {
      context.put("parameters", trigger.getParameters());
    } else {
      if (!context.containsKey("parameters")) {
        context.put("parameters", EMPTY_MAP);
      }
    }

    if (context.get("buildInfo") instanceof BuildInfo) {
      context.put(
          "scmInfo",
          Optional.ofNullable((BuildInfo) context.get("buildInfo"))
              .map(BuildInfo::getScm)
              .orElse(null));
    }

    if (context.get("scmInfo") == null && trigger instanceof JenkinsTrigger) {
      context.put(
          "scmInfo",
          Optional.ofNullable(((JenkinsTrigger) trigger).getBuildInfo())
              .map(BuildInfo::getScm)
              .orElse(emptyList()));
    }
    if (context.get("scmInfo") == null && trigger instanceof ConcourseTrigger) {
      context.put(
          "scmInfo",
          Optional.ofNullable(((ConcourseTrigger) trigger).getBuildInfo())
              .map(BuildInfo::getScm)
              .orElse(emptyList()));
    }
    if (context.get("scmInfo") != null && ((List) context.get("scmInfo")).size() >= 2) {
      List<SourceControl> scmInfos = (List<SourceControl>) context.get("scmInfo");
      SourceControl scmInfo =
          scmInfos.stream()
              .filter(it -> !"master".equals(it.getBranch()) && !"develop".equals(it.getBranch()))
              .findFirst()
              .orElseGet(() -> scmInfos.get(0));
      context.put("scmInfo", scmInfo);
    } else if (context.get("scmInfo") != null && !((List) context.get("scmInfo")).isEmpty()) {
      context.put("scmInfo", ((List) context.get("scmInfo")).get(0));
    } else {
      context.put("scmInfo", null);
    }

    return context;
  }
}
