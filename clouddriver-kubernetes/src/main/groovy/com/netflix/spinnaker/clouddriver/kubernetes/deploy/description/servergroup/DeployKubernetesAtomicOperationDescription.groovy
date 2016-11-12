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

package com.netflix.spinnaker.clouddriver.kubernetes.deploy.description.servergroup

import com.fasterxml.jackson.annotation.JsonProperty
import com.netflix.spinnaker.clouddriver.deploy.DeployDescription
import com.netflix.spinnaker.clouddriver.kubernetes.deploy.description.KubernetesAtomicOperationDescription
import groovy.transform.AutoClone
import groovy.transform.Canonical

@AutoClone
@Canonical
class DeployKubernetesAtomicOperationDescription extends KubernetesAtomicOperationDescription implements DeployDescription {
  String application
  String stack
  String freeFormDetails
  String namespace
  String restartPolicy
  Integer targetSize
  List<String> loadBalancers
  List<String> securityGroups
  List<KubernetesContainerDescription> containers
  List<KubernetesVolumeSource> volumeSources
  Capacity capacity
  KubernetesScalingPolicy scalingPolicy
  Map<String, String> replicaSetAnnotations
  Map<String, String> podAnnotations
  KubernetesSecurityContext securityContext
}

@AutoClone
@Canonical
class Capacity {
  Integer min
  Integer max
  Integer desired
}

@AutoClone
@Canonical
class KubernetesContainerPort {
  String name
  Integer containerPort
  String protocol
  String hostIp
  Integer hostPort
}

@AutoClone
@Canonical
class KubernetesImageDescription {
  String repository
  String tag
  String registry
}

@AutoClone
@Canonical
class KubernetesContainerDescription {
  String name
  KubernetesImageDescription imageDescription
  KubernetesPullPolicy imagePullPolicy

  KubernetesResourceDescription requests
  KubernetesResourceDescription limits

  List<KubernetesContainerPort> ports

  KubernetesProbe livenessProbe
  KubernetesProbe readinessProbe

  KubernetesLifecycle lifecycle

  List<KubernetesVolumeMount> volumeMounts
  List<KubernetesEnvVar> envVars

  List<String> command
  List<String> args

  KubernetesSecurityContext securityContext
}

@AutoClone
@Canonical
class KubernetesLifecycle {
  KubernetesHandler postStart
  KubernetesHandler preStop
}

@AutoClone
@Canonical
class KubernetesEnvVar {
  String name
  String value
  KubernetesEnvVarSource envSource
}

@AutoClone
@Canonical
class KubernetesScalingPolicy {
  KubernetesCpuUtilization cpuUtilization
}

@AutoClone
@Canonical
class KubernetesCpuUtilization {
  Integer target
}

enum KubernetesPullPolicy {
  @JsonProperty("IFNOTPRESENT")
  IfNotPresent,

  @JsonProperty("ALWAYS")
  Always,

  @JsonProperty("NEVER")
  Never,
}

@AutoClone
@Canonical
class KubernetesEnvVarSource {
  KubernetesSecretSource secretSource
  KubernetesConfigMapSource configMapSource
}

@AutoClone
@Canonical
class KubernetesSecretSource {
  String secretName
  String key
}

@AutoClone
@Canonical
class KubernetesConfigMapSource {
  String configMapName
  String key
}

@AutoClone
@Canonical
class KubernetesVolumeMount {
  String name
  Boolean readOnly
  String mountPath
}

enum KubernetesVolumeSourceType {
  @JsonProperty("HOSTPATH")
  HostPath,

  @JsonProperty("EMPTYDIR")
  EmptyDir,

  @JsonProperty("PERSISTENTVOLUMECLAIM")
  PersistentVolumeClaim,

  @JsonProperty("SECRET")
  Secret,

  @JsonProperty("CONFIGMAP")
  ConfigMap,

  @JsonProperty("UNSUPPORTED")
  Unsupported,
}

enum KubernetesStorageMediumType {
  @JsonProperty("DEFAULT")
  Default,

  @JsonProperty("MEMORY")
  Memory,
}

@AutoClone
@Canonical
class KubernetesVolumeSource {
  String name
  KubernetesVolumeSourceType type
  KubernetesHostPath hostPath
  KubernetesEmptyDir emptyDir
  KubernetesPersistentVolumeClaim persistentVolumeClaim
  KubernetesSecretVolumeSource secret
  KubernetesConfigMapVolumeSource configMap
}

@AutoClone
@Canonical
class KubernetesConfigMapVolumeSource {
  String configMapName
  List<KubernetesKeyToPath> items
  Integer defaultMode
}

@AutoClone
@Canonical
class KubernetesKeyToPath {
  String key
  String path
  Integer defaultMode
}

@AutoClone
@Canonical
class KubernetesSecretVolumeSource {
  String secretName
}

@AutoClone
@Canonical
class KubernetesHostPath {
  String path
}

@AutoClone
@Canonical
class KubernetesEmptyDir {
  KubernetesStorageMediumType medium
}

@AutoClone
@Canonical
class KubernetesPersistentVolumeClaim {
  String claimName
  Boolean readOnly
}

@AutoClone
@Canonical
class KubernetesProbe {
  KubernetesHandler handler
  int initialDelaySeconds
  int timeoutSeconds
  int periodSeconds
  int successThreshold
  int failureThreshold
}

enum KubernetesHandlerType {
  EXEC, TCP, HTTP
}

@AutoClone
@Canonical
class KubernetesHandler {
  KubernetesHandlerType type
  KubernetesExecAction execAction
  KubernetesHttpGetAction httpGetAction
  KubernetesTcpSocketAction tcpSocketAction
}

@AutoClone
@Canonical
class KubernetesExecAction {
  List<String> commands
}

@AutoClone
@Canonical
class KubernetesHttpGetAction {
  String path
  int port
  String host
  String uriScheme
  List<KeyValuePair> httpHeaders
}

@AutoClone
@Canonical
class KubernetesTcpSocketAction {
  int port
}

@AutoClone
@Canonical
class KubernetesResourceDescription {
  String memory
  String cpu
}

@AutoClone
@Canonical
class KeyValuePair {
  String name
  String value
}

@AutoClone
@Canonical
class KubernetesSecurityContext {
  KubernetesCapabilities capabilities
  Boolean privileged
  KubernetesSeLinuxOptions seLinuxOptions
  Long runAsUser
  Boolean runAsNonRoot
  Boolean readOnlyRootFilesystem
}

@AutoClone
@Canonical
class KubernetesCapabilities {
  List<String> add
  List<String> drop
}

@AutoClone
@Canonical
class KubernetesSeLinuxOptions {
  String user
  String role
  String type
  String level
}
