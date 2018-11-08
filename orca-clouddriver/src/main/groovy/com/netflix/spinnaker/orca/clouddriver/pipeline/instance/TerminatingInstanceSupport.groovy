/*
 * Copyright 2016 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.orca.clouddriver.pipeline.instance

import com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.support.TargetServerGroup
import com.netflix.spinnaker.orca.clouddriver.utils.CloudProviderAware
import com.netflix.spinnaker.orca.clouddriver.utils.OortHelper
import com.netflix.spinnaker.orca.pipeline.model.Stage
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import retrofit.RetrofitError

@Slf4j
@Component
class TerminatingInstanceSupport implements CloudProviderAware {

  /**
   * Key for each iteration of a task to check on the progress of termination for each instance. Value is a list
   * of TerminatingInstance objects.
   */
  static final String TERMINATE_REMAINING_INSTANCES = "terminate.remaining.instances"

  @Autowired
  OortHelper oortHelper

  /**
   * Some platforms kill instances within server groups and recreate them with different IDs - for these, we just
   * ensure the instance specified no longer exists.
   * Other platforms  kill instances within server groups and recreate them with the same ID - for these, we can't
   * just check for the absence of the instance alone. Additionally, we must compare the launch times of the new
   * instance with that of the killed one.
   * For instances that are not part of any server group (stand alone instances), we ensure the instance no longer
   * exists and do not expect it to be recreated.
   */
  List<TerminatingInstance> remainingInstances(Stage stage) {
    List<TerminatingInstance> terminatingInstances
    if (stage.context[TERMINATE_REMAINING_INSTANCES]) {
      terminatingInstances = Arrays.asList(stage.mapTo("/" + TERMINATE_REMAINING_INSTANCES, TerminatingInstance[]))
    } else {
      List<String> instanceIds
      // Used by terminateInstances, ensure its not a list of nulls or empty strings
      if (stage.context.containsKey('instanceIds')) {
        instanceIds = (stage.context.instanceIds as List ?: []).findResults { it }
      } else {
        String instanceId = stage.context.instance // Used by terminateInstanceAndDecrementServerGroup.
                                                   // Because consistency is overvalued </sarcasm>.
        if (instanceId) {
          instanceIds = [ instanceId ]
        } else {
          instanceIds = []
        }
      }
      terminatingInstances = instanceIds.collect { new TerminatingInstance(id: it) }
    }

    if (terminatingInstances.isEmpty()) {
      return terminatingInstances
    }
    String serverGroupName = stage.context.serverGroupName ?: stage.context.asgName
    return serverGroupName ?
        getRemainingInstancesFromServerGroup(stage, serverGroupName, terminatingInstances) :
        getRemainingInstancesFromSearch(stage, terminatingInstances)
  }

  List<TerminatingInstance> getRemainingInstancesFromServerGroup(Stage stage, String serverGroupName, List<TerminatingInstance> terminatingInstances) {
    String account = getCredentials(stage)
    String cloudProvider = getCloudProvider(stage)
    String location = stage.context.region

    def tsg = oortHelper.getTargetServerGroup(account, serverGroupName, location, cloudProvider)
    return tsg.map { TargetServerGroup sg ->
      return terminatingInstances.findResults { TerminatingInstance terminatingInstance ->
        def sgInst = sg.instances.find { it.name == terminatingInstance.id }
        if (sgInst) {
          // During the first iteration (most of the time in the Stage portion of the execution), we don't have the
          // launchTime yet. We'll need it later, so it should be saved. If it needs to be saved, that means
          // it's not yet terminated, so it should be included in the returned listed of TerminatingInstances.
          if (terminatingInstance.launchTime) {
            if (sgInst.launchTime <= terminatingInstance.launchTime) {
              return terminatingInstance // instance not yet shutdown.
            } else {
              return null // new launch time, instance must have rebooted.
            }
          }
          return new TerminatingInstance(id: terminatingInstance.id, launchTime: sgInst.launchTime)
        }
        return null // instance not found in clouddriver. Must be terminated.
      }
    }.orElseThrow {
      new IllegalStateException("ServerGroup not found for $cloudProvider/$account/$location/$serverGroupName")
    }
  }

  /**
   * This is a fallback mechanism for terminating instances not part of a server group (aka standalone instances).
   */
  List<TerminatingInstance> getRemainingInstancesFromSearch(Stage stage, List<TerminatingInstance> terminatingInstances) {
    String cloudProvider = getCloudProvider(stage)
    return terminatingInstances.findAll { TerminatingInstance terminatingInstance ->
      List<Map> searchResult
      try {
        searchResult = oortHelper.getSearchResults(terminatingInstance.id, "instances", cloudProvider)
      } catch (RetrofitError e) {
        log.warn e.message
      }
      return !(searchResult?.getAt(0)?.totalMatches == 0)
    }
  }
}
