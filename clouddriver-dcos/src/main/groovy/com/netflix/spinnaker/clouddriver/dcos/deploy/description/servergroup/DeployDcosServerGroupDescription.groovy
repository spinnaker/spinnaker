/*
 * Copyright 2017 Cerner Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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

package com.netflix.spinnaker.clouddriver.dcos.deploy.description.servergroup

import com.netflix.spinnaker.clouddriver.dcos.deploy.description.AbstractDcosCredentialsDescription
import com.netflix.spinnaker.clouddriver.deploy.DeployDescription
import com.netflix.spinnaker.clouddriver.security.resources.ApplicationNameable
import groovy.transform.AutoClone
import groovy.transform.Canonical

@AutoClone
class DeployDcosServerGroupDescription extends AbstractDcosCredentialsDescription implements DeployDescription, ApplicationNameable {
  String application
  String stack
  String freeFormDetails
  String cmd
  List<String> args
  String dcosUser
  Map<String, Object> env = new HashMap<>()
  Integer desiredCapacity
  Double cpus
  Double mem
  Double disk
  Double gpus
  String constraints
  List<Fetchable> fetch = new ArrayList<>()
  List<String> storeUrls = new ArrayList<>()
  Integer backoffSeconds
  Double backoffFactor
  Integer maxLaunchDelaySeconds
  Docker docker
  List<HealthCheck> healthChecks = new ArrayList<>()
  List<ReadinessCheck> readinessChecks = new ArrayList<>()
  List<String> dependencies = new ArrayList<>()
  UpgradeStrategy upgradeStrategy
  Map<String, String> labels = new HashMap<>()
  List<String> acceptedResourceRoles = null
  Residency residency
  Integer taskKillGracePeriodSeconds
  Map<String, Object> secrets = new HashMap<>()

  String networkType
  String networkName
  List<ServiceEndpoint> serviceEndpoints = new ArrayList<>()
  List<PersistentVolume> persistentVolumes = new ArrayList<>()
  List<DockerVolume> dockerVolumes = new ArrayList<>()
  List<ExternalVolume> externalVolumes = new ArrayList<>()
  Boolean requirePorts
  List<Network> networks

  boolean forceDeployment

  @Override
  Collection<String> getApplications() {
    return [application]
  }

  @Canonical
  static class Container {
    String type
  }

  @Canonical
  static class Image {
    String registry
    String repository
    String tag
    String imageId
  }

  @Canonical
  static class Fetchable {
    String uri
    Boolean executable
    Boolean extract
    Boolean cache
    String outputFile
  }

  @Canonical
  static class Docker {
    Image image

    String network
    boolean privileged
    List<Parameter> parameters
    boolean forcePullImage
  }

  @Canonical
  static class Parameter {
    String key
    String value
  }

  @Canonical
  static class UpgradeStrategy {
    Double minimumHealthCapacity
    Double maximumOverCapacity
  }

  @Canonical
  static class HealthCheck {
    String protocol
    String path
    String command
    Integer port
    Integer portIndex
    Integer gracePeriodSeconds
    Integer intervalSeconds
    Integer timeoutSeconds
    Integer maxConsecutiveFailures
    boolean ignoreHttp1xx
  }

  @Canonical
  static class ServiceEndpoint {
    String networkType
    Integer port
    Integer servicePort
    String name
    String protocol
    boolean loadBalanced
    boolean exposeToHost
    Map<String, String> labels = new HashMap<>()
  }

  @Canonical
  static class Volume {
    String containerPath
    String mode
  }

  @Canonical
  static class PersistentVolume extends Volume {
    PersistentStorage persistent
  }

  @Canonical
  static class DockerVolume extends Volume {
    String hostPath
  }

  @Canonical
  static class ExternalVolume extends Volume {
    ExternalStorage external
  }

  @Canonical
  static class PersistentStorage {
    Integer size
  }

  @Canonical
  static class ExternalStorage {
    String name
    String provider
    ExternalStorageOptions options
    String mode
    Integer size
  }

  @Canonical
  static class ExternalStorageOptions {
    String driver
    Integer size
    Integer iops
    String volumeType
    String newFsType
    Boolean overwriteFs
  }

  @Canonical
  static class Residency {
    String taskLostBehavior
    Integer relaunchEscalationTimeoutSeconds
  }

  @Canonical
  static class ReadinessCheck {
    String name
    String protocol
    String path
    String portName
    Integer intervalSeconds
    Integer timeoutSeconds
    Collection<Integer> httpStatusCodesForReady
    boolean preserveLastResponse
  }

  @Canonical
  static class Network {
    String name
    String mode
    Map<String, String> labels = new HashMap()
  }
}
