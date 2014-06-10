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
import com.netflix.spinnaker.oort.data.aws.Keys
import com.netflix.spinnaker.oort.security.aws.AmazonNamedAccount
import groovy.transform.Canonical
import groovy.transform.CompileStatic
import reactor.event.Event

import static reactor.event.selector.Selectors.object

@CompileStatic
class InstanceCachingAgent extends AbstractInfrastructureCachingAgent {
  static final Integer TERMINATED = 48
  static final Integer SHUTDOWN = 32
  static final String ASG_TAG_NAME = "aws:autoscaling:groupName"

  InstanceCachingAgent(AmazonNamedAccount account, String region) {
    super(account, region)
  }

  private Map<String, Integer> lastKnownInstances = [:]

  void load() {
    log.info "$cachePrefix - Beginning Instance Cache Load."

    reactor.on(object("newInstance"), this.&loadNewInstance)
    reactor.on(object("missingInstance"), this.&removeMissingInstance)

    def amazonEC2 = amazonClientProvider.getAmazonEC2(account.credentials, region)
    def instances = amazonEC2.describeInstances()
    def allInstances = ((List<Instance>)instances.reservations.collectMany { it.instances ?: [] }).collectEntries { Instance instance -> [(instance.instanceId): instance]}
    Map<String, Integer> instancesThisRun = (Map<String, Integer>)allInstances.collectEntries { instanceId, instance -> [(instanceId): instance.hashCode()] }
    def newInstances = instancesThisRun.specialSubtract(lastKnownInstances)
    def missingInstances = lastKnownInstances.keySet() - instancesThisRun.keySet()

    if (newInstances) {
      log.info "$cachePrefix - Loading ${newInstances.size()} new or changes instances."
      for (instanceId in newInstances.keySet()) {
        Instance instance = (Instance)allInstances[instanceId]
        if (instance.state.code == TERMINATED || instance.state.code == SHUTDOWN) {
          missingInstances << instance.instanceId
        } else {
          reactor.notify("newInstance", Event.wrap(new InstanceNotification(account, instance, region)))
        }
      }
    }
    if (missingInstances) {
      log.info "$cachePrefix - Removing ${missingInstances.size()} missing or terminated instances."
      for (instanceId in missingInstances) {
        def instance = (Instance)allInstances[instanceId]
        reactor.notify("missingInstance", Event.wrap(new InstanceNotification(account, instance, region)))
      }
    }
    if (!newInstances && !missingInstances) {
      log.info "$cachePrefix - Nothing to process"
    }

    lastKnownInstances = instancesThisRun
  }

  @Canonical
  static class InstanceNotification {
    AmazonNamedAccount account
    Instance instance
    String region
  }

  void loadNewInstance(Event<InstanceNotification> event) {
    def account = event.data.account
    def instance = event.data.instance
    def region = event.data.region
    cacheService.put(Keys.getInstanceKey(instance.instanceId, region), instance)
    def serverGroup = getServerGroupName(instance)
    if (serverGroup) {
      def sgInstanceKey = Keys.getServerGroupInstanceKey(serverGroup, instance.instanceId, account.name, region)
      // This is just a ref
      cacheService.put(sgInstanceKey, [:])
    }
  }

  void removeMissingInstance(Event<InstanceNotification> event) {
    def account = event.data.account
    Instance instance = event.data.instance
    if (instance) {
      cacheService.free(Keys.getInstanceKey(instance.instanceId, event.data.region))
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
