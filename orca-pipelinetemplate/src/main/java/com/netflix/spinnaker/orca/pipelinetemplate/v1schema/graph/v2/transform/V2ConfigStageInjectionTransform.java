/*
 * Copyright 2018 Google, Inc.
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

package com.netflix.spinnaker.orca.pipelinetemplate.v1schema.graph.v2.transform;

import com.netflix.spinnaker.orca.pipelinetemplate.exceptions.IllegalTemplateConfigurationException;
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.model.StageDefinition;
import com.netflix.spinnaker.orca.pipelinetemplate.v2schema.V2PipelineTemplateVisitor;
import com.netflix.spinnaker.orca.pipelinetemplate.v2schema.model.V2PipelineTemplate;
import com.netflix.spinnaker.orca.pipelinetemplate.v2schema.model.V2StageDefinition;
import com.netflix.spinnaker.orca.pipelinetemplate.v2schema.model.V2TemplateConfiguration;
import java.util.*;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class V2ConfigStageInjectionTransform implements V2PipelineTemplateVisitor {

  private final Logger log = LoggerFactory.getLogger(getClass());

  private V2TemplateConfiguration templateConfiguration;

  public V2ConfigStageInjectionTransform(V2TemplateConfiguration templateConfiguration) {
    this.templateConfiguration = templateConfiguration;
  }

  @Override
  public void visitPipelineTemplate(V2PipelineTemplate pipelineTemplate) {
    replaceStages(pipelineTemplate);
    injectStages(pipelineTemplate);
  }

  private void replaceStages(V2PipelineTemplate pipelineTemplate) {
    List<V2StageDefinition> templateStages = pipelineTemplate.getStages();
    for (V2StageDefinition confStage : templateConfiguration.getStages()) {
      for (int i = 0; i < templateStages.size(); i++) {
        if (templateStages.get(i).getRefId().equals(confStage.getRefId())) {
          templateStages.set(i, confStage);
          // NOTE: template.getStages() returns a copy of the stages, not the reference.
          // Hence we have to set the stages again.
          pipelineTemplate.setStages(templateStages);
          log.debug(
              "Template '{}' stage '{}' replaced by config {}",
              pipelineTemplate.getId(),
              confStage.getRefId(),
              templateConfiguration.getRuntimeId());
        }
      }
    }
  }

  private void injectStages(V2PipelineTemplate pipelineTemplate) {
    // Create initial graph via dependsOn. We need to include stages that the configuration defines
    // with dependsOn as well
    List<V2StageDefinition> initialStages = pipelineTemplate.getStages();

    initialStages.addAll(
        templateConfiguration.getStages().stream()
            .filter(s -> !s.getRequisiteStageRefIds().isEmpty())
            .collect(Collectors.toList()));

    pipelineTemplate.setStages(createGraph(initialStages));

    // Handle stage injections.
    List<V2StageDefinition> templateInjectStages =
        injectStages(pipelineTemplate.getStages(), pipelineTemplate.getStages());
    List<V2StageDefinition> configInjectStages =
        injectStages(templateConfiguration.getStages(), templateInjectStages);
    pipelineTemplate.setStages(configInjectStages);
  }

  private enum Status {
    VISITING,
    VISITED
  }

  private static void dfs(
      String stageId,
      Set<V2StageDefinition> result,
      Map<String, Status> state,
      Map<String, V2StageDefinition> graph,
      Map<String, Integer> outOrder) {

    state.put(stageId, Status.VISITING);
    V2StageDefinition stage = graph.get(stageId);

    if (stage == null) {
      state.put(stageId, Status.VISITED);
      return;
    }

    for (String n : stage.getRequisiteStageRefIds()) {
      Status status = state.get(n);
      if (status == Status.VISITING) {
        throw new IllegalTemplateConfigurationException(
            String.format("Cycle detected in graph (discovered on stage: %s)", stageId));
      }

      if (status == Status.VISITED) {
        continue;
      }

      dfs(n, result, state, graph, outOrder);
    }

    result.add(stage);
    state.put(stage.getRefId(), Status.VISITED);
    stage
        .getRequisiteStageRefIds()
        .forEach(
            s -> {
              int order = outOrder.getOrDefault(s, 0);
              outOrder.put(s, order + 1);
            });
  }

  private static List<V2StageDefinition> createGraph(List<V2StageDefinition> stages) {
    Set<V2StageDefinition> sorted = new LinkedHashSet<>();
    Map<String, Status> state = new HashMap<>();

    Map<String, V2StageDefinition> graph =
        stages.stream().collect(Collectors.toMap(V2StageDefinition::getRefId, i -> i));

    graph.forEach((k, v) -> dfs(k, sorted, state, graph, new HashMap<>()));

    return new ArrayList<>(sorted);
  }

  private static List<V2StageDefinition> injectStages(
      List<V2StageDefinition> stages, List<V2StageDefinition> templateStages) {
    // Using a stream here can cause a ConcurrentModificationException.
    for (V2StageDefinition s : new ArrayList<>(stages)) {
      if (s.getInject() == null || !s.getInject().hasAny()) {
        continue;
      }

      // De-dupe any stages that are defined by templates that have inject rules.
      templateStages.removeIf(stage -> stage.getRefId().equals(s.getRefId()));

      StageDefinition.InjectionRule rule = s.getInject();

      if (rule.getFirst() != null && rule.getFirst()) {
        injectFirst(s, templateStages);
        continue;
      }

      if (rule.getLast() != null && rule.getLast()) {
        injectLast(s, templateStages);
        continue;
      }

      if (rule.getBefore() != null) {
        injectBefore(s, rule.getBefore(), templateStages);
        continue;
      }

      if (rule.getAfter() != null) {
        injectAfter(s, rule.getAfter(), templateStages);
        continue;
      }

      throw new IllegalTemplateConfigurationException(
          String.format("stage did not have any valid injections defined (id: %s)", s.getRefId()));
    }
    return templateStages;
  }

  private static void injectFirst(V2StageDefinition stage, List<V2StageDefinition> allStages) {
    if (!allStages.isEmpty()) {
      allStages.forEach(
          s -> {
            if (s.getRequisiteStageRefIds().isEmpty())
              s.getRequisiteStageRefIds().add(stage.getRefId());
          });
      allStages.add(0, stage);
    } else {
      allStages.add(stage);
    }
  }

  private static void injectLast(V2StageDefinition stage, List<V2StageDefinition> allStages) {
    Map<String, Integer> outOrder = new HashMap<>();
    Set<V2StageDefinition> sorted = new LinkedHashSet<>();
    Map<String, Status> state = new HashMap<>();

    Map<String, V2StageDefinition> graph =
        allStages.stream().collect(Collectors.toMap(V2StageDefinition::getRefId, i -> i));

    // leaf nodes are stages with outOrder 0
    graph.keySet().forEach(k -> outOrder.put(k, 0));
    graph.forEach((k, v) -> dfs(k, sorted, state, graph, outOrder));
    sorted.stream()
        .filter(i -> outOrder.get(i.getRefId()) == 0)
        .forEach(i -> stage.getRequisiteStageRefIds().add(i.getRefId()));

    allStages.add(stage);
    graph.put(stage.getRefId(), stage);
    ensureNoCyclesInDAG(allStages, graph);
  }

  private static void injectBefore(
      V2StageDefinition stage, List<String> targetIds, List<V2StageDefinition> allStages) {
    Set<String> targetEdges = new LinkedHashSet<>();
    SortedSet<Integer> targetIndexes = new TreeSet<>();
    for (String targetId : targetIds) {
      V2StageDefinition target = getInjectionTarget(stage.getRefId(), targetId, allStages);
      targetEdges.addAll(target.getRequisiteStageRefIds());

      target.getRequisiteStageRefIds().clear();
      target.getRequisiteStageRefIds().add(stage.getRefId());
      targetIndexes.add(allStages.indexOf(target));
    }

    stage.getRequisiteStageRefIds().addAll(targetEdges);
    allStages.add(targetIndexes.last(), stage);

    Map<String, V2StageDefinition> graph =
        allStages.stream().collect(Collectors.toMap(V2StageDefinition::getRefId, i -> i));

    ensureNoCyclesInDAG(allStages, graph);
  }

  private static void ensureNoCyclesInDAG(
      List<V2StageDefinition> allStages, Map<String, V2StageDefinition> graph) {
    Map<String, Status> state = new HashMap<>();
    allStages.stream()
        .collect(Collectors.toMap(V2StageDefinition::getRefId, i -> i))
        .forEach((k, v) -> dfs(k, new LinkedHashSet<>(), state, graph, new HashMap<>()));
  }

  private static void injectAfter(
      V2StageDefinition stage, List<String> targetIds, List<V2StageDefinition> allStages) {
    Map<String, Integer> outOrder = new HashMap<>();
    Set<V2StageDefinition> sorted = new LinkedHashSet<>();
    Map<String, Status> state = new HashMap<>();

    Map<String, V2StageDefinition> graph =
        allStages.stream().collect(Collectors.toMap(V2StageDefinition::getRefId, i -> i));

    graph.forEach((k, v) -> dfs(k, sorted, state, graph, outOrder));

    SortedSet<Integer> targetIndexes = new TreeSet<>();
    for (String targetId : targetIds) {
      V2StageDefinition target = graph.get(targetId);
      if (target == null) {
        throw new IllegalTemplateConfigurationException(
            String.format(
                "could not inject '%s' stage: unknown target stage id '%s'",
                stage.getRefId(), targetId));
      }

      targetIndexes.add(allStages.indexOf(target));
      stage.getRequisiteStageRefIds().add(target.getRefId());

      // 1. find edges to target stage
      Set<V2StageDefinition> edges =
          graph.entrySet().stream()
              .filter(s -> s.getValue().getRequisiteStageRefIds().contains(targetId))
              .map(Map.Entry::getValue)
              .collect(Collectors.toSet());

      // 2. swap target with stage being inserted
      edges.stream()
          .filter(e -> e.getRequisiteStageRefIds().removeIf(targetId::equals))
          .forEach(e -> e.getRequisiteStageRefIds().add(stage.getRefId()));
    }

    // 3. add in the sorted graph
    allStages.add(targetIndexes.last() + 1, stage);
    graph.put(stage.getRefId(), stage);

    ensureNoCyclesInDAG(allStages, graph);
  }

  private static V2StageDefinition getInjectionTarget(
      String stageId, String targetId, List<V2StageDefinition> allStages) {
    return allStages.stream()
        .filter(pts -> pts.getRefId().equals(targetId))
        .findFirst()
        .orElseThrow(
            () ->
                new IllegalTemplateConfigurationException(
                    String.format(
                        "could not inject '%s' stage: unknown target stage id '%s'",
                        stageId, targetId)));
  }
}
