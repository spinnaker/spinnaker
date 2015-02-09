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

package com.netflix.spinnaker.orca.notifications
import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.appinfo.InstanceInfo
import com.netflix.discovery.DiscoveryClient
import com.netflix.spinnaker.orca.pipeline.PipelineStarter
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
/**
 * An abstract class that can be extended to implement the {@code NotificationHandler} functionality. It has the basic
 * pipeline orchestration stuff wired in. The {@code NotificationHandler}'s are used by the classes that extend {@AbstractPollingNotificationAgent}
 */
abstract class AbstractNotificationHandler implements NotificationHandler {
  private final Logger log = LoggerFactory.getLogger(getClass());

  @Autowired
  PipelineStarter pipelineStarter

  //TODO(cfieber) we aren't currently injecting a full discovery client in kork-core
  @Autowired(required = false)
  DiscoveryClient discoveryClient

  @Autowired
  ObjectMapper objectMapper

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
    if (discoveryClient == null) {
      log.info("no DiscoveryClient, assuming InService")
      true
    } else {
      def remoteStatus = discoveryClient.instanceRemoteStatus
      log.info("current remote status ${remoteStatus}")
      remoteStatus == InstanceInfo.InstanceStatus.UP
    }
  }
}