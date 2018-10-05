/*
 * Copyright 2016 Google, Inc.
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

package com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.support

import com.netflix.spinnaker.orca.clouddriver.utils.ClusterLockHelper
import com.netflix.spinnaker.orca.clouddriver.utils.MonikerHelper
import com.netflix.spinnaker.orca.clouddriver.utils.TrafficGuard
import com.netflix.spinnaker.orca.locks.LockingConfigurationProperties
import com.netflix.spinnaker.orca.pipeline.AcquireLockStage
import com.netflix.spinnaker.orca.pipeline.ReleaseLockStage
import groovy.transform.stc.ClosureParams
import groovy.transform.stc.SimpleType

import javax.annotation.Nonnull
import com.netflix.spinnaker.orca.kato.pipeline.Nameable
import com.netflix.spinnaker.orca.pipeline.StageDefinitionBuilder
import com.netflix.spinnaker.orca.pipeline.TaskNode
import com.netflix.spinnaker.orca.pipeline.graph.StageGraphBuilder
import com.netflix.spinnaker.orca.pipeline.model.Stage
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import static com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.support.TargetServerGroup.isDynamicallyBound

/**
 * Stages extending this class will resolve targets then generate synthetic before
 * stages (of the same type) to operate on each target.
 */
@Slf4j
abstract class TargetServerGroupLinearStageSupport implements StageDefinitionBuilder, Nameable {

  @Autowired TargetServerGroupResolver resolver
  @Autowired TrafficGuard trafficGuard
  @Autowired LockingConfigurationProperties lockingConfigurationProperties

  /**
   * Override to supply tasks that individual target stages will run. The top level
   * stage will never run any tasks.
   */
  protected void taskGraphInternal(Stage stage, TaskNode.Builder builder) {}

  /**
   * Override to supply before stages for individual target stages operating on a
   * static target.
   */
  protected void preStatic(Map<String, Object> descriptor, StageGraphBuilder graph) {
  }

  /**
   * Override to supply after stages for individual target stages operating on a
   * static target.
   */
  protected void postStatic(Map<String, Object> descriptor, StageGraphBuilder graph) {
  }

  /**
   * Override to supply before stages for individual target stages operating on a
   * dynamic target.
   */
  protected void preDynamic(Map<String, Object> context, StageGraphBuilder graph) {
  }

  /**
   * Override to supply after stages for individual target stages operating on a
   * dynamic target.
   */
  protected void postDynamic(Map<String, Object> context, StageGraphBuilder graph) {
  }

  @Override
  String getName() {
    return type
  }

  protected String getServerGroupName(Map<String, Object> context) {
    return context.serverGroupName ?: context.asgName
  }

  public Map<String, Object> removeServerGroupName(Map<String, Object> context) {
    def copyOfContext = new HashMap(context)

    copyOfContext.remove("serverGroupName")
    copyOfContext.remove("asgName")

    return copyOfContext
  }

  @Override
  final void taskGraph(Stage stage, TaskNode.Builder builder) {
    if (!isTopLevel(stage)) {
      // Tasks are only run by individual target stages
      taskGraphInternal(stage, builder)
    }
  }

  @Override
  final void beforeStages(
    @Nonnull Stage parent,
    @Nonnull StageGraphBuilder graph
  ) {
    if (isTopLevel(parent)) {
      // the top level stage should resolve targets and create synthetic stages to
      // deal with each one
      composeTargets(parent, graph)
    } else if (isTopLevel(parent.parent)) {
      // a non top level stage operates on a single target and may have its own
      // synthetic stages
      withGuard(parent) { moniker, account, location ->
        graph.add {
          it.type = AcquireLockStage.PIPELINE_TYPE
          it.name = 'acquireLock'
          it.context = [lock: [lockName: ClusterLockHelper.clusterLockName(moniker, account, location)]]
        }
      }

      if (isDynamicallyBound(parent)) {
        preDynamic(parent.context, graph)
      } else {
        preStatic(parent.context, graph)
      }
    }
  }

  void withGuard(Stage parent,
                 @ClosureParams(
                   value = SimpleType,
                   options = [
                   'com.netflix.spinnaker.moniker.Moniker',
                   'java.lang.String',
                   'com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.support.Location'
                 ]) Closure handleGuard) {
    if (lockingConfigurationProperties.isEnabled()) {
      def param = TargetServerGroup.Params.fromStage(parent)
      def moniker = param.moniker ?: MonikerHelper.friggaToMoniker(param.cluster ?: param.serverGroupName)
      def location = param.locations[0]
      if (trafficGuard.hasDisableLock(moniker, param.credentials, location)) {
        handleGuard.call(moniker, param.credentials, location)
      }
    }
  }

  @Override
  final void afterStages(
    @Nonnull Stage parent,
    @Nonnull StageGraphBuilder graph
  ) {
    if (isTopLevel(parent)) {
      // the top level stage has no after stages
    } else if (isTopLevel(parent.parent)) {
      // a non top level stage operates on a single target and may have its own
      // synthetic stages
      if (isDynamicallyBound(parent)) {
        postDynamic(parent.context, graph)
      } else {
        postStatic(parent.context, graph)
      }

      withGuard(parent) { moniker, account, location ->
        graph.append {
          it.type = ReleaseLockStage.PIPELINE_TYPE
          it.name = 'releaseLock'
          it.context = [lock: [lockName: ClusterLockHelper.clusterLockName(moniker, account, location)]]
        }
      }
    }
  }

  void composeTargets(Stage parent, StageGraphBuilder graph) {
    parent.resolveStrategyParams()
    def params = TargetServerGroup.Params.fromStage(parent)
    if (isDynamicallyBound(parent)) {
      composeDynamicTargets(parent, params, graph)
    } else {
      composeStaticTargets(parent, params, graph)
    }
  }

  private boolean isTopLevel(Stage stage) {
    return stage.parentStageId == null
  }

  private void composeStaticTargets(Stage stage, TargetServerGroup.Params params, StageGraphBuilder graph) {
    def targets = resolver.resolveByParams(params)
    def descriptionList = buildStaticTargetDescriptions(stage, targets)

    descriptionList.each { Map<String, Object> description ->
      graph.append {
        it.type = type
        it.name = name
        it.context = description
      }
    }
  }

  private void composeDynamicTargets(Stage stage, TargetServerGroup.Params params, StageGraphBuilder graph) {
    // Scrub the context of any preset location.
    stage.context.with {
      remove("zone")
      remove("zones")
      remove("region")
      remove("regions")
    }

    def singularLocationType = params.locations[0].singularType()
    def pluralLocationType = params.locations[0].pluralType()

    graph.add {
      it.type = DetermineTargetServerGroupStage.PIPELINE_CONFIG_TYPE
      it.name = DetermineTargetServerGroupStage.PIPELINE_CONFIG_TYPE
      it.context.putAll(stage.context)
      it.context[pluralLocationType] = params.locations.collect { it.value }
    }

    params.locations.each { Location location ->
      graph.append {
        it.type = type
        it.name = name
        it.context.putAll(stage.context)
        it.context[singularLocationType] = location.value
        it.context["targetLocation"] = [type: location.type.name(), value: location.value]
      }
    }
  }

  protected List<Map<String, Object>> buildStaticTargetDescriptions(Stage stage, List<TargetServerGroup> targets) {
    List<Map<String, Object>> descriptions = []
    for (target in targets) {
      def description = new HashMap(stage.context)
      description.asgName = target.name
      description.serverGroupName = target.name

      def location = target.getLocation()
      if (location.type == Location.Type.ZONE) {
        description.zone = location.value
      } else if (location.type == Location.Type.REGION) {
        description.region = location.value
      }
      description.deployServerGroupsRegion = target.region
      description.targetLocation = [type: location.type.name(), value: location.value]

      descriptions << description
    }
    descriptions
  }
}
