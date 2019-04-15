/*
 * Copyright 2016 Google, Inc.
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
 */

package com.netflix.spinnaker.clouddriver.google.deploy.ops

import com.google.api.services.compute.model.*
import com.netflix.spinnaker.clouddriver.google.deploy.GCEUtil
import com.netflix.spinnaker.clouddriver.google.deploy.description.snapshot.SaveSnapshotDescription
import com.netflix.spinnaker.clouddriver.google.deploy.exception.GoogleResourceIllegalStateException
import com.netflix.spinnaker.clouddriver.google.deploy.ops.snapshot.SaveSnapshotAtomicOperation
import com.netflix.spinnaker.clouddriver.google.model.GoogleHealthCheck
import com.netflix.spinnaker.clouddriver.google.model.GoogleSecurityGroup
import com.netflix.spinnaker.clouddriver.google.model.GoogleServerGroup
import com.netflix.spinnaker.clouddriver.google.model.loadbalancing.GoogleNetworkLoadBalancer
import com.netflix.spinnaker.clouddriver.model.securitygroups.IpRangeRule
import com.netflix.spinnaker.clouddriver.model.securitygroups.Rule
import spock.lang.Specification
import spock.lang.Subject

class SerializeApplicationAtomicOperationUnitSpec extends Specification {

  private static final SERVER_GROUP_NAME = "spinnaker-test-v000"
  private static final SERVER_GROUP_ZONE = "us-east1-b"
  private static final SERVER_GROUP_LOAD_BALANCERS = ["test_load_balancer"]

  private static final INSTANCE_TEMPLATE_NAME = "spinnaker-test-v000-template"
  private static final INSTANCE_TEMPLATE_MACHINE_TYPE = "f1-micro"
  private static final INSTANCE_TEMPLATE_CAN_IP_FORWARD = false
  private static final INSTANCE_TEMPLATE_DESCRIPTION = "description"
  private static final INSTANCE_TEMPLATE_INSTANCE_DESCRIPTION = "instance description"
  private static final INSTANCE_TEMPLATE_TAGS = ["test_tag"]
  private static final INSTANCE_TEMPLATE_METADATA = new Metadata(items: [new Metadata.Items(key: "test", value: "data")])

  private static final SCHEDULING_AUTOMATIC_RESTART = true
  private static final SCHEDULING_ON_HOST_MAINTENANCE = "MIGRATE"
  private static final SCHEDULING_PREEMPTIBLE = false

  private static final SHIELDEDVMCONFIG_ENABLE_SECURE_BOOT = false
  private static final SHIELDEDVMCONFIG_ENABLE_VTPM = false
  private static final SHIELDEDVMCONFIG_ENABLE_INTEGRITY_MONITORING = false

  private static final DISK_AUTO_DELETE = true
  private static final DISK_BOOT = false
  private static final DISK_DEVICE_NAME = "test_device_name"
  private static final DISK_NAME = "spinnaker-test-disk"
  private static final DISK_SOURCE_IMAGE = "ubuntu-1404-trusty-v20160610"
  private static final DISK_INTERFACE = "SCSI"
  private static final DISK_TYPE_OF_DISK = "PERSISTENT"
  private static final DISK_TYPE = "pd-ssd"
  private static final DISK_MODE = "READ_WRITE"
  private static final DISK_SIZE_GB = 100
  private static final DISK_SOURCE = "https://pantheon.corp.google.com/compute/disksDetail/zones/us-central1-f/disks/spinnaker-test-disk"

  private static final NETWORK_URL = "https://compute.googleapis.com/compute/v1/projects/test-proj/networking/networks/details/default"
  private static final SUBNETWORK_URL = "https://compute.googleapis.com/compute/v1/projects/test-proj/networking/subnetworks/details/us-central1/default"
  private static final NETWORK_ACCESS_CONFIG = []

  private static final AUTOSCALING_MAX_NUM_REPLICAS = 6
  private static final AUTOSCALING_MIN_NUM_REPLICAS = 3
  private static final AUTOSCALING_COOL_DOWN_PERIOD = 20
  private static final AUTOSCALING_CPU_TARGET = 10.0
  private static final AUTOSCALING_LOAD_BALANCER_TARGET = 25.0
  private static final AUTOSCALING_METRIC_NAME = "agent.googleapis.com/apache/connections"
  private static final AUTOSCALING_METRIC_TARGET = 5.0
  private static final AUTOSCALING_METRIC_TYPE = "GAUGE"

  private static final LOAD_BALANCER_NAME = "spinnaker_load_balancer"
  private static final LOAD_BALANCER_IP_PROTOCOL = "TCP"
  private static final LOAD_BALANCER_PORT_RANGE = "8080-8080"
  private static final LOAD_BALANCER_REGION = "us-east1"
  private static final LOAD_BALANCER_TARGET_POOL = "https://compute.googleapis.com/compute/v1/projects/test-proj/regions/us-central1/targetPools/spinnaker-load_balancer-tp"

  private static final HEALTH_CHECK_NAME = "spinnaker-load-balancer-hc"
  private static final HEALTH_CHECK_INTERVAL = 15
  private static final HEALTH_CHECK_HEALTHY_THRESHOLD = 100
  private static final HEALTH_CHECK_PORT = 8080
  private static final HEALTH_CHECK_REQUEST_PATH = "hello"
  private static final HEALTH_CHECK_TIMEOUT = 100
  private static final HEALTH_CHECK_UNHEALTHY_THRESHOLD = 20

  private static final SECURITY_GROUP_RULE_PROTOCOL = "TCP"
  private static final SECURITY_GROUP_RULE_PORT = 8080
  private static final SECURITY_GROUP_NAME = "spinnaker-test"
  private static final SECURITY_GROUP_NETWORK = "default"
  private static final SECURITY_GROUP_TARGET_TAGS = ["test_tag"]

  void "should serialize a server group"() {
    setup:
      def resourceMap = [:]

      // Create a server group
      def scheduling = new Scheduling(automaticRestart: SCHEDULING_AUTOMATIC_RESTART,
                                      onHostMaintenance: SCHEDULING_ON_HOST_MAINTENANCE,
                                      preemptible: SCHEDULING_PREEMPTIBLE)
      def shieldedVmConfig = new ShieldedVmConfig(enableSecureBoot: SHIELDEDVMCONFIG_ENABLE_SECURE_BOOT,
                                                  enableVtpm: SHIELDEDVMCONFIG_ENABLE_VTPM,
                                                  enableIntegrityMonitoring: SHIELDEDVMCONFIG_ENABLE_INTEGRITY_MONITORING)
      def disk = new AttachedDisk(autoDelete: DISK_AUTO_DELETE,
                                  boot: DISK_BOOT,
                                  deviceName: DISK_DEVICE_NAME,
                                  initializeParams: new AttachedDiskInitializeParams(diskName: DISK_NAME,
                                                                                    sourceImage: DISK_SOURCE_IMAGE,
                                                                                    diskSizeGb: DISK_SIZE_GB,
                                                                                    diskType: DISK_TYPE),
                                  source: DISK_SOURCE,
                                  interface: DISK_INTERFACE,
                                  type: DISK_TYPE_OF_DISK,
                                  mode: DISK_MODE)
      def networkInterface = new NetworkInterface(network: NETWORK_URL,
                                                  subnetwork: SUBNETWORK_URL,
                                                  accessConfigs: NETWORK_ACCESS_CONFIG)
      def instanceProperties = new InstanceProperties(machineType: INSTANCE_TEMPLATE_MACHINE_TYPE,
                                                      canIpForward: INSTANCE_TEMPLATE_CAN_IP_FORWARD,
                                                      description: INSTANCE_TEMPLATE_INSTANCE_DESCRIPTION,
                                                      tags: new Tags(items: INSTANCE_TEMPLATE_TAGS),
                                                      metadata: INSTANCE_TEMPLATE_METADATA,
                                                      scheduling: scheduling,
                                                      disks: [disk],
                                                      networkInterfaces: [networkInterface],
                                                      shieldedVmConfig: shieldedVmConfig)
      def instanceTemplate = new InstanceTemplate(description: INSTANCE_TEMPLATE_DESCRIPTION,
                                                  name: INSTANCE_TEMPLATE_NAME,
                                                  properties: instanceProperties)
      def autoscalingPolicy = new AutoscalingPolicy(maxNumReplicas: AUTOSCALING_MAX_NUM_REPLICAS,
                                                    minNumReplicas: AUTOSCALING_MIN_NUM_REPLICAS,
                                                    coolDownPeriodSec: AUTOSCALING_COOL_DOWN_PERIOD,
                                                    cpuUtilization: new AutoscalingPolicyCpuUtilization(utilizationTarget: AUTOSCALING_CPU_TARGET),
                                                    loadBalancingUtilization: new AutoscalingPolicyLoadBalancingUtilization(utilizationTarget: AUTOSCALING_LOAD_BALANCER_TARGET),
                                                    customMetricUtilizations: [new AutoscalingPolicyCustomMetricUtilization(metric: AUTOSCALING_METRIC_NAME,
                                                                                                                            utilizationTarget: AUTOSCALING_METRIC_TARGET,
                                                                                                                            utilizationTargetType: AUTOSCALING_METRIC_TYPE)])
      def serverGroup = new GoogleServerGroup(name: SERVER_GROUP_NAME,
                                              zone: SERVER_GROUP_ZONE,
                                              asg: [(GCEUtil.REGIONAL_LOAD_BALANCER_NAMES): SERVER_GROUP_LOAD_BALANCERS],
                                              launchConfig: ["instanceTemplate": instanceTemplate],
                                              autoscalingPolicy: autoscalingPolicy)

      // Create sever group map
      def diskMap = [auto_delete: DISK_AUTO_DELETE,
                    boot: DISK_BOOT,
                    device_name: DISK_DEVICE_NAME,
                    name: DISK_NAME,
                    source_image: DISK_SOURCE_IMAGE,
                    source: DISK_SOURCE,
                    interface: DISK_INTERFACE,
                    mode: DISK_MODE,
                    disk_type: DISK_TYPE,
                    disk_size_gb: DISK_SIZE_GB,
                    type: DISK_TYPE_OF_DISK]

      def networkInterfaceMap = [network: NETWORK_URL.split("/").last(),
                                 subnetwork: SUBNETWORK_URL.split("/").last(),
                                 access_config: [[nat_ip: ""]]]
      def schedulingMap = [automatic_restart: SCHEDULING_AUTOMATIC_RESTART,
                           on_host_maintenance: SCHEDULING_ON_HOST_MAINTENANCE,
                           preemptible: SCHEDULING_PREEMPTIBLE]
      def shieldedVmConfigMap = [enable_secure_boot: SHIELDEDVMCONFIG_ENABLE_SECURE_BOOT,
                                  enable_vtpm: SHIELDEDVMCONFIG_ENABLE_VTPM,
                                  enable_integrity_monitoring: SHIELDEDVMCONFIG_ENABLE_INTEGRITY_MONITORING]
      def autoscalingPolicyMap = [max_replicas: AUTOSCALING_MAX_NUM_REPLICAS,
                                  min_replicas: AUTOSCALING_MIN_NUM_REPLICAS,
                                  cooldown_period: AUTOSCALING_COOL_DOWN_PERIOD,
                                  cpu_utilization: [target: AUTOSCALING_CPU_TARGET],
                                  metric: [[name: AUTOSCALING_METRIC_NAME,
                                           target: AUTOSCALING_METRIC_TARGET,
                                           type: AUTOSCALING_METRIC_TYPE.toLowerCase()]],
                                  load_balancing_utilization: [target: AUTOSCALING_LOAD_BALANCER_TARGET]]
      def autoscalingMap = [name: SERVER_GROUP_NAME,
                            target: "\${google_compute_instance_group_manager.${SERVER_GROUP_NAME}.self_link}",
                            zone: SERVER_GROUP_ZONE,
                            autoscaling_policy: autoscalingPolicyMap,
                            project: null]
      def metadataMap = [:]
      INSTANCE_TEMPLATE_METADATA.items.each {Metadata.Items item ->
        metadataMap[item.key] = item.value
      }
      def instanceTemplateMap = [name: INSTANCE_TEMPLATE_NAME,
                                 machine_type: INSTANCE_TEMPLATE_MACHINE_TYPE,
                                 can_ip_forward: INSTANCE_TEMPLATE_CAN_IP_FORWARD,
                                 description: INSTANCE_TEMPLATE_DESCRIPTION,
                                 instance_description: INSTANCE_TEMPLATE_INSTANCE_DESCRIPTION,
                                 tags: INSTANCE_TEMPLATE_TAGS,
                                 disk: [diskMap],
                                 project: null,
                                 network_interface: [networkInterfaceMap],
                                 scheduling: schedulingMap,
                                 metadata: metadataMap,
                                 shielded_vm_config: shieldedVmConfigMap]
      def targetPools = []
      SERVER_GROUP_LOAD_BALANCERS.each {String loadBalancer ->
        targetPools.add("\${google_compute_target_pool.${loadBalancer}.self_link}")
      }
      def serverGroupMap = [name: SERVER_GROUP_NAME,
                            base_instance_name: SERVER_GROUP_NAME,
                            project: null,
                            zone: SERVER_GROUP_ZONE,
                            instance_template: "\${google_compute_instance_template.${INSTANCE_TEMPLATE_NAME}.self_link}",
                            target_pools: targetPools]

      @Subject def operation = new SaveSnapshotAtomicOperation(new SaveSnapshotDescription())

    when:
      operation.initializeResourceMap(resourceMap)
      operation.addServerGroupToResourceMap(serverGroup.view, resourceMap)

    then:
      resourceMap.google_compute_instance_group_manager[SERVER_GROUP_NAME] == serverGroupMap
      resourceMap.google_compute_instance_template[INSTANCE_TEMPLATE_NAME] == instanceTemplateMap
      resourceMap.google_compute_autoscaler[SERVER_GROUP_NAME] == autoscalingMap

  }

  void "should serialize a load balancer"() {
    setup:
      def resourceMap = [:]

      // Create a load balancer
      def healthCheck = new GoogleHealthCheck(name: HEALTH_CHECK_NAME,
                                              checkIntervalSec: HEALTH_CHECK_INTERVAL,
                                              healthyThreshold: HEALTH_CHECK_HEALTHY_THRESHOLD,
                                              port: HEALTH_CHECK_PORT,
                                              requestPath: HEALTH_CHECK_REQUEST_PATH,
                                              timeoutSec: HEALTH_CHECK_TIMEOUT,
                                              unhealthyThreshold: HEALTH_CHECK_UNHEALTHY_THRESHOLD)
      def loadBalancer = new GoogleNetworkLoadBalancer(name: LOAD_BALANCER_NAME,
                                                       ipProtocol: LOAD_BALANCER_IP_PROTOCOL,
                                                       portRange: LOAD_BALANCER_PORT_RANGE,
                                                       region: LOAD_BALANCER_REGION,
                                                       targetPool: LOAD_BALANCER_TARGET_POOL,
                                                       healthCheck: healthCheck)
      // Create a load balancer map

      def forwardingRuleMap = [name: LOAD_BALANCER_NAME,
                               ip_protocol: LOAD_BALANCER_IP_PROTOCOL,
                               port_range: LOAD_BALANCER_PORT_RANGE,
                               region: LOAD_BALANCER_REGION,
                               project: null,
                               target: "\${google_compute_target_pool.${LOAD_BALANCER_NAME}.self_link}"]
      def targetPoolMap = [name: LOAD_BALANCER_TARGET_POOL.split("/").last(),
                           region: LOAD_BALANCER_REGION,
                           project: null,
                           health_checks: ["\${google_compute_http_health_check.${HEALTH_CHECK_NAME}.name}"]]
      def healthCheckMap = [name: HEALTH_CHECK_NAME,
                            check_interval_sec: HEALTH_CHECK_INTERVAL,
                            healthy_threshold: HEALTH_CHECK_HEALTHY_THRESHOLD,
                            port: HEALTH_CHECK_PORT,
                            project: null,
                            request_path: HEALTH_CHECK_REQUEST_PATH,
                            timeout_sec: HEALTH_CHECK_TIMEOUT,
                            unhealthy_threshold: HEALTH_CHECK_UNHEALTHY_THRESHOLD]
      @Subject def operation = new SaveSnapshotAtomicOperation(new SaveSnapshotDescription())
    when:
      operation.initializeResourceMap(resourceMap)
      operation.addLoadBalancerToResourceMap(loadBalancer.view, resourceMap)
    then:
      resourceMap.google_compute_target_pool[LOAD_BALANCER_NAME] == targetPoolMap
      resourceMap.google_compute_forwarding_rule[LOAD_BALANCER_NAME] == forwardingRuleMap
      resourceMap.google_compute_http_health_check[HEALTH_CHECK_NAME] == healthCheckMap

  }

  void "should serialize a security group"() {
    setup:
      def resourceMap = [:]
      // Create a security group
      def portRanges = new TreeSet()
      portRanges.add(new Rule.PortRange(startPort: SECURITY_GROUP_RULE_PORT,
                                        endPort: SECURITY_GROUP_RULE_PORT))
      def rule = new IpRangeRule(protocol: SECURITY_GROUP_RULE_PROTOCOL,
                                 portRanges: portRanges)
      def securityGroup = new GoogleSecurityGroup(name: SECURITY_GROUP_NAME,
                                                  network: SECURITY_GROUP_NETWORK,
                                                  inboundRules: [rule],
                                                  targetTags: SECURITY_GROUP_TARGET_TAGS)
      // Create a security group map
      def firewallMap = [name: SECURITY_GROUP_NAME,
                         network: SECURITY_GROUP_NETWORK,
                         project: null,
                         allow: [[protocol: SECURITY_GROUP_RULE_PROTOCOL, ports: ["$SECURITY_GROUP_RULE_PORT"]]],
                         source_ranges: [],
                         target_tags: SECURITY_GROUP_TARGET_TAGS]
      @Subject def operation = new SaveSnapshotAtomicOperation(new SaveSnapshotDescription())
    when:
      operation.initializeResourceMap(resourceMap)
      operation.addSecurityGroupToResourceMap(securityGroup, resourceMap)
    then:
      resourceMap.google_compute_firewall[SECURITY_GROUP_NAME] == firewallMap
  }

  void "should throw exception because server group has no instance template"() {
    setup:
      def resourceMap = [:]

      // Create a server group with no instance template
      def serverGroup = new GoogleServerGroup(name: SERVER_GROUP_NAME,
        zone: SERVER_GROUP_ZONE,
        asg: [(GCEUtil.REGIONAL_LOAD_BALANCER_NAMES): SERVER_GROUP_LOAD_BALANCERS],
        launchConfig: ["instanceTemplate": null])
      @Subject def operation = new SaveSnapshotAtomicOperation(new SaveSnapshotDescription())

    when:
      operation.initializeResourceMap(resourceMap)
      operation.addServerGroupToResourceMap(serverGroup.view, resourceMap)
    then:
      def e = thrown(GoogleResourceIllegalStateException)
      e.message == "Required instance template not found for server group: ${SERVER_GROUP_NAME}"
  }
}
