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

import com.netflix.spinnaker.orca.pipelinetemplate.exceptions.IllegalTemplateConfigurationException;
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.PipelineTemplateVisitor;
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.model.PipelineTemplate;
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.model.StageDefinition;
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.model.StageDefinition.InjectionRule;
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.model.TemplateConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Replaces and injects configuration-defined stages into the pipeline template.
 *
 * If the template does not have any stages, all stages from the configuration
 * will be added. This can be useful in situations where users want to publish
 * pipeline traits, then wire them together through configuration-level stages.
 * This offers more flexibility in addition to inheritance relatively for free.
 */
public class ConfigStageInjectionTransform implements PipelineTemplateVisitor {

  private final Logger log = LoggerFactory.getLogger(getClass());

  private TemplateConfiguration templateConfiguration;

  public ConfigStageInjectionTransform(TemplateConfiguration templateConfiguration) {
    this.templateConfiguration = templateConfiguration;
  }

  @Override
  public void visitPipelineTemplate(PipelineTemplate pipelineTemplate) {
    addAll(pipelineTemplate);
    replaceStages(pipelineTemplate);
    injectStages(pipelineTemplate);
  }

  private void addAll(PipelineTemplate pipelineTemplate) {
    List<StageDefinition> templateStages = pipelineTemplate.getStages();
    if (templateStages.size() == 0) {
      templateStages.addAll(templateConfiguration.getStages());
    }
  }

  private void replaceStages(PipelineTemplate pipelineTemplate) {
    List<StageDefinition> templateStages = pipelineTemplate.getStages();
    for (StageDefinition confStage : templateConfiguration.getStages()) {
      for (int i = 0; i < templateStages.size(); i++) {
        if (templateStages.get(i).getId().equals(confStage.getId())) {
          templateStages.set(i, confStage);
          log.debug(String.format("Template '%s' stage '%s' replaced by config %s", pipelineTemplate.getId(), confStage.getId(), templateConfiguration.getRuntimeId()));
        }
      }
    }
  }

  private void injectStages(PipelineTemplate pipelineTemplate) {
    // Create initial graph via dependsOn.
    pipelineTemplate.setStages(
      createGraph(pipelineTemplate.getStages())
    );

    // Handle stage injections.
    injectStages(pipelineTemplate.getStages(), pipelineTemplate.getStages());
    injectStages(templateConfiguration.getStages(), pipelineTemplate.getStages());
  }

  private enum Status {
    VISITING,
    VISITED
  }

  private static void dfs(String stageId,
                          Set<StageDefinition> result,
                          Map<String, Status> state,
                          Map<String, StageDefinition> graph,
                          Map<String, Integer> outOrder) {

    state.put(stageId, Status.VISITING);
    StageDefinition stage = graph.get(stageId);

    for (String n : stage.getRequisiteStageRefIds()) {
      Status status = state.get(n);
      if (status == Status.VISITING) throw new IllegalTemplateConfigurationException(String.format("Cycle detected in graph (discovered on stage: %s)", stageId));
      if (status == Status.VISITED) continue;
      dfs(n, result, state, graph, outOrder);
    }

    result.add(stage);
    state.put(stage.getId(), Status.VISITED);
    stage.getRequisiteStageRefIds().forEach( s -> {
      int order = outOrder.getOrDefault(s, 0);
      outOrder.put(s, order + 1);
    });
  }

  private static List<StageDefinition> createGraph(List<StageDefinition> stages) {
    Set<StageDefinition> sorted = new LinkedHashSet<>();
    Map<String, Status> state = new HashMap<>();

    stages.forEach(s -> s.setRequisiteStageRefIds(s.getDependsOn()));

    Map<String, StageDefinition> graph = stages
      .stream()
      .collect(
        Collectors.toMap(StageDefinition::getId, i -> i)
      );

    graph.forEach((k, v) -> dfs(k, sorted, state, graph, new HashMap<>()));

    return sorted
      .stream()
      .collect(Collectors.toList());
  }

  private static void injectStages(List<StageDefinition> stages, List<StageDefinition> templateStages) {
    stages.stream()
      .filter(s -> s.getInject() != null)
      .forEach(s -> {
        InjectionRule rule = s.getInject();

        if (rule.getFirst() != null && rule.getFirst()) {
          injectFirst(s, templateStages);
          return;
        }

        if (rule.getLast() != null && rule.getLast()) {
          injectLast(s, templateStages);
          return;
        }

        if (rule.getBefore() != null) {
          injectBefore(s, rule.getBefore(), templateStages);
          return;
        }

        if (rule.getAfter() != null) {
          injectAfter(s, rule.getAfter(), templateStages);
          return;
        }

        throw new IllegalTemplateConfigurationException(String.format("stage did not have any valid injections defined (id: %s)", s.getId()));
      });
  }

  private static void injectFirst(StageDefinition stage, List<StageDefinition> allStages) {
    allStages.get(0).getRequisiteStageRefIds().add(stage.getId());
    allStages.add(0, stage);
  }

  private static void injectLast(StageDefinition stage, List<StageDefinition> allStages) {
    Map<String, Integer> outOrder = new HashMap<>();
    Set<StageDefinition> sorted = new LinkedHashSet<>();
    Map<String, Status> state = new HashMap<>();

    Map<String, StageDefinition> graph = allStages
      .stream()
      .collect(
        Collectors.toMap(StageDefinition::getId, i -> i)
      );

    // leaf nodes are stages with outOrder 0
    graph.keySet().forEach(k -> outOrder.put(k, 0));
    graph.forEach((k, v) -> dfs(k, sorted, state, graph, outOrder));
    sorted
      .stream()
      .filter(i -> outOrder.get(i.getId()) == 0)
      .forEach(i -> stage.getRequisiteStageRefIds().add(i.getId()));

    allStages.add(stage);
    graph.put(stage.getId(), stage);
    ensureNoCyclesInDAG(allStages, graph);
  }

  private static void injectBefore(StageDefinition stage, String targetId, List<StageDefinition> allStages) {
    StageDefinition target = getInjectionTarget(stage.getId(), targetId, allStages);
    int index = allStages.indexOf(target);

    stage.getRequisiteStageRefIds().addAll(target.getRequisiteStageRefIds());
    target.getRequisiteStageRefIds().clear();
    target.setRequisiteStageRefIds(Collections.singletonList(stage.getId()));
    allStages.add(index + 1, stage);

    Map<String, StageDefinition> graph = allStages
      .stream()
      .collect(
        Collectors.toMap(StageDefinition::getId, i -> i)
      );

    ensureNoCyclesInDAG(allStages, graph);
  }

  private static void ensureNoCyclesInDAG(List<StageDefinition> allStages, Map<String, StageDefinition> graph) {
    Map<String, Status> state = new HashMap<>();
    allStages
      .stream()
      .collect(
        Collectors.toMap(StageDefinition::getId, i -> i)
      ).forEach((k, v) -> dfs(k, new LinkedHashSet<>(), state, graph, new HashMap<>()));
  }

  private static void injectAfter(StageDefinition stage, String targetId, List<StageDefinition> allStages) {
    Map<String, Integer> outOrder = new HashMap<>();
    Set<StageDefinition> sorted = new LinkedHashSet<>();
    Map<String, Status> state = new HashMap<>();

    Map<String, StageDefinition> graph = allStages
      .stream()
      .collect(
        Collectors.toMap(StageDefinition::getId, i -> i)
      );

    graph.forEach((k, v) -> dfs(k, sorted, state, graph, outOrder));

    StageDefinition target = graph.get(targetId);
    if (target == null) {
      throw new IllegalTemplateConfigurationException(String.format("could not inject '%s' stage: unknown target stage id '%s'", stage.getId(), targetId));
    }

    stage.getRequisiteStageRefIds().add(target.getId());
    int index = allStages.indexOf(target);

    // 1. find edges to target stage
    Set<StageDefinition> edges = graph.entrySet()
      .stream()
      .filter(s -> s.getValue().getRequisiteStageRefIds().contains(targetId))
      .map(Map.Entry::getValue)
      .collect(Collectors.toSet());

    // 2. swap target with stage being inserted
    edges
      .stream()
      .filter(e -> e.getRequisiteStageRefIds().removeIf(targetId::equals))
      .forEach(e -> e.getRequisiteStageRefIds().add(stage.getId()));

    // 3. add in the sorted graph
    allStages.add(index + 1, stage);
    graph.put(stage.getId(), stage);

    ensureNoCyclesInDAG(allStages, graph);
  }

  private static StageDefinition getInjectionTarget(String stageId, String targetId, List<StageDefinition> allStages) {
    return allStages
      .stream()
      .filter(pts -> pts.getId().equals(targetId))
      .findFirst()
      .orElseThrow(() -> new IllegalTemplateConfigurationException(
        String.format("could not inject '%s' stage: unknown target stage id '%s'", stageId, targetId)
      ));
  }
}
