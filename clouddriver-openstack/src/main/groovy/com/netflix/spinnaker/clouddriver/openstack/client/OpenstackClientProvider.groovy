/*
 * Copyright 2016 Target, Inc.
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

package com.netflix.spinnaker.clouddriver.openstack.client

import com.netflix.spinnaker.clouddriver.openstack.deploy.exception.OpenstackOperationException
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperations
import org.openstack4j.api.OSClient
import org.openstack4j.model.common.ActionResponse
import org.openstack4j.model.compute.RebootType
import org.openstack4j.model.heat.Stack

/**
 * Provides access to the Openstack API.
 *
 * TODO tokens will need to be regenerated if they are expired.
 */
abstract class OpenstackClientProvider {

  /**
   * Delete an instance.
   * @param instanceId
   * @return
   */
  void deleteInstance(String instanceId) {
    handleRequest(AtomicOperations.TERMINATE_INSTANCES) {
      client.compute().servers().delete(instanceId)
    }
  }

  /**
   * Reboot an instance ... Default to SOFT reboot if not passed.
   * @param instanceId
   * @return
   */
  void rebootInstance(String instanceId, RebootType rebootType = RebootType.SOFT) {
    handleRequest(AtomicOperations.REBOOT_INSTANCES) {
      client.compute().servers().reboot(instanceId, rebootType)
    }
  }

  /**
   * Create a Spinnaker Server Group (Openstack Heat Stack).
   * @param stackName
   * @param heatTemplate
   * @param parameters
   * @param disableRollback
   * @param timeoutMins
   * @return
   */
  void deploy(String stackName, String heatTemplate, Map<String, String> parameters, boolean disableRollback, Long timeoutMins) {
    try {
      client.heat().stacks().create(stackName, heatTemplate, parameters, disableRollback, timeoutMins)
    } catch (Exception e) {
      throw new OpenstackOperationException(AtomicOperations.CREATE_SERVER_GROUP, e)
    }
    //TODO: Handle heat autoscaling migration to senlin in versions > Mitaka
  }

  /**
   * List existing heat stacks (server groups)
   * @return List<? extends Stack> stacks
   */
  List<? extends Stack> listStacks() {
    def stacks
    try {
      stacks = client.heat().stacks().list()
    } catch (Exception e) {
      throw new OpenstackOperationException(e)
    }
    stacks
  }


  /**
   * Handler for an openstack4j request.
   * @param closure
   * @return
   */
  ActionResponse handleRequest(String operation, Closure closure) {
    ActionResponse result
    try {
      result = closure()
    } catch (Exception e) {
      throw new OpenstackOperationException(operation, e)
    }
    if (!result.isSuccess()) {
      throw new OpenstackOperationException(result, operation)
    }
    result
  }

  /**
   * Thread-safe way to get client.
   * @return
   */
  abstract OSClient getClient()

  /**
   * Get a new token id.
   * @return
   */
  abstract String getTokenId()
}
