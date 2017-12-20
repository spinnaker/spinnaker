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


package com.netflix.spinnaker.orca.clouddriver.pipeline.providers.aws

import com.netflix.spinnaker.kork.core.RetrySupport
import com.netflix.spinnaker.orca.clouddriver.FeaturesService
import com.netflix.spinnaker.orca.clouddriver.OortService
import com.netflix.spinnaker.orca.clouddriver.pipeline.entitytags.DeleteEntityTagsStage
import com.netflix.spinnaker.orca.clouddriver.tasks.MonitorKatoTask
import com.netflix.spinnaker.orca.clouddriver.tasks.servergroup.ServerGroupCacheForceRefreshTask
import com.netflix.spinnaker.orca.pipeline.StageDefinitionBuilder
import com.netflix.spinnaker.orca.pipeline.TaskNode
import com.netflix.spinnaker.orca.pipeline.model.Stage
import com.netflix.spinnaker.orca.pipeline.model.SyntheticStageOwner
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

import javax.annotation.Nonnull

import static com.netflix.spinnaker.orca.clouddriver.tasks.servergroup.PinnedServerGroupTagGenerator.PINNED_CAPACITY_TAG

/**
 * Ensure that external scaling policies do not adversely affect a server group as it is in the process of being deployed.
 *
 * It accomplishes this by:
 * - capturing the source server group 'min' capacity prior to this deploy ({@link CaptureSourceServerGroupCapacityTask})
 * - creating a new server group with min = desired ({@link CaptureSourceServerGroupCapacityTask})
 * - restoring min after the deploy has completed ({@link ApplySourceServerGroupCapacityTask})
 */
@Slf4j
@Component
class ApplySourceServerGroupCapacityStage implements StageDefinitionBuilder {
  @Autowired
  private FeaturesService featuresService

  @Autowired
  DeleteEntityTagsStage deleteEntityTagsStage

  @Autowired
  OortService oortService

  @Autowired
  RetrySupport retrySupport

  @Override
  void taskGraph(Stage stage, TaskNode.Builder builder) {
    builder
      .withTask("restoreMinCapacity", ApplySourceServerGroupCapacityTask)
      .withTask("waitForCapacityMatch", MonitorKatoTask)
      .withTask("forceCacheRefresh", ServerGroupCacheForceRefreshTask)
  }

  @Override
  List<Stage> afterStages(@Nonnull Stage stage) {
    try {
      def taggingEnabled = featuresService.isStageAvailable("upsertEntityTags")
      if (!taggingEnabled) {
        return []
      }

      def entityTags = fetchEntityTags(oortService, retrySupport, stage)?.get(0)
      if (!entityTags) {
        return []
      }

      return [
        newStage(
          stage.execution,
          deleteEntityTagsStage.type,
          "Cleanup Server Group Tags",
          [
            id  : entityTags.id,
            tags: Collections.singletonList(PINNED_CAPACITY_TAG)
          ],
          stage,
          SyntheticStageOwner.STAGE_AFTER
        )
      ]
    } catch (Exception e) {
      log.error(
        "Unable to determine whether server group is pinned (serverGroup: {}, account: {}, region: {})",
        stage.context.serverGroupName,
        stage.context.credentials,
        getRegion(stage),
        e
      )

      // any false negatives (pinned server groups that were not detected) will be caught by the RestorePinnedServerGroupsAgent
      return []
    }
  }

  private static List<Map> fetchEntityTags(OortService oortService, RetrySupport retrySupport, Stage stage) {
    retrySupport.retry({
      return oortService.getEntityTags([
        ("tag:${PINNED_CAPACITY_TAG}".toString()): "*",
        entityId                                 : stage.context.serverGroupName,
        account                                  : stage.context.credentials,
        region                                   : getRegion(stage)
      ])
    }, 5, 2000, false)
  }

  private static String getRegion(Stage stage) {
    return ((Map<String, Object>) stage.context."deploy.server.groups").keySet()?.getAt(0)
  }
}
