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

package com.netflix.spinnaker.orca.mine.tasks

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.frigga.NameBuilder
import com.netflix.frigga.Names
import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.Task
import com.netflix.spinnaker.orca.TaskResult
import com.netflix.spinnaker.orca.kato.pipeline.ParallelDeployStage
import com.netflix.spinnaker.orca.mine.Canary
import com.netflix.spinnaker.orca.mine.CanaryConfig
import com.netflix.spinnaker.orca.mine.CanaryDeployment
import com.netflix.spinnaker.orca.mine.Cluster
import com.netflix.spinnaker.orca.mine.MineService
import com.netflix.spinnaker.orca.mine.Recipient
import com.netflix.spinnaker.orca.mine.pipeline.CanaryStage
import com.netflix.spinnaker.orca.pipeline.model.Stage
import groovy.transform.Canonical
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import retrofit.client.Response

import java.util.regex.Pattern

@Component
class RegisterCanaryTask implements Task {

  static final Pattern DETAIL_PATTERN = ~/^(.*)_?(canary|baseline)$/

  @Autowired
  MineService mineService
  @Autowired
  ObjectMapper objectMapper
  @Autowired
  ResultSerializationHelper resultSerializationHelper

  @Override
  TaskResult execute(Stage stage) {
    String app = stage.context.application ?: stage.execution.application
    Canary c = buildCanary(app, stage)
    Response response = mineService.registerCanary(c)
    String canaryId
    if (response.status == 200 && response.body.mimeType().startsWith('text/plain')) {
      canaryId = response.body.in().text
    } else {
      throw new IllegalStateException("Unable to handle $response")
    }
    Canary canary = mineService.checkCanaryStatus(canaryId)
    return resultSerializationHelper.result(ExecutionStatus.SUCCEEDED, [canary: canary])
  }

  Canary buildCanary(String app, Stage stage) {
    Canary c = new Canary()
    c.application = app
    def context = stage.context
    c.owner = objectMapper.convertValue(context.owner, Recipient)
    c.watchers = objectMapper.convertValue(context.watchers, new TypeReference<List<Recipient>>() {})
    c.canaryConfig = objectMapper.convertValue(context.canaryConfig, CanaryConfig)
    c.canaryConfig.name = c.canaryConfig.name ?: stage.execution.id
    c.canaryConfig.application = app

    def preceedingCanary = stage.preceding(CanaryStage.MAYO_CONFIG_TYPE)

    def allStages = stage.execution.stages

    def firstCanary = allStages.find { it.type == CanaryStage.MAYO_CONFIG_TYPE }
    def toUse = preceedingCanary ?: firstCanary

    if (!toUse) { throw new IllegalStateException('wat') }
    def deployCanaryId = toUse.id

    Map<String, CanaryDeployment> clusters = [:].withDefault { new CanaryDeployment() }

    for (Stage s in stage.execution.stages.findAll {
      it.type == ParallelDeployStage.MAYO_CONFIG_TYPE && it.parentStageId == deployCanaryId
    }) {
      String account = s.context.account
      s.context.'deploy.server.groups'.each { String region, List<String> serverGroups ->
        for (sg in serverGroups) {
          def cluster = buildCluster(sg, region, account)
          if (cluster.type == 'canary') {
            cluster.cluster.imageId = s.context.deploymentDetails.find { it.region == region }?.ami
            clusters[cluster.key].canaryCluster = cluster.cluster
          } else {
            cluster.cluster.imageId = s.context.amiName
            clusters[cluster.key].baselineCluster = cluster.cluster
          }
        }
      }
    }

    def unmatched = clusters.values().findAll { it.canaryCluster == null || it.baselineCluster == null }
    if (unmatched) {
      throw new IllegalStateException("Didn't pair up ${unmatched}")
    }

    c.canaryDeployments.addAll(clusters.values())


    return c
  }


  static TypedCluster buildCluster(String asgName, String region, String account) {
    Names name = Names.parseName(asgName)
    def match = name.detail =~ DETAIL_PATTERN
    if (match.matches()) {
      String baseName = new CanaryClusterNameBuilder(name.app, name.stack, match.group(1)).baseName
      return new TypedCluster(match.group(2), baseName, new Cluster(null, asgName, 'aws', account, region))
    }
    return null
  }

  @Canonical
  static class TypedCluster {
    String type
    String baseName
    Cluster cluster

    String getKey() {
      "$cluster.accountName:$cluster.region:$baseName"
    }
  }

  private static class CanaryClusterNameBuilder extends NameBuilder {
    String app
    String stack
    String freeFormDetails

    CanaryClusterNameBuilder(String app, String stack, String freeFormDetails) {
      this.app = app
      this.stack = stack
      this.freeFormDetails = freeFormDetails
    }

    String getBaseName() {
      super.combineAppStackDetail(app, stack, freeFormDetails)
    }
  }
}
