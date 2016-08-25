/*
 * Copyright 2016 Target, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.openstack.client

import com.netflix.spinnaker.clouddriver.openstack.deploy.description.servergroup.ServerGroupParameters
import org.openstack4j.model.heat.Stack

interface OpenstackOrchestrationProvider {

  /**
   * TODO: Handle heat autoscaling migration to senlin in versions > Mitaka
   * Create a Spinnaker Server Group (Openstack Heat Stack).
   * @param region the openstack region
   * @param stackName the openstack stack name
   * @param template the main heat template
   * @param subtemplate a map of subtemplate files references by the template
   * @param parameters the parameters substituted into the heat template
   * @param disableRollback if true, resources are not removed upon stack create failure
   * @param timeoutMins stack create timeout, after which the operation will fail
   * @param tags tags to pass to stack
   */
  void deploy(String region, String stackName, String template, Map<String, String> subtemplate,
              ServerGroupParameters parameters, boolean disableRollback, Long timeoutMins, List<String> tags)

  /**
   * TODO: Handle heat autoscaling migration to senlin in versions > Mitaka
   * Updates a Spinnaker Server Group (Openstack Heat Stack).
   * @param region the openstack region
   * @param stackName the openstack stack name
   * @param stackId the openstack stack id
   * @param template the main heat template
   * @param subtemplate a map of subtemplate files references by the template
   * @param parameters the parameters substituted into the heat template
   * @param tags the tags to pass to the stack. These replace existing tags.
   */
  void updateStack(String region, String stackName, String stackId, String template, Map<String, String> subtemplate,
                   ServerGroupParameters parameters, List<String> tags)

  /**
   * Get a heat template from an existing Openstack Heat Stack
   * @param region
   * @param stackName
   * @param stackId
   * @return
   */
  String getHeatTemplate(String region, String stackName, String stackId)

  /**
   * List existing heat stacks (server groups)
   * @return List < ? extends Stack >  stacks
   */
  List<? extends Stack> listStacks(String region)

  /**
   * List stack associated to these load balancers.
   * @param region
   * @param loadBalancerIds
   * @return
   */
  List<? extends Stack> listStacksWithLoadBalancers(String region, List<String> loadBalancerIds)

  /**
   * Get a stack in a specific region.
   * @param stackName
   * @return
   */
  Stack getStack(String region, String stackName)

  /**
   * Delete a stack in a specific region.
   * @param stack
   */
  void destroy(String region, Stack stack)

  /**
   * Get all instance ids of server resources associated with a stack.
   * @param region
   * @param stackName
   */
  List<String> getInstanceIdsForStack(String region, String stackName)

}
