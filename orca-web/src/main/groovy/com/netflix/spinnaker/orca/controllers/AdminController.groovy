/*
 * Copyright 2017 Google, Inc.
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
 *
 */

package com.netflix.spinnaker.orca.controllers

import com.netflix.config.validation.ValidationException
import com.netflix.spinnaker.kork.exceptions.HasAdditionalAttributes
import com.netflix.spinnaker.orca.commands.ForceExecutionCancellationCommand
import com.netflix.spinnaker.orca.eureka.NoDiscoveryApplicationStatusPublisher
import com.netflix.spinnaker.orca.pipeline.model.Execution
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.ApplicationListener
import org.springframework.context.event.ContextRefreshedEvent
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/admin")
@Slf4j
class AdminController {
  @Autowired(required = false)
  @Qualifier("discoveryStatusPoller")
  ApplicationListener<ContextRefreshedEvent> discoveryStatusPoller

  @Autowired
  ForceExecutionCancellationCommand forceExecutionCancellationCommand

  @RequestMapping(value = "/instance/enabled", method = RequestMethod.POST)
  void setInstanceStatus(@RequestBody Map<String, Boolean> enabledWrapper) {
    if (!discoveryStatusPoller) {
      log.warn("No configured discovery status poller")
      return
    }

    if (!discoveryStatusPoller instanceof  NoDiscoveryApplicationStatusPublisher) {
      throw new DiscoveryUnchangeableException("Discovery status cannot be overwritten", discoveryStatusPoller.class)
    }

    NoDiscoveryApplicationStatusPublisher noDiscoveryApplicationStatusPublisher = (NoDiscoveryApplicationStatusPublisher) discoveryStatusPoller;

    Boolean enabled = enabledWrapper.get("enabled")
    if (enabled == null) {
      throw new ValidationException("The field \"enabled\" must be set", null)
    }

    noDiscoveryApplicationStatusPublisher.setInstanceEnabled(enabled)
  }

  @RequestMapping(value = "/forceCancelExecution", method = RequestMethod.PUT)
  void forceExecutionStatus(@RequestParam(value = "executionId", required = true) String executionId,
                            @RequestParam(value = "executionType", required = false, defaultValue = "PIPELINE") Execution.ExecutionType executionType,
                            @RequestParam(value = "canceledBy", required = false, defaultValue = "admin") String canceledBy)  {
    forceExecutionCancellationCommand.forceCancel(executionType, executionId, canceledBy)
  }

  @ResponseStatus(HttpStatus.BAD_REQUEST)
  private static class DiscoveryUnchangeableException extends IllegalStateException implements HasAdditionalAttributes {
    DiscoveryUnchangeableException(String message, Class discoveryPoller) {
      super(message)
      this.discoveryPoller = discoveryPoller
    }

    private Class discoveryPoller

    Map<String, Object> additionalAttributes = ['discovery': 'Discovery client ' + discoveryPoller.simpleName + ' is managing discovery status']
  }
}
