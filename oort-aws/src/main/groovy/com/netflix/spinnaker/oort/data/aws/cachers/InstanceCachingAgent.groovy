/*
 * Copyright 2014 Netflix, Inc.
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

package com.netflix.spinnaker.oort.data.aws.cachers

import com.amazonaws.services.ec2.model.Instance
import com.amazonaws.services.ec2.model.InstanceState
import com.amazonaws.services.ec2.model.InstanceStateName
import com.netflix.spinnaker.amos.aws.NetflixAmazonCredentials
import com.netflix.spinnaker.oort.data.aws.Keys
import groovy.transform.CompileStatic

import static com.netflix.spinnaker.oort.ext.MapExtensions.specialSubtract

@CompileStatic
class InstanceCachingAgent extends AbstractInfrastructureCachingAgent {

  static enum InstanceStateValue {

    Pending(0, InstanceStateName.Pending),
    Running(16, InstanceStateName.Running),
    ShuttingDown(32, InstanceStateName.ShuttingDown),
    Terminated(48, InstanceStateName.Terminated),
    Stopping(64, InstanceStateName.Stopping),
    Stopped(80, InstanceStateName.Stopped)

    static final Set<InstanceStateValue> MISSING = Collections.unmodifiableSet([ShuttingDown, Terminated] as Set)

    final int code
    final InstanceStateName name

    private InstanceStateValue(int code, InstanceStateName name) {
      this.code = code
      this.name = name
    }

    InstanceState buildInstanceState() {
      new InstanceState().withCode(code).withName(name)
    }

    static InstanceStateValue fromInstanceState(InstanceState instanceState) {
      InstanceStateValue value = null

      if (instanceState.code != null) {
        value = values().find { it.code == instanceState.code }
      } else if (instanceState.name != null) {
        value = values().find { it.name == instanceState.name }
      }

      if (!value) {
        throw new IllegalArgumentException("unknown InstanceState")
      }

      value
    }
  }
  static final String ASG_TAG_NAME = "aws:autoscaling:groupName"

  InstanceCachingAgent(NetflixAmazonCredentials account, String region) {
    super(account, region)
  }

  private Map<String, Integer> lastKnownInstances = [:]

  void load() {
    log.info "$cachePrefix - Beginning Instance Cache Load."

    def amazonEC2 = amazonClientProvider.getAmazonEC2(account, region)
    def instances = amazonEC2.describeInstances()
    def allInstances = ((List<Instance>)instances.reservations.collectMany { it.instances ?: [] }).collectEntries { Instance instance -> [(instance.instanceId): instance]}
    Map<String, Integer> instancesThisRun = (Map<String, Integer>)allInstances.collectEntries { instanceId, instance -> [(instanceId): instance.hashCode()] }
    Map<String, Integer> newInstances = specialSubtract(instancesThisRun, lastKnownInstances)
    Set<String> missingInstances = new HashSet<String>(lastKnownInstances.keySet())
    missingInstances.removeAll(instancesThisRun.keySet())

    if (newInstances) {
      log.info "$cachePrefix - Loading ${newInstances.size()} new or changes instances."
      for (instanceId in newInstances.keySet()) {
        Instance instance = (Instance)allInstances[instanceId]
        if (InstanceStateValue.MISSING.contains(InstanceStateValue.fromInstanceState(instance.state))) {
          missingInstances << instance.instanceId
        } else {
          loadNewInstance(account, instance, region)
        }
      }
    }
    if (missingInstances) {
      log.info "$cachePrefix - Removing ${missingInstances.size()} missing or terminated instances."
      for (instanceId in missingInstances) {
        def instance = (Instance)allInstances[instanceId]
        removeMissingInstance(account, instance, region)
      }
    }
    if (!newInstances && !missingInstances) {
      log.info "$cachePrefix - Nothing to process"
    }

    lastKnownInstances = instancesThisRun
  }

  void loadNewInstance(NetflixAmazonCredentials account, Instance instance, String region) {
    cacheService.put(Keys.getInstanceKey(instance.instanceId, region), instance)
    def serverGroup = getServerGroupName(instance)
    if (serverGroup) {
      def sgInstanceKey = Keys.getServerGroupInstanceKey(serverGroup, instance.instanceId, account.name, region)
      // This is just a ref
      cacheService.put(sgInstanceKey, [:])
    }
  }

  void removeMissingInstance(NetflixAmazonCredentials account, Instance instance, String region) {
    if (instance) {
      cacheService.free(Keys.getInstanceKey(instance.instanceId, region))
      def serverGroup = getServerGroupName(instance)
      if (serverGroup) {
        def sgInstanceKey = Keys.getServerGroupInstanceKey(serverGroup, instance.instanceId, account.name, region)
        cacheService.free(sgInstanceKey)
      }
    }
  }

  private static String getServerGroupName(Instance instance) {
    instance.tags.find { it.key == ASG_TAG_NAME }?.value
  }

  private String getCachePrefix() {
    "[caching:$region:${account.name}:ins]"
  }
}
