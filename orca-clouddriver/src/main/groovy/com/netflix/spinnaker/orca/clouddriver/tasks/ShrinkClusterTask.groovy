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

import com.netflix.frigga.Names
import com.netflix.spinnaker.orca.DefaultTaskResult
import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.RetryableTask
import com.netflix.spinnaker.orca.TaskResult
import com.netflix.spinnaker.orca.clouddriver.KatoService
import com.netflix.spinnaker.orca.clouddriver.utils.OortHelper
import com.netflix.spinnaker.orca.pipeline.model.Stage
import groovy.transform.Canonical
import groovy.transform.PackageScope
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component
class ShrinkClusterTask implements RetryableTask {

  final long backoffPeriod = 5000
  final long timeout = 30000

  @Autowired OortHelper oortHelper
  @Autowired KatoService katoService

  @Canonical
  static class ShrinkConfig {
    String cluster
    String cloudProvider = 'aws'
    String credentials
    Set<String> regions
    boolean allowDeleteActive = false
    int shrinkToSize = 1
    boolean retainLargerOverNewer = false
  }


  @Override
  TaskResult execute(Stage stage) {
    def config = stage.mapTo(ShrinkConfig)
    def names = Names.parseName(config.cluster)


    Optional<Map> cluster = oortHelper.getCluster(names.app, config.credentials, config.cluster, config.cloudProvider)
    if (!cluster.isPresent()) {
      return DefaultTaskResult.SUCCEEDED
    }

    List<Map> serverGroups = cluster.get().serverGroups

    Map<String, List<Map>> serverGroupsByRegion = serverGroups.groupBy { it.region }

    List<Map> deletions = config.regions.findResults
    {
      def regionGroups = serverGroupsByRegion[it]
      if (!regionGroups) {
        return null
      }
      getDeletionServerGroups(regionGroups, config.retainLargerOverNewer, config.allowDeleteActive, config.shrinkToSize) ?: null
    }.flatten()

    List<Map<String, Map>> katoOps = deletions.collect {
      [destroyServerGroup: [
        //TODO(cfieber) - this is likely not correct for DeleteGoogleReplicaPoolDescription
        credentials: config.credentials,
        accountName: config.credentials,

        //TODO(cfieber) - dedupe
        regions: [it.region],
        region: it.region,

        //TODO(cfieber) - dedupe
        serverGroupName: it.name,
        asgName: it.name,

        //TODO(cfieber) - dedupe
        cloudProvider: config.cloudProvider,
        providerType: config.cloudProvider
      ]]
    }

    if (!katoOps) {
      return DefaultTaskResult.SUCCEEDED
    }

    def regionGroups = [:].withDefault { [] }
    deletions.each {
      regionGroups[it.region] << it.name
    }

    def taskId = katoService.requestOperations(config.cloudProvider, katoOps).toBlocking().first()
    new DefaultTaskResult(ExecutionStatus.SUCCEEDED, [
      "notification.type"   : "shrinkcluster",
      "deploy.account.name" : config.credentials,
      "kato.last.task.id"   : taskId,
      "deploy.server.groups": regionGroups
    ])
  }

  /**
   * From the provided serverGroups (all within the same region), determine which to delete.
   *
   * @param serverGroups the candidates for deletion - all must be in the same region
   * @param retainLargerOverNewer whether a larger security group should take priority over a newer security group
   * @param allowDeleteActive whether active server groups should be considered for deletion
   * @param shrinkToSize the number of serverGroups to retain
   * @return the list of server groups for deletion
   */
  @PackageScope
  List<Map> getDeletionServerGroups(List<Map> serverGroups, boolean retainLargerOverNewer, boolean allowDeleteActive, int shrinkToSize) {
    if (!serverGroups) {
      return []
    }

    String region = serverGroups[0].region
    if (!serverGroups.every { it.region == region }) {
      throw new IllegalStateException("all server groups must be in the same region, found ${serverGroups*.region}")
    }

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
      (o2.disabled == false || o2.instances.any { it.healthState == 'Up' }) <=> (o1.disabled == false || o1.instances.any { it.healthState == 'Up' })
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
