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


package com.netflix.spinnaker.orca.clouddriver.tasks.cluster

import com.netflix.spinnaker.orca.TaskResult
import com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.CloneServerGroupStage
import com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.CreateServerGroupStage
import com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.DisableServerGroupStage
import com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.support.Location
import com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.support.TargetServerGroup
import com.netflix.spinnaker.orca.clouddriver.utils.OortHelper
import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import com.netflix.spinnaker.orca.pipeline.model.Stage
import spock.lang.Specification
import spock.lang.Unroll

class AbstractClusterWideClouddriverTaskSpec extends Specification {
  static def sg1 = sg("sg-1", "us-west-1", 100)
  static def sg2 = sg("sg-2", "us-west-1", 200)
  static def sg3 = sg("sg-3", "us-west-1", 300)
  static def sg4 = sg("sg-4", "us-east-1", 400)

  def "should extract server groups from parent stages"() {
    given:
    def pipeline = new Pipeline()
    pipeline.stages << new Stage<>(pipeline, null)
    pipeline.stages << new Stage<>(pipeline, CreateServerGroupStage.PIPELINE_CONFIG_TYPE, [
      "deploy.account.name" : account,
      "deploy.server.groups": [
        "us-west-1": [sg1.name]
      ]
    ])
    pipeline.stages << new Stage<>(pipeline, CloneServerGroupStage.PIPELINE_CONFIG_TYPE, [
      "deploy.account.name" : account,
      "deploy.server.groups": [
        "us-west-1": [sg2.name, sg4.name]
      ]
    ])

    pipeline.stages[0].parentStageId = pipeline.stages[1].id
    pipeline.stages[1].requisiteStageRefIds = ["2"]
    pipeline.stages[2].refId = "2"

    when:
    def targetServerGroups = AbstractClusterWideClouddriverTask.parentDeployedServerGroups(
      pipeline.stages[0],
      account,
      location,
      clusterServerGroups
    )

    then:
    targetServerGroups*.name == [sg1.name, sg2.name]

    where:
    account = "test"
    location = new Location(Location.Type.REGION, "us-west-1")

    clusterServerGroups = [
      sg1, sg2, sg3, sg4
    ]
  }

  @Unroll
  def "should filter out server groups that are newer than parent deploys"() {
    when:
    def targetServerGroups = AbstractClusterWideClouddriverTask.filterParentAndNewerThanParentDeploys(
      parentDeployedServerGroups,
      clusterServerGroups
    )

    then:
    targetServerGroups == expectedTargetServerGroups

    where:
    parentDeployedServerGroups | clusterServerGroups  || expectedTargetServerGroups
    [sg1]                      | [sg1, sg2, sg3, sg4] || []
    [sg2]                      | [sg1, sg2, sg3, sg4] || [sg1]
    [sg3, sg4]                 | [sg1, sg2, sg3, sg4] || [sg1, sg2]
  }

  def "should succeed immediately if cluster not found and continueIfClusterNotFound flag set in context"() {
    given:
    def pipeline = new Pipeline()
    def stage = new Stage<>(pipeline, DisableServerGroupStage.PIPELINE_CONFIG_TYPE, [
        continueIfClusterNotFound: true
    ])
    def task = new AbstractClusterWideClouddriverTask() {
      @Override
      String getClouddriverOperation() {
        return null
      }
    }
    def oortHelper = Mock(OortHelper)
    task.oortHelper = oortHelper;

    when:
    def result = task.execute(stage)

    then:
    1 * oortHelper.getCluster(_, _, _, _) >> Optional.empty()
    result == TaskResult.SUCCEEDED

  }

  def "should succeed immediately if cluster is empty and continueIfClusterNotFound flag set in context"() {
    given:
    def pipeline = new Pipeline()
    def stage = new Stage<>(pipeline, DisableServerGroupStage.PIPELINE_CONFIG_TYPE, [
        continueIfClusterNotFound: true
    ])
    def task = new AbstractClusterWideClouddriverTask() {
      @Override
      String getClouddriverOperation() {
        return null
      }
    }
    def oortHelper = Mock(OortHelper)
    task.oortHelper = oortHelper;

    when:
    def result = task.execute(stage)

    then:
    1 * oortHelper.getCluster(_, _, _, _) >> Optional.of([ serverGroups: [] ])
    result == TaskResult.SUCCEEDED

  }

  static TargetServerGroup sg(String name, String region = "us-west-1", int createdTime = System.currentTimeMillis()) {
    new TargetServerGroup(name: name, region: region, createdTime: createdTime)
  }
}
