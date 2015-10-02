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

package com.netflix.spinnaker.orca.clouddriver.tasks

import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.clouddriver.utils.OortHelper
import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import com.netflix.spinnaker.orca.pipeline.model.PipelineStage
import com.netflix.spinnaker.orca.pipeline.model.Stage
import spock.lang.Specification
import spock.lang.Subject

class WaitForClusterShrinkTaskSpec extends Specification {
  private static final String application = 'bar'
  private static final String cluster = 'bar'
  private static final String credentials = 'test'
  private static final String cloudProvider = 'aws'

  OortHelper oortHelper = Mock(OortHelper)
  @Subject WaitForClusterShrinkTask task = new WaitForClusterShrinkTask(oortHelper: oortHelper)

  def 'uses deploy.server.groups to populate inital waitingOnDestroy list'() {
    when:
    def result = task.execute(stage(['deploy.server.groups': deployServerGroups]))

    then:
    1 * oortHelper.getCluster(application, credentials, cluster, cloudProvider) >> Optional.of([serverGroups: serverGroups])

    result.status == ExecutionStatus.RUNNING
    result.stageOutputs.waitingOnDestroy == serverGroups

    where:
    deployServerGroups = [r1: ['s1', 's2'], r2: ['s3', 's4']]
    serverGroups = deployServerGroups.collect { k, v -> v.collect { [region: k, name: it]}}.flatten()
  }


  def 'succeeds immediately if nothing to wait for'() {
    expect:
    task.execute(stage([:])).status == ExecutionStatus.SUCCEEDED
  }

  def 'succeeds if no cluster present'() {
    when:
    def result = task.execute(stage([waitingOnDestroy: [sg('c1')]]))

    then:
    1 * oortHelper.getCluster(application, credentials, cluster, cloudProvider) >> Optional.empty()

    result.status == ExecutionStatus.SUCCEEDED
  }

  def 'succeeds if no server groups in cluster'() {
    when:
    def result = task.execute(stage([waitingOnDestroy: [sg('c1')]]))

    then:
    1 * oortHelper.getCluster(application, credentials, cluster, cloudProvider) >> Optional.of([serverGroups: []])

    result.status == ExecutionStatus.SUCCEEDED

  }

  def 'runs while there are still server groups'() {
    when:
    def result = task.execute(stage([waitingOnDestroy: [sg('c1'), sg('c2')]]))

    then:
    1 * oortHelper.getCluster(application, credentials, cluster, cloudProvider) >> Optional.of([serverGroups: [sg('c1')]])

    result.status == ExecutionStatus.RUNNING
    result.stageOutputs.waitingOnDestroy == [sg('c1')]
  }

  def 'finishes when last serverGroups disappear'() {
    when:
    def result = task.execute(stage([waitingOnDestroy: [sg('c1')]]))

    then:
    1 * oortHelper.getCluster(application, credentials, cluster, cloudProvider) >> Optional.of([serverGroups: [sg('c2'), sg('c3')]])

    result.status == ExecutionStatus.SUCCEEDED
  }


  Map sg(String name, String region = 'e1') {
    [name: name, region: region]
  }

  Stage stage(Map context) {
    def base = [
      cluster: cluster,
      credentials: credentials,
      cloudProvider: cloudProvider
    ]
    new PipelineStage(new Pipeline(), 'shrinkCluster', base + context)
  }
}
