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
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.model.PartialDefinition;
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.model.PipelineTemplate;
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.model.StageDefinition;
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.model.StageDefinition.InjectionRule;
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.model.TemplateConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.SortedSet;
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
    expandStageLoops(pipelineTemplate);
    expandStagePartials(pipelineTemplate);
    replaceStages(pipelineTemplate);
    injectStages(pipelineTemplate);
    expandStagePartials(pipelineTemplate);
  }

  private void replaceStages(PipelineTemplate pipelineTemplate) {
    List<StageDefinition> templateStages = pipelineTemplate.getStages();
    for (StageDefinition confStage : templateConfiguration.getStages()) {
      for (int i = 0; i < templateStages.size(); i++) {
        if (templateStages.get(i).getId().equals(confStage.getId())) {
          templateStages.set(i, confStage);
          log.debug("Template '{}' stage '{}' replaced by config {}", pipelineTemplate.getId(), confStage.getId(), templateConfiguration.getRuntimeId());
        }
      }
    }
  }

  private void expandStageLoops(PipelineTemplate pipelineTemplate) {
    List<StageDefinition> addStages = new ArrayList<>();
    List<StageDefinition> templateStages = pipelineTemplate.getStages();

    // For each looped stage in the graph, inject its internal stage graph into the main template, then delete
    // the container stages.
    templateStages.stream().filter(StageDefinition::isLooping).forEach(loopingPlaceholder -> {
      List<StageDefinition> stages = loopingPlaceholder.getLoopedStages();

      createGraph(stages);

      Map<String, StageDefinition> graph = stages.stream().collect(Collectors.toMap(StageDefinition::getId, i -> i));
      Set<String> leafNodes = getLeafNodes(graph);

      stages.stream()
        .filter(s -> s.getDependsOn().isEmpty())
        .forEach(s -> {
          s.setDependsOn(loopingPlaceholder.getDependsOn());
          s.setRequisiteStageRefIds(loopingPlaceholder.getRequisiteStageRefIds());
        });

      templateStages.stream()
        .filter(s -> s.getDependsOn().contains(loopingPlaceholder.getId()))
        .forEach(s -> {
          s.getDependsOn().remove(loopingPlaceholder.getId());
          s.getDependsOn().addAll(leafNodes);
        });

      templateStages.stream()
        .filter(s -> s.getRequisiteStageRefIds().contains(loopingPlaceholder.getId()))
        .forEach(s -> {
          s.getRequisiteStageRefIds().remove(loopingPlaceholder.getId());
          s.getRequisiteStageRefIds().addAll(leafNodes);
        });

      addStages.addAll(stages);
    });

    templateStages.addAll(addStages);
    templateStages.removeIf(StageDefinition::isLooping);
  }

  private void expandStagePartials(PipelineTemplate pipelineTemplate) {
    List<StageDefinition> addStages = new ArrayList<>();
    List<StageDefinition> templateStages = pipelineTemplate.getStages();

    // For each "partial" type stage in the graph, inject its internal stage graph into the main template, then
    // delete the "partial" type stages. Root-level partial stages will inherit the placeholder's dependsOn values,
    // and stages that had dependsOn references to the placeholder will be reassigned to partial leaf nodes.
    templateStages.stream().filter(StageDefinition::isPartialType).forEach(partialPlaceholder -> {
      PartialDefinition partial = pipelineTemplate.getPartials().stream()
        .filter(p -> p.getRenderedPartials().containsKey(partialPlaceholder.getId()))
        .findFirst()
        .orElseThrow(() -> new IllegalTemplateConfigurationException(String.format("Could not find rendered partial: %s", partialPlaceholder.getId())));

      List<StageDefinition> stages = partial.getRenderedPartials().get(partialPlaceholder.getId());

      // Create a graph so we can find the leaf nodes
      createGraph(stages);

      // get leaf nodes of the partial
      Map<String, StageDefinition> graph = stages.stream().collect(Collectors.toMap(StageDefinition::getId, i -> i));
      Set<String> leafNodes = getLeafNodes(graph);

      // assign root nodes to placeholder's dependsOn value
      stages.stream()
        .filter(s -> s.getDependsOn().isEmpty())
        .forEach(s -> {
          s.setDependsOn(partialPlaceholder.getDependsOn());
          s.setRequisiteStageRefIds(partialPlaceholder.getRequisiteStageRefIds());
        });

      // And assign them as the dependsOn of the placeholder partial stage
      templateStages.stream()
        .filter(s -> s.getDependsOn().contains(partialPlaceholder.getId()))
        .forEach(s -> {
          s.getDependsOn().remove(partialPlaceholder.getId());
          s.getDependsOn().addAll(leafNodes);
        });

      templateStages.stream()
        .filter(s -> s.getRequisiteStageRefIds().contains(partialPlaceholder.getId()))
        .forEach(s -> {
          s.getRequisiteStageRefIds().remove(partialPlaceholder.getId());
          s.getRequisiteStageRefIds().addAll(leafNodes);
        });

      addStages.addAll(stages);
    });

    // Add partial stages into template stages list
    templateStages.addAll(addStages);

    // Remove placeholder partial stages
    templateStages.removeIf(StageDefinition::isPartialType);
  }

  private void injectStages(PipelineTemplate pipelineTemplate) {
    // Create initial graph via dependsOn. We need to include stages that the configuration defines with dependsOn as well
    List<StageDefinition> initialStages = pipelineTemplate.getStages();

    initialStages.addAll(templateConfiguration.getStages().stream()
      .filter(s -> !s.getDependsOn().isEmpty())
      .collect(Collectors.toList()));

    pipelineTemplate.setStages(
      createGraph(initialStages)
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

    if(stage == null) {
      state.put(stageId, Status.VISITED);
      return;
    }

    for (String n : stage.getRequisiteStageRefIds()) {
      Status status = state.get(n);
      if (status == Status.VISITING) {
        throw new IllegalTemplateConfigurationException(
          String.format("Cycle detected in graph (discovered on stage: %s)", stageId)
        );
      }

      if (status == Status.VISITED) {
        continue;
      }

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

    return new ArrayList<>(sorted);
  }

  private static void injectStages(List<StageDefinition> stages, List<StageDefinition> templateStages) {
    // Using a stream here can cause a ConcurrentModificationException.
    for (StageDefinition s : new ArrayList<>(stages)) {
      if (s.getInject() == null || !s.getInject().hasAny()) {
        continue;
      }

      // De-dupe any stages that are defined by templates that have inject rules.
      templateStages.removeIf(stage -> stage.getId().equals(s.getId()));

      InjectionRule rule = s.getInject();

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

      throw new IllegalTemplateConfigurationException(String.format("stage did not have any valid injections defined (id: %s)", s.getId()));
    }
  }

  private static void injectFirst(StageDefinition stage, List<StageDefinition> allStages) {
    if (!allStages.isEmpty()) {
      allStages.forEach(s -> {
        if(s.getRequisiteStageRefIds().isEmpty())
          s.getRequisiteStageRefIds().add(stage.getId());
      });
      allStages.add(0, stage);
    } else {
      allStages.add(stage);
    }
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

  private static void injectBefore(StageDefinition stage, List<String> targetIds, List<StageDefinition> allStages) {
    Set<String> targetEdges = new LinkedHashSet<>();
    SortedSet<Integer> targetIndexes = new TreeSet<>();
    for (String targetId : targetIds) {
      StageDefinition target = getInjectionTarget(stage.getId(), targetId, allStages);
      targetEdges.addAll(target.getRequisiteStageRefIds());

      target.getRequisiteStageRefIds().clear();
      target.getRequisiteStageRefIds().add(stage.getId());
      targetIndexes.add(allStages.indexOf(target));
    }

    stage.getRequisiteStageRefIds().addAll(targetEdges);
    allStages.add(targetIndexes.last(), stage);

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

  private static void injectAfter(StageDefinition stage, List<String> targetIds, List<StageDefinition> allStages) {
    Map<String, Integer> outOrder = new HashMap<>();
    Set<StageDefinition> sorted = new LinkedHashSet<>();
    Map<String, Status> state = new HashMap<>();

    Map<String, StageDefinition> graph = allStages
      .stream()
      .collect(
        Collectors.toMap(StageDefinition::getId, i -> i)
      );

    graph.forEach((k, v) -> dfs(k, sorted, state, graph, outOrder));

    SortedSet<Integer> targetIndexes = new TreeSet<>();
    for (String targetId: targetIds) {
      StageDefinition target = graph.get(targetId);
      if (target == null) {
        throw new IllegalTemplateConfigurationException(
          String.format("could not inject '%s' stage: unknown target stage id '%s'", stage.getId(), targetId)
        );
      }

      targetIndexes.add(allStages.indexOf(target));
      stage.getRequisiteStageRefIds().add(target.getId());

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
    }

    // 3. add in the sorted graph
    allStages.add(targetIndexes.last() + 1, stage);
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

  private static Set<String> getLeafNodes(Map<String, StageDefinition> graph) {
    Map<String, Integer> outOrder = new HashMap<>();
    Set<StageDefinition> sorted = new LinkedHashSet<>();
    Map<String, Status> state = new HashMap<>();

    // leaf nodes are stages with outOrder 0
    graph.keySet().forEach(k -> outOrder.put(k, 0));
    graph.forEach((k, v) -> dfs(k, sorted, state, graph, outOrder));

    return sorted
      .stream()
      .filter(i -> outOrder.get(i.getId()) == 0)
      .map(StageDefinition::getId)
      .collect(Collectors.toSet());
  }
}
