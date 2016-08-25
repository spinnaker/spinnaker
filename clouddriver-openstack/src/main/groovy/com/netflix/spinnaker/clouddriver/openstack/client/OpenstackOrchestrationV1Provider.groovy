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

import com.netflix.spinnaker.clouddriver.openstack.deploy.exception.OpenstackProviderException
import com.netflix.spinnaker.clouddriver.openstack.deploy.description.servergroup.ServerGroupParameters
import org.openstack4j.api.Builders
import org.openstack4j.model.heat.Resource
import org.openstack4j.model.heat.Stack
import org.openstack4j.model.heat.StackCreate
import org.openstack4j.model.heat.StackUpdate

class OpenstackOrchestrationV1Provider implements OpenstackOrchestrationProvider, OpenstackRequestHandler, OpenstackIdentityAware {

  OpenstackIdentityProvider identityProvider

  OpenstackOrchestrationV1Provider(OpenstackIdentityProvider identityProvider) {
    this.identityProvider = identityProvider
  }

  @Override
  void deploy(String region, String stackName, String template, Map<String, String> subtemplate,
              ServerGroupParameters parameters, boolean disableRollback, Long timeoutMins, List<String> tags) {
    handleRequest {
      Map<String, String> params = parameters.toParamsMap()
      StackCreate create = Builders.stack()
        .name(stackName)
        .template(template)
        .parameters(params)
        .files(subtemplate)
        .disableRollback(disableRollback)
        .timeoutMins(timeoutMins)
        .tags(tags.join(","))
        .build()
      getRegionClient(region).heat().stacks().create(create)
    }
  }

  @Override
  void updateStack(String region, String stackName, String stackId, String template, Map<String, String> subtemplate,
                   ServerGroupParameters parameters, List<String> tags) {
    handleRequest {
      Map<String, String> params = parameters.toParamsMap()
      StackUpdate update = Builders.stackUpdate()
        .template(template)
        .files(subtemplate)
        .parameters(params)
        .tags(tags ? tags.join(",") : null)
        .build()
      getRegionClient(region).heat().stacks().update(stackName, stackId, update)
    }
  }

  @Override
  String getHeatTemplate(String region, String stackName, String stackId) {
    handleRequest {
      client.useRegion(region).heat().templates().getTemplateAsString(stackName, stackId)
    }
  }

  @Override
  List<? extends Stack> listStacks(String region) {
    handleRequest {
      getRegionClient(region).heat().stacks().list()
    }
  }

  @Override
  List<? extends Stack> listStacksWithLoadBalancers(String region, List<String> loadBalancerIds) {
    handleRequest {
      getRegionClient(region).heat().stacks().list([tags:loadBalancerIds.join(',')])
    }
  }

  @Override
  Stack getStack(String region, String stackName) {
    Stack stack = handleRequest {
      getRegionClient(region).heat().stacks().getStackByName(stackName)
    }
    if (!stack) {
      throw new OpenstackProviderException("Unable to find stack $stackName in region $region")
    }
    stack
  }

  @Override
  void destroy(String region, Stack stack) {
    handleRequest {
      getRegionClient(region).heat().stacks().delete(stack.name, stack.id)
    }
  }

  @Override
  List<String> getInstanceIdsForStack(String region, String stackName) {
    List<? extends Resource> resources = handleRequest {
      getRegionClient(region).heat().resources().list(stackName)
    }
    List<String> ids = resources.findResults {
      it.type == "OS::Nova::Server" ? it.physicalResourceId : null
    }
    ids
  }

}
