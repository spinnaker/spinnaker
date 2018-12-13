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

import com.netflix.spinnaker.kork.dynamicconfig.DynamicConfigService

import javax.annotation.Nonnull
import com.netflix.spinnaker.kork.core.RetrySupport
import com.netflix.spinnaker.orca.clouddriver.FeaturesService
import com.netflix.spinnaker.orca.clouddriver.OortService
import com.netflix.spinnaker.orca.clouddriver.pipeline.entitytags.DeleteEntityTagsStage
import com.netflix.spinnaker.orca.clouddriver.tasks.MonitorKatoTask
import com.netflix.spinnaker.orca.clouddriver.tasks.servergroup.ServerGroupCacheForceRefreshTask
import com.netflix.spinnaker.orca.pipeline.StageDefinitionBuilder
import com.netflix.spinnaker.orca.pipeline.TaskNode
import com.netflix.spinnaker.orca.pipeline.graph.StageGraphBuilder
import com.netflix.spinnaker.orca.pipeline.model.Stage
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
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

  @Autowired
  DynamicConfigService dynamicConfigService

  @Override
  void taskGraph(Stage stage, TaskNode.Builder builder) {
    builder
      .withTask("restoreMinCapacity", ApplySourceServerGroupCapacityTask)
      .withTask("waitForCapacityMatch", MonitorKatoTask)

    if (isForceCacheRefreshEnabled(dynamicConfigService)) {
      builder.withTask("forceCacheRefresh", ServerGroupCacheForceRefreshTask)
    }
  }

  @Override
  void afterStages(@Nonnull Stage stage, @Nonnull StageGraphBuilder graph) {
    try {
      def taggingEnabled = featuresService.areEntityTagsAvailable()
      if (!taggingEnabled) {
        return
      }

      def entityTags = fetchEntityTags(oortService, retrySupport, stage)?.getAt(0)
      if (!entityTags) {
        return
      }

      graph.add {
        it.type = deleteEntityTagsStage.type
        it.name = "Cleanup Server Group Tags"
        it.context = [
          id  : entityTags.id,
          tags: Collections.singletonList(PINNED_CAPACITY_TAG)
        ]
      }
    } catch (Exception e) {
      log.warn(
        "Unable to determine whether server group is pinned (serverGroup: {}, account: {}, region: {})",
        stage.context.serverGroupName,
        stage.context.credentials,
        getRegion(stage),
        e
      )

      // any false negatives (pinned server groups that were not detected) will be caught by the RestorePinnedServerGroupsAgent
    }
  }

  private static List<Map> fetchEntityTags(OortService oortService, RetrySupport retrySupport, Stage stage) {
    def serverGroupName = stage.context.serverGroupName
    def credentials = stage.context.credentials
    def region = getRegion(stage)

    if (!serverGroupName || !credentials || !region) {
      log.warn(
        "Unable to determine whether server group is pinned (serverGroup: {}, account: {}, region: {}, stageContext: {})",
        serverGroupName,
        credentials,
        region,
        stage.context
      )
      return []
    }

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
    return ((Map<String, Object>) stage.context."deploy.server.groups")?.keySet()?.getAt(0)
  }
}
