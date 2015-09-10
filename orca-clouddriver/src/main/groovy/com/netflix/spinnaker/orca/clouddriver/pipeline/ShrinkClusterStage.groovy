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

package com.netflix.spinnaker.orca.clouddriver.pipeline

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.frigga.Names
import com.netflix.spinnaker.orca.DefaultTaskResult
import com.netflix.spinnaker.orca.Task
import com.netflix.spinnaker.orca.TaskResult
import com.netflix.spinnaker.orca.clouddriver.OortService
import com.netflix.spinnaker.orca.pipeline.ParallelStage
import com.netflix.spinnaker.orca.pipeline.model.Stage
import groovy.transform.PackageScope
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component
class ShrinkClusterStage extends ParallelStage {
  public static final String PIPELINE_CONFIG_TYPE = "shrinkCluster"

  ShrinkClusterStage() {
    super(PIPELINE_CONFIG_TYPE)
  }

  @Autowired
  OortService oortService

  @Autowired
  ObjectMapper objectMapper

  @Override
  String parallelStageName(Stage stage, boolean hasParallelFlows) {
    return "Shrink Cluster"
  }

  @Override
  Task completeParallel() {
    return new Task() {
      @Override
      TaskResult execute(Stage stage) {
        DefaultTaskResult.SUCCEEDED
      }
    }
  }

  @Override
  List<Map<String, Object>> parallelContexts(Stage stage) {
    String clusterName = stage.context.cluster
    def names = Names.parseName(clusterName)
    String cloudProvider = stage.context.cloudProvider ?: 'aws'
    String account = stage.context.account
    Set<String> regions = stage.context.regions as Set
    boolean allowDeleteActive = Boolean.valueOf(stage.context.allowDeleteActive as String)
    int shrinkToSize = Integer.parseInt(stage.context.shrinkToSize as String ?: "1")
    boolean retainLargerOverNewer = Boolean.valueOf(stage.context.retainLargerOverNewer as String)

    def response = oortService.getCluster(names.app, account, clusterName, cloudProvider)
    Map cluster = objectMapper.readValue(response.body.in(), Map)

    List<Map> serverGroups = cluster.serverGroups

    Map<String, List<Map>> serverGroupsByRegion = serverGroups.groupBy { it.region }

    regions.findResults {
      def regionGroups = serverGroupsByRegion[it]
      if (!regionGroups) {
        return null
      }
      getDeletionPriorityServerGroups(regionGroups, retainLargerOverNewer, allowDeleteActive, shrinkToSize) ?: null
    }.flatten().collect {
      [
        type: DestroyServerGroupStage.PIPELINE_CONFIG_TYPE,
        region: it.region,
        account: account,
        serverGroupName: it.name,
        cloudProvider: cloudProvider
      ]
    }
  }


  @PackageScope List<Map> getDeletionPriorityServerGroups(List<Map> serverGroups, boolean retainLargerOverNewer, boolean allowDeleteActive, int shrinkToSize) {
    def comparators = []
    int dropCount = shrinkToSize
    if (allowDeleteActive) {
      comparators << new IsActive()
    } else {
      int origSize = serverGroups.size()
      serverGroups = serverGroups.findAll { it.instances.every { it.healthState != 'Up' }}
      int activeServerGroups = origSize - serverGroups.size()
      dropCount = Math.max(0, shrinkToSize - activeServerGroups)
    }
    if (retainLargerOverNewer) {
      comparators << new InstanceCount()
    }
    comparators << new CreatedTime()

    //result will be sorted in priority order to retain
    def prioritized = serverGroups.sort(false, new CompositeComparitor(comparators))

    return prioritized.drop(dropCount)
  }

  static class IsActive implements Comparator<Map> {
    @Override
    int compare(Map o1, Map o2) {
      o2.instances.any { it.healthState == 'Up' } <=> o1.instances.any { it.healthState == 'Up' }
    }
  }

  static class InstanceCount implements Comparator<Map> {
    @Override
    int compare(Map o1, Map o2) {
      o2.instances.size() <=> o1.instances.size()
    }
  }

  static class CreatedTime implements Comparator<Map> {
    @Override
    int compare(Map o1, Map o2) {
      o2.createdTime <=> o1.createdTime
    }
  }

  static class CompositeComparitor implements Comparator<Map> {
    private final List<Comparator<Map>> comparators

    CompositeComparitor(List<Comparator<Map>> comparators) {
      this.comparators = comparators
    }

    @Override
    int compare(Map o1, Map o2) {
      comparators.findResult { it.compare(o1, o2) ?: null } ?: 0
    }
  }
}
