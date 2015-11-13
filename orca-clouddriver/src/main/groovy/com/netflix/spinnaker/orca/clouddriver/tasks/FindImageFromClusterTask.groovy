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

package com.netflix.spinnaker.orca.clouddriver.tasks

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.frigga.Names
import com.netflix.spinnaker.orca.DefaultTaskResult
import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.RetryableTask
import com.netflix.spinnaker.orca.TaskResult
import com.netflix.spinnaker.orca.clouddriver.OortService
import com.netflix.spinnaker.orca.clouddriver.pipeline.support.Location
import com.netflix.spinnaker.orca.pipeline.model.Stage
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import retrofit.RetrofitError

@Component
@Slf4j
class FindImageFromClusterTask extends AbstractCloudProviderAwareTask implements RetryableTask {

  static String SUMMARY_TYPE = "Image"

  final long backoffPeriod = 2000

  final long timeout = 60000

  static enum SelectionStrategy {
    /**
     * Choose the server group with the most instances, falling back to newest in the case of a tie
     */
    LARGEST,

    /**
     * Choose the newest ServerGroup by createdTime
     */
    NEWEST,

    /**
     * Choose the oldest ServerGroup by createdTime
     */
    OLDEST,

    /**
     * Fail if there is more than one server group to choose from
     */
    FAIL
  }

  @Autowired
  OortService oortService
  @Autowired
  ObjectMapper objectMapper

  @Override
  TaskResult execute(Stage stage) {
    String cloudProvider = getCloudProvider(stage)
    String app = Names.parseName(stage.context.cluster).app
    String account = getCredentials(stage)
    String cluster = stage.context.cluster
    Set<Location> requiredLocations = []
    if (stage.context.regions) {
      requiredLocations = stage.context.regions.collect({
        return new Location(type: Location.Type.REGION, value: it)
      }) as Set
    } else if (stage.context.zones) {
      requiredLocations = stage.context.zones.collect({
        return new Location(type: Location.Type.ZONE, value: it)
      }) as Set
    }

    def onlyEnabled = stage.context.onlyEnabled == null ? true : (Boolean.valueOf(stage.context.onlyEnabled.toString()))
    def selectionStrat = SelectionStrategy.valueOf(stage.context.selectionStrategy?.toString() ?: "NEWEST")

    Map<Location, Map<String, Object>> imageSummaries = requiredLocations.collectEntries { location ->
      try {
        def lookupResults = oortService.getServerGroupSummary(
          app,
          account,
          cluster,
          cloudProvider,
          location.value,
          selectionStrat.toString(),
          SUMMARY_TYPE,
          onlyEnabled.toString())
        return [(location): lookupResults]
      } catch (RetrofitError e) {
        if (e.response.status == 404) {
          def message = "Could not find cluster '$cluster' in account '$account'"
          try {
            Map reason = objectMapper.readValue(e.response.body.in(), new TypeReference<Map<String, Object>>() {})
            if (reason.error.contains("target.fail.strategy")){
              message = "Multiple possible server groups present in ${location.value}"
            }
          } catch (Exception ignored) {}
          throw new IllegalStateException(message)
        }
        throw e
      }
    }

    List<Map> deploymentDetails = imageSummaries.collect { location, summary ->
      def result = [
        ami              : summary.imageId, // TODO(ttomsu): Deprecate and remove this value.
        imageId          : summary.imageId,
        imageName        : summary.imageName,
        sourceServerGroup: summary.serverGroupName
      ]

      if (location.type == Location.Type.REGION) {
        result.region = location.value
      } else if (location.type == Location.Type.ZONE) {
        result.zone = location.value
      }

      try {
        result.putAll(summary.image ?: [:])
        result.putAll(summary.buildInfo ?: [:])
      } catch (Exception e) {
        log.error("Unable to merge server group image/build info (summary: ${summary})", e)
      }

      return result
    }

    return new DefaultTaskResult(ExecutionStatus.SUCCEEDED, [
      amiDetails: deploymentDetails
    ], [
      deploymentDetails: deploymentDetails
    ])
  }
}
