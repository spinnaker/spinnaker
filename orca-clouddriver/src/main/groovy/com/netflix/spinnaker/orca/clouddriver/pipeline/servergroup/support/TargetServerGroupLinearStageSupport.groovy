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

import groovy.util.logging.Slf4j
import com.netflix.spinnaker.orca.kato.pipeline.Nameable
import com.netflix.spinnaker.orca.pipeline.StageDefinitionBuilder
import com.netflix.spinnaker.orca.pipeline.model.Execution
import com.netflix.spinnaker.orca.pipeline.model.Stage
import org.springframework.beans.factory.annotation.Autowired
import static com.netflix.spinnaker.orca.pipeline.StageDefinitionBuilder.StageDefinitionBuilderSupport.newStage
import static com.netflix.spinnaker.orca.pipeline.model.SyntheticStageOwner.STAGE_AFTER
import static com.netflix.spinnaker.orca.pipeline.model.SyntheticStageOwner.STAGE_BEFORE

@Slf4j
abstract class TargetServerGroupLinearStageSupport implements StageDefinitionBuilder, Nameable {

  @Autowired
  TargetServerGroupResolver resolver

  @Autowired
  DetermineTargetServerGroupStage determineTargetServerGroupStage

  String name = this.type

  @Override
  def <T extends Execution> List<Stage<T>> aroundStages(Stage<T> parentStage) {
    return composeTargets(parentStage)
  }

  List<Stage> composeTargets(Stage stage) {
    stage.resolveStrategyParams()
    def params = TargetServerGroup.Params.fromStage(stage)
    if (TargetServerGroup.isDynamicallyBound(stage)) {
      return composeDynamicTargets(stage, params)
    }

    return composeStaticTargets(stage, params)
  }

  private List<Stage> composeStaticTargets(Stage stage, TargetServerGroup.Params params) {
    if (stage.parentStageId) {
      // Only process this stage as-is when the user specifies. Otherwise, the targets should already be defined in the
      // context.
      return []
    }

    def stages = []

    def targets = resolver.resolveByParams(params)
    def descriptionList = buildStaticTargetDescriptions(stage, targets)
    def first = descriptionList.remove(0)
    stage.context.putAll(first)

    preStatic(first).each {
      stages << newStage(stage.execution, it.stage.type, it.name, it.context, stage, STAGE_BEFORE)
    }
    postStatic(first).each {
      stages << newStage(stage.execution, it.stage.type, it.name, it.context, stage, STAGE_AFTER)
    }

    for (description in descriptionList) {
      preStatic(description).each {
        // Operations done after the first iteration must all be added with injectAfter.
        stages << newStage(stage.execution, it.stage.type, it.name, it.context, stage, STAGE_AFTER)
      }
      stages << newStage(stage.execution, this.type, name, description, stage, STAGE_AFTER)

      postStatic(description).each {
        stages << newStage(stage.execution, it.stage.type, it.name, it.context, stage, STAGE_AFTER)
      }
    }

    return stages
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

  private List<Stage> composeDynamicTargets(Stage stage, TargetServerGroup.Params params) {
    if (stage.parentStageId) {
      // We only want to determine the target server groups once per stage, so only inject if this is the root stage,
      // i.e. the one the user configured.
      // This may become a bad assumption, or a limiting one, in that we cannot inject a dynamic stage ourselves
      // as part of some other stage that is not itself injecting a determineTargetReferences stage.
      return []
    }

    def stages = []

    // Scrub the context of any preset location.
    stage.context.with {
      remove("zone")
      remove("zones")
      remove("region")
      remove("regions")
    }

    def singularLocationType = params.locations[0].singularType()
    def pluralLocationType = params.locations[0].pluralType()

    Map dtsgContext = new HashMap(stage.context)
    dtsgContext[pluralLocationType] = params.locations.collect { it.value }

    // The original stage.context object is reused here because concrete subclasses must actually perform the requested
    // operation. All future copies of the subclass (operating on different regions/zones) use a copy of the context.
    def initialLocation = params.locations.head()
    def remainingLocations = params.locations.tail()
    stage.context[singularLocationType] = initialLocation.value
    stage.context.targetLocation = [type: initialLocation.type.name(), value: initialLocation.value]

    preDynamic(stage.context).each {
      stages << newStage(stage.execution, it.stage.type, it.name, it.context, stage, STAGE_BEFORE)
    }
    postDynamic(stage.context).each {
      stages << newStage(stage.execution, it.stage.type, it.name, it.context, stage, STAGE_AFTER)
    }

    for (location in remainingLocations) {
      def ctx = new HashMap(stage.context)
      ctx[singularLocationType] = location.value
      ctx.targetLocation = [type: location.type.name(), value: location.value]
      preDynamic(ctx).each {
        // Operations done after the first pre-postDynamic injection must all be added with injectAfter.
        stages << newStage(stage.execution, it.stage.type, it.name, it.context, stage, STAGE_AFTER)
      }
      stages << newStage(stage.execution, this.type, name, ctx, stage, STAGE_AFTER)
      postDynamic(ctx).each {
        stages << newStage(stage.execution, it.stage.type, it.name, it.context, stage, STAGE_AFTER)
      }
    }

    // For silly reasons, this must be added after the pre/post-DynamicInject to get the execution order right.
    stages << newStage(stage.execution, determineTargetServerGroupStage.type, DetermineTargetServerGroupStage.PIPELINE_CONFIG_TYPE, dtsgContext, stage, STAGE_BEFORE)
    return stages
  }

  protected List<Injectable> preStatic(Map descriptor) {}

  protected List<Injectable> postStatic(Map descriptor) {}

  protected List<Injectable> preDynamic(Map context) {}

  protected List<Injectable> postDynamic(Map context) {}

  static class Injectable {
    String name
    StageDefinitionBuilder stage
    Map<String, Object> context
  }
}
