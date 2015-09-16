/*
 * Copyright 2015 Google, Inc.
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

package com.netflix.spinnaker.orca.kato.pipeline.support

import com.google.common.annotations.VisibleForTesting
import com.netflix.spinnaker.orca.batch.StageBuilder
import com.netflix.spinnaker.orca.kato.pipeline.DetermineTargetServerGroupStage
import com.netflix.spinnaker.orca.pipeline.LinearStage
import com.netflix.spinnaker.orca.pipeline.model.Stage
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired

@Slf4j
abstract class TargetServerGroupLinearStageSupport extends LinearStage {

  @Autowired
  TargetServerGroupResolver resolver

  @Autowired
  DetermineTargetServerGroupStage determineTargetServerGroupStage

  TargetServerGroupLinearStageSupport(String name) {
    super(name)
  }

  void composeTargets(Stage stage) {
    def tsgs = resolver.resolve(stage)
    if (TargetServerGroup.isDynamicallyBound(stage)) {
      composeDynamicTargets(stage, tsgs)
    } else {
      composeStaticTargets(stage, tsgs)
    }
  }

  private void composeStaticTargets(Stage stage, List<TargetServerGroup> targets) {
    if (stage.parentStageId) {
      // Only process this stage as-is when the user specifies. Otherwise, the targets should already be defined in the
      // context.
      return
    }

    def descriptionList = buildStaticTargetDescriptions(stage, targets)
    def first = descriptionList.remove(0)
    stage.context.putAll(first)

    preStatic(first).each {
      injectBefore(stage, it.type, it.stage, it.context)
    }
    postStatic(first).each {
      injectAfter(stage, it.type, it.stage, it.context)
    }

    for (description in descriptionList) {
      preStatic(description).each {
        // Operations done after the first iteration must all be added with injectAfter.
        injectAfter(stage, it.type, it.stage, it.context)
      }

      injectAfter(stage, this.type, this, description)

      postStatic(description).each {
        injectAfter(stage, it.type, it.stage, it.context)
      }
    }
  }

  protected List<Map<String, Object>> buildStaticTargetDescriptions(Stage stage, List<TargetServerGroup> targets) {
    Map<String, Map<String, Object>> descriptions = [:]
    for (target in targets) {
      def location = target.location
      def serverGroup = target.serverGroup

      def description = new HashMap(stage.context)
      if (descriptions.containsKey(serverGroup.name)) {
        ((List<String>) descriptions.get(serverGroup.name).locations) << location
      } else {
        description.asgName = serverGroup.name
        description.locations = [location]
        descriptions[serverGroup.name as String] = description
      }
    }
    descriptions.values().toList()
  }

  private void composeDynamicTargets(Stage stage, List<TargetServerGroup> tsgs) {
    if (stage.parentStageId) {
      // We only want to determine the target ASGs once per stage, so only inject if this is the root stage, i.e.
      // the one the user configured
      // This may become a bad assumption, or a limiting one, in that we cannot inject a dynamic stage ourselves
      // as part of some other stage that is not itself injecting a determineTargetReferences stage
      return
    }

    def configuredLocations = tsgs.collect { it.location }
    Map dtsgContext = new HashMap(stage.context)
    dtsgContext.regions = new ArrayList(configuredLocations)
    stage.context.regions = [configuredLocations.remove(0)]

    preDynamic(stage.context).each {
      injectBefore(stage, it.type, it.stage, it.context)
    }
    postDynamic(stage.context).each {
      injectAfter(stage, it.type, it.stage, it.context)
    }

    for (location in configuredLocations) {
      def ctx = new HashMap(stage.context)
      ctx.regions = [location]
      preDynamic(ctx).each {
        // Operations done after the first iteration must all be added with injectAfter.
        injectAfter(stage, it.type, it.stage, it.context)
      }
      injectAfter(stage, this.type, this, ctx)
      postDynamic(ctx).each {
        injectAfter(stage, it.type, it.stage, it.context)
      }
    }

    // For silly reasons, this must be added after the pre/post-DynamicInject to get the execution order right.
    injectBefore(stage, DetermineTargetServerGroupStage.PIPELINE_CONFIG_TYPE, determineTargetServerGroupStage, dtsgContext)
  }

  protected List<Injectable> preStatic(Map descriptor) {}

  protected List<Injectable> postStatic(Map descriptor) {}

  protected List<Injectable> preDynamic(Map context) {}

  protected List<Injectable> postDynamic(Map context) {}

  @VisibleForTesting
  static class Injectable {
    String type
    StageBuilder stage
    Map<String, Object> context
  }
}
