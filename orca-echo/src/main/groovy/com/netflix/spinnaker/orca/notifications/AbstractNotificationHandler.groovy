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

package com.netflix.spinnaker.orca.notifications

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.appinfo.ApplicationInfoManager
import com.netflix.appinfo.InstanceInfo
import com.netflix.spinnaker.orca.mayo.services.PipelineConfigurationService
import com.netflix.spinnaker.orca.pipeline.PipelineStarter
import org.springframework.beans.factory.annotation.Autowired

abstract class AbstractNotificationHandler implements NotificationHandler {

  @Autowired
  PipelineStarter pipelineStarter

  @Autowired
  PipelineConfigurationService pipelineConfigurationService

  @Autowired
  ObjectMapper objectMapper

  @Autowired
  ApplicationInfoManager applicationInfoManager

  abstract String getHandlerType()

  abstract void handleInternal(Map input)

  boolean handles(String type) {
    type == handlerType
  }

  void handle(Map input) {
    if (inService) {
      handleInternal(input)
    }
  }

  boolean isInService() {
    def info = applicationInfoManager.info
    info && info.status == InstanceInfo.InstanceStatus.UP
  }
}
