/*
 * Copyright 2016 The original authors.
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

package com.netflix.spinnaker.clouddriver.openstack.deploy.ops.servergroup

import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.deploy.DeploymentResult
import com.netflix.spinnaker.clouddriver.openstack.client.OpenstackClientProvider
import com.netflix.spinnaker.clouddriver.openstack.deploy.OpenstackServerGroupNameResolver
import com.netflix.spinnaker.clouddriver.openstack.deploy.description.servergroup.DeployOpenstackAtomicOperationDescription
import com.netflix.spinnaker.clouddriver.openstack.deploy.description.servergroup.MemberData
import com.netflix.spinnaker.clouddriver.openstack.deploy.exception.OpenstackOperationException
import com.netflix.spinnaker.clouddriver.openstack.deploy.ops.StackPoolMemberAware
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperations
import org.apache.commons.io.FileUtils
import org.apache.commons.io.IOUtils
import org.openstack4j.model.network.Subnet
import org.openstack4j.model.network.ext.ListenerV2
import org.openstack4j.model.network.ext.LoadBalancerV2

import java.util.concurrent.ConcurrentHashMap

/**
 * For now, we want to provide 'the standard' way of being able to configure an autoscaling group in much the same way
 * as it is done with other providers, albeit with the hardcoded templates.
 * Later on we should consider adding in the feature to provide custom templates.
 *
 * Overriding the default via configuration is a good idea, as long as people do their diligence to honor
 * the properties that the template can expect to be given to it. The Openstack API is finicky when properties
 * are provided but not used, and doesn't work at all when properties are not provided but expected.
 *
 * Being able to pass in the template via free-form text is also a good idea,
 * but again it would need to honor the expected parameters.
 * We could use the freeform details field to store the template string.
 */
class DeployOpenstackAtomicOperation implements AtomicOperation<DeploymentResult>, StackPoolMemberAware {

  private final String BASE_PHASE = "DEPLOY"

  DeployOpenstackAtomicOperationDescription description

  static final Map<String, String> templateMap = new ConcurrentHashMap<>()

  DeployOpenstackAtomicOperation(DeployOpenstackAtomicOperationDescription description) {
    this.description = description
  }

  protected static Task getTask() {
    TaskRepository.threadLocalTask.get()
  }

  /*
   curl -X POST -H "Content-Type: application/json" -d '[{
    "createServerGroup": {
      "stack": "teststack",
      "application": "myapp",
      "serverGroupParameters": {
        "instanceType": "m1.medium",
        "image": "4e0d0b4b-8089-4703-af99-b6a0c90fbbc7",
        "maxSize": 5,
        "minSize": 3,
        "desiredSize": 4,
        "subnetId": "77bb3aeb-c1e2-4ce5-8d8f-b8e9128af651",
        "loadBalancers": ["87077f97-83e7-4ea1-9ca9-40dc691846db"],
        "securityGroups": ["e56fa7eb-550d-42d4-8d3f-f658fbacd496"],
        "scaleup": {
          "cooldown": 60,
          "adjustment": 1,
          "period": 60,
          "threshold": 50
        },
        "scaledown": {
          "cooldown": 60,
          "adjustment": -1,
          "period": 600,
          "threshold": 15
        }
      },
      "region": "REGION1",
      "disableRollback": false,
      "timeoutMins": 5,
      "account": "test"
    }
  }]' localhost:7002/openstack/ops
  */
  @Override
  DeploymentResult operate(List priorOutputs) {
    DeploymentResult deploymentResult = new DeploymentResult()
    try {
      task.updateStatus BASE_PHASE, "Initializing creation of server group"
      OpenstackClientProvider provider = description.credentials.provider

      def serverGroupNameResolver = new OpenstackServerGroupNameResolver(description.credentials, description.region)
      def groupName = serverGroupNameResolver.combineAppStackDetail(description.application, description.stack, description.freeFormDetails)

      task.updateStatus BASE_PHASE, "Looking up next sequence index for cluster ${groupName}..."
      def stackName = serverGroupNameResolver.resolveNextServerGroupName(description.application, description.stack, description.freeFormDetails, false)
      task.updateStatus BASE_PHASE, "Heat stack name chosen to be ${stackName}."

      //look up all load balancer listeners -> pool ids and internal ports
      task.updateStatus BASE_PHASE, "Getting load balancer details for load balancers $description.serverGroupParameters.loadBalancers"
      List<MemberData> memberDataList = description.serverGroupParameters.loadBalancers.collectMany { loadBalancerId ->
        task.updateStatus BASE_PHASE, "Looking up load balancer details for load balancer $loadBalancerId"
        LoadBalancerV2 loadBalancer = provider.getLoadBalancer(description.region, loadBalancerId)
        task.updateStatus BASE_PHASE, "Found load balancer details for load balancer $loadBalancerId"
        loadBalancer.listeners.collect { item ->
          task.updateStatus BASE_PHASE, "Looking up load balancer listener details for listener $item.id"
          ListenerV2 listener = provider.getListener(description.region, item.id)
          String internalPort = parseListenerKey(listener.description).internalPort
          String poolId = listener.defaultPoolId
          task.updateStatus BASE_PHASE, "Found load balancer listener details (poolId=$poolId, internalPort=$internalPort) for listener $item.id"
          new MemberData(subnetId: description.serverGroupParameters.subnetId, internalPort: internalPort, poolId: poolId)
        }
      }
      task.updateStatus BASE_PHASE, "Finished getting load balancer details for load balancers $description.serverGroupParameters.loadBalancers"

      task.updateStatus BASE_PHASE, "Loading templates"
      String template = getTemplateFile(ServerGroupConstants.TEMPLATE_FILE)
      Map<String, String> subtemplates = [:]
      if (template.contains(ServerGroupConstants.SUBTEMPLATE_FILE)) {
        String subtemplate = getTemplateFile(ServerGroupConstants.SUBTEMPLATE_FILE)
        subtemplates << [(ServerGroupConstants.SUBTEMPLATE_FILE): subtemplate]
        if (subtemplate.contains(ServerGroupConstants.MEMBERTEMPLATE_FILE)) {
          subtemplates << [(ServerGroupConstants.MEMBERTEMPLATE_FILE): buildPoolMemberTemplate(memberDataList)]
        }
      }
      task.updateStatus BASE_PHASE, "Finished loading templates"

      String subnetId = description.serverGroupParameters.subnetId
      task.updateStatus BASE_PHASE, "Getting network id from subnet $subnetId"
      Subnet subnet = provider.getSubnet(description.region, subnetId)
      task.updateStatus BASE_PHASE, "Found network id $subnet.networkId from subnet $subnetId"

      task.updateStatus BASE_PHASE, "Creating heat stack $stackName"
      provider.deploy(description.region, stackName, template, subtemplates, description.serverGroupParameters.identity {
        networkId = subnet.networkId
        it
      }, description.disableRollback, description.timeoutMins)
      task.updateStatus BASE_PHASE, "Finished creating heat stack $stackName"

      task.updateStatus BASE_PHASE, "Successfully created server group."

      deploymentResult.serverGroupNames = ["$description.region:$stackName".toString()] //stupid GString
      deploymentResult.serverGroupNameByRegion = [(description.region): stackName]
    } catch (Exception e) {
      throw new OpenstackOperationException(AtomicOperations.CREATE_SERVER_GROUP, e)
    }
    deploymentResult
  }

  /**
   * TODO this will move once the lbaasv2 operation is done
   * Generate key in the format externalProtocol:externalPort:internalProtocol:internalPort
   * @param port
   * @return
   */
  String getListenerKey(int externalPort, String externalProtocol, int internalPort, String internalProtocol) {
    "${externalProtocol}:${externalPort}:${internalProtocol}:${internalPort}"
  }

  /**
   * TODO this will move once the lbaasv2 operation is done
   * Parse the listener attributes from the key.
   * @param key
   * @return
   */
  Map<String, String> parseListenerKey(String key) {
    Map<String, String> result = [:]
    String[] parts = key.split(':')
    if (parts.length == 4) {
      result << [externalProtocol: parts[0], externalPort: parts[1], internalProtocol: parts[2], internalPort: parts[3]]
    }
    result
  }

  /**
   * Return the file contents of a template, either from the account config location or from the classpath.
   * @param filename
   * @return
   */
  String getTemplateFile(String filename) {
    Optional.ofNullable(templateMap.get(filename)).orElseGet {
      String template
      String tmplDir = description.credentials.credentials.heatTemplateLocation
      if (tmplDir && new File("$tmplDir/${filename}").exists()) {
        template = FileUtils.readFileToString(new File("$tmplDir/${filename}"))
      } else {
        template = IOUtils.toString(this.class.classLoader.getResourceAsStream(filename))
      }
      templateMap.put(filename, template)
      template ?: ""
    }
  }

}
