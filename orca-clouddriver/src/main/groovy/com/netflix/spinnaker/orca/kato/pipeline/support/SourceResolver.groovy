/*
 * Copyright 2015 Netflix, Inc.
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

import com.fasterxml.jackson.core.JsonParseException
import com.fasterxml.jackson.databind.JsonMappingException
import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.orca.clouddriver.OortService
import com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.support.Location
import com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.support.TargetServerGroup
import com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.support.TargetServerGroupResolver
import com.netflix.spinnaker.orca.pipeline.model.Stage
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import retrofit.RetrofitError

@Component
@CompileStatic
@Slf4j
class SourceResolver {

  @Autowired OortService oortService
  @Autowired ObjectMapper mapper

  @Autowired
  TargetServerGroupResolver resolver

  StageData.Source getSource(Stage stage) throws RetrofitError, JsonParseException, JsonMappingException {
    def stageData = stage.mapTo(StageData)
    if (stageData.source) {
      // targeting a source in a different account and region
      if (stageData.source.clusterName && stage.context.target) {
        TargetServerGroup.Params params = new TargetServerGroup.Params(
          cloudProvider:  stageData.cloudProvider,
          credentials: stageData.source.account,
          cluster: stageData.source.clusterName,
          target: TargetServerGroup.Params.Target.valueOf(stage.context.target as String),
          locations: [Location.region(stageData.source.region)]
        )

        def targetServerGroups = resolver.resolveByParams(params)

        if (targetServerGroups) {
          return new StageData.Source(account: params.credentials as String,
            region: targetServerGroups[0].region as String,
            serverGroupName: targetServerGroups[0].name as String,
            asgName: targetServerGroups[0].name as String)
        } else {
          return null
        }
      } else {
        // has an existing source, return it
        return stageData.source
      }
    } else if (stage.context.target) {
      // If no source was specified, but targeting coordinates were, attempt to resolve the target server group.
      TargetServerGroup.Params params = TargetServerGroup.Params.fromStage(stage)

      if (!params.cluster && stage.context.targetCluster) {
        params.cluster = stage.context.targetCluster
      }

      def targetServerGroups = resolver.resolveByParams(params)

      if (targetServerGroups) {
        return new StageData.Source(account: params.credentials as String,
                                    region: targetServerGroups[0].region as String,
                                    serverGroupName: targetServerGroups[0].name as String,
                                    asgName: targetServerGroups[0].name as String)
      }
    }

    def existingAsgs = getExistingAsgs(
      stageData.application, stageData.account, stageData.cluster, stageData.cloudProvider
    )

    if (!existingAsgs) {
      return null
    }

    if (!stageData.region && !stageData.availabilityZones) {
      throw new IllegalStateException("No 'region' or 'availabilityZones' in stage context")
    }

    def targetRegion = stageData.region
    def regionalAsgs = existingAsgs.findAll { it.region == targetRegion } as List<Map>
    if (!regionalAsgs) {
      return null
    }

    //prefer enabled ASGs but allow disabled in favour of nothing
    def onlyEnabled = regionalAsgs.findAll { it.disabled == false }
    if (onlyEnabled) {
      regionalAsgs = onlyEnabled
    }

    //with useSourceCapacity prefer the largest ASG over the newest ASG
    def latestAsg = stageData.useSourceCapacity ? regionalAsgs.sort { (it.instances as Collection)?.size() ?: 0 }.last() : regionalAsgs.last()
    return new StageData.Source(
      account: stageData.account, region: latestAsg["region"] as String, asgName: latestAsg["name"] as String, serverGroupName: latestAsg["name"] as String
    )
  }

  List<Map> getExistingAsgs(String app, String account, String cluster, String cloudProvider) throws RetrofitError, JsonParseException, JsonMappingException {
    try {
      def response = oortService.getCluster(app, account, cluster, cloudProvider)
      def json = response.body.in().text
      def map = mapper.readValue(json, Map)
      (map.serverGroups as List<Map>).sort { it.createdTime }
    } catch (RetrofitError re) {
      if (re.kind == RetrofitError.Kind.HTTP && re.response.status == 404) {
        return []
      }
      throw re
    }
  }

}
