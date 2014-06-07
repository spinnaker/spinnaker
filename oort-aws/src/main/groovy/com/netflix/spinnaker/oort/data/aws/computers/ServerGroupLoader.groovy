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

package com.netflix.spinnaker.oort.data.aws.computers

import com.amazonaws.services.autoscaling.model.LaunchConfiguration
import com.amazonaws.services.ec2.model.Image
import com.netflix.amazoncomponents.security.AmazonClientProvider
import com.netflix.frigga.Names
import com.netflix.frigga.ami.AppVersion
import com.netflix.spinnaker.oort.data.aws.AmazonDataLoadEvent
import com.netflix.spinnaker.oort.data.aws.Keys
import com.netflix.spinnaker.oort.model.aws.AmazonInstance
import com.netflix.spinnaker.oort.model.aws.AmazonServerGroup
import org.apache.directmemory.cache.CacheService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationListener
import org.springframework.stereotype.Component
import org.apache.log4j.Logger

@Component
class ServerGroupLoader implements ApplicationListener<AmazonDataLoadEvent> {
  private static final Logger log = Logger.getLogger(this)

  @Autowired
  CacheService<String, Object> cacheService

  @Autowired
  AmazonClientProvider amazonClientProvider

  @Override
  void onApplicationEvent(AmazonDataLoadEvent event) {
    def asg = event.autoScalingGroup
    def names = Names.parseName(asg.autoScalingGroupName)
    //log.info "Loading Server Group -- ${names.group}"

    def launchConfig = (LaunchConfiguration) cacheService.map.keySet().findAll { it.startsWith(Keys.getLaunchConfigKey(asg.launchConfigurationName, event.region)) }.collect {
      cacheService.retrieve(it)
    }?.getAt(0)
    def buildInfo = [:]
    def image = null
    if (launchConfig) {
      image = (Image) cacheService.retrieve(Keys.getImageKey(launchConfig.imageId, event.region))
      if (image) {
        def appVersionTag = image.tags.find { it.key == "appversion" }?.value
        if (appVersionTag) {
          def appVersion = AppVersion.parseName(appVersionTag)
          if (appVersion) {
            buildInfo = [package_name: appVersion.packageName, version: appVersion.version, commit: appVersion.commit]
            if (appVersion.buildJobName) {
              buildInfo.jenkins = [name: appVersion.buildJobName, number: appVersion.buildNumber]
            }
            def buildHost = image.tags.find { it.key == "build_host" }?.value
            if (buildHost) {
              buildInfo.jenkins.host = buildHost
            }
          }
        }
      }
    }

    def serverGroup = new AmazonServerGroup(names.group, "aws", event.region)
    serverGroup.launchConfig = launchConfig
    serverGroup.image = image
    serverGroup.buildInfo = buildInfo
    for (instance in asg.instances) {
      serverGroup.instances.add(new AmazonInstance(instance.instanceId))
    }
    if (!cacheService.put(Keys.getServerGroupKey(names.group, event.amazonNamedAccount.name, event.region), serverGroup)) {
      log.info "Out of space!!"
    }
  }

}
