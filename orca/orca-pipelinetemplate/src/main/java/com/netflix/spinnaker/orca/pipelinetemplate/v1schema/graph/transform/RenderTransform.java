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
package com.netflix.spinnaker.orca.pipelinetemplate.v1schema.graph.transform;

import com.google.common.base.Strings;
import com.netflix.spectator.api.Registry;
import com.netflix.spectator.api.Timer;
import com.netflix.spinnaker.orca.pipelinetemplate.exceptions.IllegalTemplateConfigurationException;
import com.netflix.spinnaker.orca.pipelinetemplate.exceptions.TemplateRenderException;
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.PipelineTemplateVisitor;
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.model.*;
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.render.RenderContext;
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.render.RenderUtil;
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.render.Renderer;
import com.netflix.spinnaker.orca.pipelinetemplate.validator.Errors;
import com.netflix.spinnaker.orca.pipelinetemplate.validator.Errors.Error;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class RenderTransform implements PipelineTemplateVisitor {

  TemplateConfiguration templateConfiguration;

  Renderer renderer;

  Registry registry;

  Map<String, Object> trigger;

  private final Timer renderTemplateTimer;

  public RenderTransform(
      TemplateConfiguration templateConfiguration,
      Renderer renderer,
      Registry registry,
      Map<String, Object> trigger) {
    this.templateConfiguration = templateConfiguration;
    this.renderer = renderer;
    this.registry = registry;
    this.trigger = trigger;
    this.renderTemplateTimer = registry.timer("server.renderPipelineTemplate");
  }

  @Override
  public void visitPipelineTemplate(PipelineTemplate pipelineTemplate) {
    long start = registry.clock().monotonicTime();
    render(pipelineTemplate);
    long end = registry.clock().monotonicTime();
    renderTemplateTimer.record(end - start, TimeUnit.NANOSECONDS);
  }

  private void render(PipelineTemplate template) {
    RenderContext context =
        RenderUtil.createDefaultRenderContext(template, templateConfiguration, trigger);

    expandLoops(template.getStages(), context);
    expandLoops(templateConfiguration.getStages(), context);

    // We only render the stages here, whereas modules will be rendered only if used within stages.
    renderStages(filterStages(template.getStages(), false), context, "template");
    renderStages(filterStages(templateConfiguration.getStages(), false), context, "configuration");

    // We don't care about configuration partials, they were already merged into the template at
    // this point
    renderPartials(template.getPartials(), filterStages(template.getStages(), true), context);
    renderPartials(
        template.getPartials(), filterStages(templateConfiguration.getStages(), true), context);

    renderConfigurations(
        template.getConfiguration().getParameters(), context, "template:configuration.parameters");
    renderConfigurations(
        templateConfiguration.getConfiguration().getParameters(),
        context,
        "configuration:configuration.parameters");

    renderConfigurations(
        template.getConfiguration().getTriggers(), context, "template:configuration.triggers");
    renderConfigurations(
        templateConfiguration.getConfiguration().getTriggers(),
        context,
        "configuration:configuration.triggers");

    renderConfigurations(
        template.getConfiguration().getNotifications(),
        context,
        "template:configuration.notifications");
    renderConfigurations(
        templateConfiguration.getConfiguration().getNotifications(),
        context,
        "configuration:configuration.notifications");
  }

  private void expandLoops(List<StageDefinition> stages, RenderContext context) {
    for (StageDefinition stage : loopingStages(stages)) {
      Object loopValue;
      try {
        loopValue = RenderUtil.deepRender(renderer, stage.getLoopWith(), context);
      } catch (TemplateRenderException e) {
        throw TemplateRenderException.fromError(
            new Error()
                .withMessage("Failed rendering loopWith value")
                .withLocation(context.getLocation()),
            e);
      }

      if (!(loopValue instanceof Collection)) {
        throw TemplateRenderException.fromError(
            new Error()
                .withMessage("Rendered loopWith value must be a list")
                .withCause("Received type " + loopValue.getClass().toString())
                .withLocation(context.getLocation()));
      }

      int index = 0;
      for (Object value : (Collection) loopValue) {
        StageDefinition expandedStage = wrapClone(stage);

        expandedStage.getLoopContext().put("value", value);
        expandedStage.setLoopWith(null);

        // If the stage ID is templated, allow access to the templateLoop context for rendering.
        if (stage.getId().contains("{{") && stage.getId().contains("}}")) {
          RenderContext loopedContext = context.copy();
          loopedContext.getVariables().put("templateLoop", expandedStage.getLoopContext());
          expandedStage.setId(renderer.render(stage.getId(), loopedContext));
        } else {
          expandedStage.setId(stage.getId() + index);
        }

        stage.getLoopedStages().add(expandedStage);

        index++;
      }
    }
  }

  private void renderStages(
      List<StageDefinition> stages, RenderContext context, String locationNamespace) {
    if (stages == null) {
      return;
    }

    for (StageDefinition stage : stages) {
      if (stage.isPartialType()) {
        // Partials are handled separately
        continue;
      }

      context.setLocation(String.format("%s:stages.%s", locationNamespace, stage.getId()));
      renderStage(stage, context, locationNamespace);
    }
  }

  @SuppressWarnings("unchecked")
  private void renderStage(StageDefinition stage, RenderContext context, String locationNamespace) {
    if (stage.isLooping()) {
      for (StageDefinition loopedStage : stage.getLoopedStages()) {
        RenderContext loopContext = context.copy();
        loopContext.getVariables().put("templateLoop", loopedStage.getLoopContext());
        renderStage(loopedStage, loopContext, locationNamespace);
      }
      // Looped stages don't get rendered themselves, only the expanded stages they store.
      return;
    }

    Object rendered;
    try {
      rendered = RenderUtil.deepRender(renderer, stage.getConfig(), context);
    } catch (TemplateRenderException e) {
      throw TemplateRenderException.fromError(
          new Error().withMessage("Failed rendering stage").withLocation(context.getLocation()), e);
    }

    if (!(rendered instanceof Map)) {
      throw new IllegalTemplateConfigurationException(
          new Errors.Error()
              .withMessage("A stage's rendered config must be a map")
              .withCause("Received type " + rendered.getClass().toString())
              .withLocation(context.getLocation()));
    }
    stage.setConfig((Map<String, Object>) rendered);

    stage.setName(
        renderStageProperty(
            stage.getName(),
            context,
            getStagePropertyLocation(locationNamespace, stage.getId(), "name")));
    stage.setComments(
        renderStageProperty(
            stage.getComments(),
            context,
            getStagePropertyLocation(locationNamespace, stage.getId(), "comments")));
    if (stage.getWhen() != null) {
      stage.setWhen(
          stage.getWhen().stream()
              .map(
                  w ->
                      renderStageProperty(
                          w,
                          context,
                          getStagePropertyLocation(locationNamespace, stage.getId(), "when")))
              .collect(Collectors.toList()));
    }
  }

  private String renderStageProperty(String input, RenderContext context, String location) {
    Object result;
    try {
      result = RenderUtil.deepRender(renderer, input, context);
    } catch (TemplateRenderException e) {
      throw TemplateRenderException.fromError(
          new Error().withMessage("Failed rendering stage property").withLocation(location), e);
    }
    return (result == null) ? null : result.toString();
  }

  private static String getStagePropertyLocation(
      String namespace, String stageId, String propertyName) {
    return String.format("%s:stages.%s.%s", namespace, stageId, propertyName);
  }

  private static List<StageDefinition> filterStages(
      List<StageDefinition> stages, boolean partialsOnly) {
    return stages.stream()
        .filter(s -> partialsOnly == s.isPartialType())
        .collect(Collectors.toList());
  }

  private static List<StageDefinition> loopingStages(List<StageDefinition> stages) {
    return stages.stream()
        .filter(it -> !Strings.isNullOrEmpty(it.getLoopWith()))
        .collect(Collectors.toList());
  }

  private void renderPartials(
      List<PartialDefinition> partials, List<StageDefinition> stages, RenderContext context) {
    for (StageDefinition stage : stages) {
      String partialId = stage.getPartialId();
      if (partialId == null) {
        throw TemplateRenderException.fromError(
            new Error()
                .withMessage("Stage with partial type has malformed ID format")
                .withCause(String.format("Expected 'partial:PARTIAL_ID' got '%s'", stage.getType()))
                .withLocation(String.format("template:stages.%s", stage.getId())));
      }
      PartialDefinition partial =
          partials.stream()
              .filter(p -> p.getId().equals(partialId))
              .findFirst()
              .orElseThrow(
                  () ->
                      TemplateRenderException.fromError(
                          new Error()
                              .withMessage("Unable to find Partial by ID: " + partialId)
                              .withLocation(String.format("template:stages.%s", stage.getId()))));

      if (stage.isLooping()) {
        for (StageDefinition loopedStage : stage.getLoopedStages()) {
          RenderContext loopedContext = context.copy();
          loopedContext.getVariables().put("templateLoop", loopedStage.getLoopContext());
          renderPartial(partial, loopedStage, loopedContext);
        }
      } else {
        renderPartial(partial, stage, context);
      }
    }
  }

  private void renderPartial(
      PartialDefinition partial, StageDefinition stage, RenderContext context) {
    RenderContext partialContext = context.copy();
    partialContext.setLocation(String.format("template:stages.%s", stage.getId()));
    renderStage(stage, partialContext, "template");
    partialContext.getVariables().putAll(stage.getConfigAsMap());
    partialContext.setLocation(String.format("partial:%s.%s", stage.getId(), partial.getId()));

    List<StageDefinition> renderedStages = new ArrayList<>();
    for (StageDefinition partialStage : partial.getStages()) {
      // TODO rz - add recursive partials support
      if (partialStage.isPartialType()) {
        throw TemplateRenderException.fromError(
            new Error()
                .withMessage("Recursive partials support is not currently implemented")
                .withLocation(String.format("partial:%s", partial.getId())));
      }

      StageDefinition renderedStage = wrapClone(partialStage);
      renderedStage.setPartialDefinitionContext(
          new StageDefinition.PartialDefinitionContext(partial, stage));
      renderedStage.setId(String.format("%s.%s", stage.getId(), renderedStage.getId()));
      renderedStage.setDependsOn(
          renderedStage.getDependsOn().stream()
              .map(d -> String.format("%s.%s", stage.getId(), d))
              .collect(Collectors.toSet()));

      renderStage(
          renderedStage,
          partialContext,
          String.format("partial:%s.%s", stage.getId(), partial.getId()));

      renderedStages.add(renderedStage);
    }
    partial.getRenderedPartials().put(stage.getId(), renderedStages);
  }

  private void renderConfigurations(
      List<NamedHashMap> configurations, RenderContext context, String location) {
    if (configurations == null) {
      return;
    }

    for (Map<String, Object> config : configurations) {
      for (Map.Entry<String, Object> pair : config.entrySet()) {
        try {
          pair.setValue(RenderUtil.deepRender(renderer, pair.getValue(), context));
        } catch (TemplateRenderException e) {
          throw TemplateRenderException.fromError(
              new Error()
                  .withMessage("Failed rendering configuration property")
                  .withLocation(location),
              e);
        }
      }
    }
  }

  private StageDefinition wrapClone(StageDefinition stage) {
    try {
      return (StageDefinition) stage.clone();
    } catch (CloneNotSupportedException e) {
      // This definitely should never happen. Yay checked exceptions.
      throw new TemplateRenderException("StageDefinition clone unsupported", e);
    }
  }
}
