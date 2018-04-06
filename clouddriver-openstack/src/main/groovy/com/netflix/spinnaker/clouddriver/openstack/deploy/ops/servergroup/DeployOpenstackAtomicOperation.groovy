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

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.netflix.spinnaker.clouddriver.deploy.DeploymentResult
import com.netflix.spinnaker.clouddriver.openstack.client.BlockingStatusChecker
import com.netflix.spinnaker.clouddriver.openstack.client.OpenstackClientProvider
import com.netflix.spinnaker.clouddriver.openstack.deploy.OpenstackServerGroupNameResolver
import com.netflix.spinnaker.clouddriver.openstack.deploy.description.servergroup.DeployOpenstackAtomicOperationDescription
import com.netflix.spinnaker.clouddriver.openstack.deploy.description.servergroup.MemberData
import com.netflix.spinnaker.clouddriver.openstack.deploy.description.servergroup.ServerGroupParameters
import com.netflix.spinnaker.clouddriver.openstack.deploy.description.servergroup.UserDataType
import com.netflix.spinnaker.clouddriver.openstack.deploy.exception.OpenstackOperationException
import com.netflix.spinnaker.clouddriver.openstack.deploy.exception.OpenstackProviderException
import com.netflix.spinnaker.clouddriver.openstack.deploy.exception.OpenstackResourceNotFoundException
import com.netflix.spinnaker.clouddriver.openstack.deploy.ops.StackPoolMemberAware
import com.netflix.spinnaker.clouddriver.openstack.domain.LoadBalancerResolver
import com.netflix.spinnaker.clouddriver.openstack.task.TaskStatusAware
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperations
import org.apache.commons.io.FileUtils
import org.apache.commons.io.IOUtils
import org.openstack4j.model.heat.Stack
import org.openstack4j.model.network.Subnet

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
class DeployOpenstackAtomicOperation implements TaskStatusAware, AtomicOperation<DeploymentResult>, StackPoolMemberAware, LoadBalancerResolver {

  private final String BASE_PHASE = "DEPLOY"

  DeployOpenstackAtomicOperationDescription description

  static final Map<String, String> templateMap = new ConcurrentHashMap<>()

  private ObjectMapper objectMapper = new ObjectMapper(new YAMLFactory())

  DeployOpenstackAtomicOperation(DeployOpenstackAtomicOperationDescription description) {
    this.description = description
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
        "floatingNetworkId: "99bb3aeb-c1e2-4ce5-8d8f-b8e9128af699",
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
        },
        "tags": {
          "foo": "bar",
          "bar": "foo"
        }
      },
      "userDataType": "URL",
      "userData": "http://foobar.com",
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
      task.updateStatus BASE_PHASE, "Initializing creation of server group..."
      OpenstackClientProvider provider = description.credentials.provider

      def serverGroupNameResolver = new OpenstackServerGroupNameResolver(description.credentials, description.region)
      def groupName = serverGroupNameResolver.combineAppStackDetail(description.application, description.stack, description.freeFormDetails)

      task.updateStatus BASE_PHASE, "Looking up next sequence index for cluster ${groupName}..."
      def stackName = serverGroupNameResolver.resolveNextServerGroupName(description.application, description.stack, description.freeFormDetails, false)
      task.updateStatus BASE_PHASE, "Heat stack name chosen to be ${stackName}."

      Map<String, Map> templates = [
        main: objectMapper.readValue(getTemplateFile(ServerGroupConstants.TEMPLATE_FILE), Map)
      ]

      if (description.serverGroupParameters.floatingNetworkId) {
        templates.main.parameters.floating_network_id = [type: "string", description: "Network used to allocate a floating IP for each server."]
        templates.main.resources.servergroup.properties.resource.properties.floating_network_id = [get_param: "floating_network_id"]

      }
      if (description.serverGroupParameters.loadBalancers && !description.serverGroupParameters.loadBalancers.isEmpty()) {
        //look up all load balancer listeners -> pool ids and internal ports
        task.updateStatus BASE_PHASE, "Getting load balancer details for load balancers $description.serverGroupParameters.loadBalancers..."
        List<MemberData> memberDataList = buildMemberData(description.credentials, description.region, description.serverGroupParameters.subnetId, description.serverGroupParameters.loadBalancers, this.&parseListenerKey)
        task.updateStatus BASE_PHASE, "Finished getting load balancer details for load balancers $description.serverGroupParameters.loadBalancers."

        templates[ServerGroupConstants.SUBTEMPLATE_FILE] = objectMapper.readValue(getTemplateFile(ServerGroupConstants.SUBTEMPLATE_FILE), Map)
        //check for floating ip
        if (description.serverGroupParameters.floatingNetworkId) {
          templates[ServerGroupConstants.SUBTEMPLATE_FILE].parameters.floating_network_id = [type: "string", description: "Network used to allocate a floating IP for each server."]
          templates[ServerGroupConstants.SUBTEMPLATE_FILE].resources.server_floating_ip = [
            type: "OS::Neutron::FloatingIP",
            properties: [
              floating_network_id: [get_param: "floating_network_id"],
              port_id: [get_attr: ["server", "addresses", [get_param: "network_id"], 0, "port"]]
            ]
          ]
        }

        task.updateStatus BASE_PHASE, "Loading lbaas subtemplates..."
        if (objectMapper.writeValueAsString(templates[ServerGroupConstants.SUBTEMPLATE_FILE]).contains(ServerGroupConstants.MEMBERTEMPLATE_FILE)) {
          templates[ServerGroupConstants.MEMBERTEMPLATE_FILE] = buildPoolMemberTemplate(memberDataList)
        }
        task.updateStatus BASE_PHASE, "Finished loading lbaas templates."
      } else {
        task.updateStatus BASE_PHASE, "Loading subtemplates..."

        //check for floating ip
        templates[ServerGroupConstants.SUBTEMPLATE_FILE] = objectMapper.readValue(getTemplateFile(ServerGroupConstants.SUBTEMPLATE_SERVER_FILE), Map)
        if (description.serverGroupParameters.floatingNetworkId) {
          templates[ServerGroupConstants.SUBTEMPLATE_FILE].parameters.floating_network_id = [type: "string", description: "Network used to allocate a floating IP for each server."]
          templates[ServerGroupConstants.SUBTEMPLATE_FILE].resources.server_floating_ip = [
            type: "OS::Neutron::FloatingIP",
            properties: [
              floating_network_id: [get_param: "floating_network_id"],
              port_id: [get_attr: ["server", "addresses", [get_param: "network_id"], 0, "port"]]
            ]
          ]
        }
        task.updateStatus BASE_PHASE, "Finished loading templates."
      }

      String subnetId = description.serverGroupParameters.subnetId
      task.updateStatus BASE_PHASE, "Getting network id from subnet $subnetId..."
      Subnet subnet = provider.getSubnet(description.region, subnetId)
      task.updateStatus BASE_PHASE, "Found network id $subnet.networkId from subnet $subnetId."

      String userData = getUserData(provider, stackName)

      if (description.serverGroupParameters.zones) {
        task.updateStatus BASE_PHASE, "Creating zone policy for ${description.serverGroupParameters.zones.size()} zones"
        addZonePlacementPolicy(description.serverGroupParameters.zones, templates.main, templates[ServerGroupConstants.SUBTEMPLATE_FILE])
      }

      task.updateStatus BASE_PHASE, "Creating heat stack $stackName..."
      ServerGroupParameters params = description.serverGroupParameters.identity {
        it.networkId = subnet.networkId
        it.rawUserData = userData
        it.sourceUserDataType = description.userDataType
        it.sourceUserData = description.userData
        it
      }

      def template = objectMapper.writeValueAsString(templates.main)
      //drop the primary template and convert everything to string
      def subtemplates = (Map<String, String>) templates.findAll { it.key != "main"}.collectEntries {k, v -> [(k): objectMapper.writeValueAsString(v)]}

      provider.deploy(description.region, stackName, template, subtemplates, params,
        description.disableRollback, description.timeoutMins, description.serverGroupParameters.loadBalancers)

      task.updateStatus BASE_PHASE, "Waiting on heat stack creation status $stackName..."
      // create a status checker for the stack creation status
      def config = description.credentials.credentials.stackConfig
      StackChecker stackChecker = new StackChecker(StackChecker.Operation.CREATE)
      BlockingStatusChecker statusChecker = BlockingStatusChecker.from(config.pollTimeout, config.pollInterval, stackChecker)
      statusChecker.execute {
        provider.getStack(description.region, stackName)
      }

      task.updateStatus BASE_PHASE, "Finished creating heat stack $stackName."

      task.updateStatus BASE_PHASE, "Successfully created server group."

      deploymentResult.serverGroupNames = ["$description.region:$stackName".toString()] //stupid GString
      deploymentResult.serverGroupNameByRegion = [(description.region): stackName]
    } catch (Exception e) {
      throw new OpenstackOperationException(AtomicOperations.CREATE_SERVER_GROUP, e)
    }
    deploymentResult
  }

  String getUserData(OpenstackClientProvider provider, String serverGroupName) {
    String customUserData = ''
    if (description.userDataType && description.userData) {
      if (UserDataType.fromString(description.userDataType) == UserDataType.URL) {
        task.updateStatus BASE_PHASE, "Resolving user data from url $description.userData..."
        customUserData = description.userData.toURL()?.text
      } else if (UserDataType.fromString(description.userDataType) == UserDataType.SWIFT) {
        String[] parts = description.userData.split(":")
        if (parts?.length == 2) {
          customUserData = provider.readSwiftObject(description.region, parts[0], parts[1])
          if (!customUserData) {
            throw new OpenstackResourceNotFoundException("Failed to read the Swift object ${parts[0]}/${parts[1]} in region ${description.region}")
          }
        }
      } else {
        customUserData = description.userData
      }
    }

    String userData = description.credentials.userDataProvider.getUserData(serverGroupName, description.region, customUserData)
    task.updateStatus BASE_PHASE, "Resolved user data."
    userData
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

  private static void addZonePlacementPolicy(List<String> zones, Map mainTemplate, Map resourceTemplate) {
    def placementList = zones.collect { zone ->
      [
          name: zone,
          weight: 100
      ]
    }
    mainTemplate.resources.zone_policy = [
        type: "OS::Senlin::Policy",
        properties: [
            type: "senlin.policy.zone_placement",
            version: "1.0",
            properties: [
              regions: placementList
            ]
        ]
    ]
    mainTemplate.resources.zone_policy_group = [
        type: "OS::Nova::ServerGroup",
        properties: [
          policies: [
              [
                  get_resource: "zone_policy"
              ]
          ]
        ]
    ]
    resourceTemplate.resources.server.properties.scheduler_hints = [
        group: [
            get_resource: "zone_policy_group"
        ]
    ]
  }
}
