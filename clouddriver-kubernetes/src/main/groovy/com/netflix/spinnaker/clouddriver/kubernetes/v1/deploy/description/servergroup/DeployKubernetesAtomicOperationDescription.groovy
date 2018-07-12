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

package com.netflix.spinnaker.clouddriver.kubernetes.v1.deploy.description.servergroup

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonProperty
import com.netflix.spinnaker.clouddriver.deploy.DeployDescription
import com.netflix.spinnaker.clouddriver.kubernetes.v1.deploy.description.KubernetesKindAtomicOperationDescription
import groovy.transform.AutoClone
import groovy.transform.Canonical

@AutoClone
@Canonical
class DeployKubernetesAtomicOperationDescription extends KubernetesKindAtomicOperationDescription implements DeployDescription {
  String application
  String stack
  String freeFormDetails
  String namespace
  String restartPolicy
  Integer targetSize
  Boolean hostNetwork
  List<String> loadBalancers
  List<String> securityGroups
  List<KubernetesContainerDescription> containers
  List<KubernetesContainerDescription> initContainers
  List<KubernetesVolumeSource> volumeSources
  Capacity capacity
  KubernetesScalingPolicy scalingPolicy
  Map<String, String> replicaSetAnnotations
  Map<String, String> controllerAnnotations
  Map<String, String> podAnnotations
  Map<String, String> volumeAnnotations
  Map<String, String> nodeSelector
  KubernetesSecurityContext securityContext
  KubernetesDeployment deployment
  KubernetesUpdateController updateController
  Long terminationGracePeriodSeconds
  String serviceAccountName
  Integer sequence
  KubernetesPodSpecDescription podSpec
  String podManagementPolicy
  KubernetesDnsPolicy dnsPolicy
  Source source
  List<KubernetesPersistentVolumeClaimDescription> volumeClaims
  List<KubernetesToleration> tolerations

  @JsonIgnore
  Set<String> imagePullSecrets
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
  String registry
  String repository
  String tag
  String digest
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
  List<KubernetesEnvFromSource> envFrom

  List<String> command
  List<String> args

  KubernetesSecurityContext securityContext
}

@AutoClone
@Canonical
class KubernetesDeployment {
  boolean enabled
  KubernetesStrategy deploymentStrategy
  int minReadySeconds
  Integer revisionHistoryLimit     // May be null
  boolean paused
  Integer rollbackRevision         // May be null
  Integer progressRollbackSeconds  // May be null
}

@AutoClone
@Canonical
class KubernetesUpdateController {
  boolean enabled
  KubernetesStrategy updateStrategy
  int minReadySeconds
  Integer revisionHistoryLimit
}

@AutoClone
@Canonical
class KubernetesStrategy {
  KubernetesStrategyType type
  KubernetesRollingUpdate rollingUpdate
}

@AutoClone
@Canonical
class KubernetesRollingUpdate {
  String maxUnavailable
  String maxSurge
  Integer partition
}

enum KubernetesStrategyType {
  Recreate,
  RollingUpdate
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
class KubernetesEnvFromSource {
  String prefix
  KubernetesConfigMapEnvSource configMapRef
  KubernetesSecretEnvSource secretRef
}

@AutoClone
@Canonical
class KubernetesConfigMapEnvSource {
  String name
  boolean optional
}

@AutoClone
@Canonical
class KubernetesSecretEnvSource {
  String name
  boolean optional
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
  KubernetesFieldRefSource fieldRef
  KubernetesResourceFieldRefSource resourceFieldRef
}

@AutoClone
@Canonical
class KubernetesResourceFieldRefSource {
  String resource
  String containerName
  String divisor
}

@AutoClone
@Canonical
class KubernetesFieldRefSource {
  String fieldPath
}

@AutoClone
@Canonical
class KubernetesSecretSource {
  String secretName
  String key
  Boolean optional = true
}

@AutoClone
@Canonical
class KubernetesConfigMapSource {
  String configMapName
  String key
  Boolean optional = true
}

@AutoClone
@Canonical
class KubernetesVolumeMount {
  String name
  Boolean readOnly
  String mountPath
  String subPath
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

  @JsonProperty("AWSELASTICBLOCKSTORE")
  AwsElasticBlockStore,

  @JsonProperty("NFS")
  NFS,

  @JsonProperty("UNSUPPORTED")
  Unsupported,
}

enum KubernetesDnsPolicy {
  @JsonProperty("ClusterFirst")
  ClusterFirst,

  @JsonProperty("Default")
  Default,

  @JsonProperty("ClusterFirstWithHostNet")
  ClusterFirstWithHostNet,
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
  KubernetesAwsElasticBlockStoreVolumeSource awsElasticBlockStore
  KubernetesNfsVolumeSource nfs
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
class KubernetesAwsElasticBlockStoreVolumeSource {
  String volumeId
  String fsType
  Integer partition
}

@AutoClone
@Canonical
class KubernetesNfsVolumeSource {
  String server
  String path
  Boolean readOnly
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

@AutoClone
@Canonical
//Base on model https://kubernetes.io/docs/resources-reference/v1.5/#podspec-v1
//which will map to Kubernetes 1.5 API version
class KubernetesPodSpecDescription {
  Long activeDeadlineSeconds
  List<KubernetesContainerDescription> containers
  String dnsPolicy
  Boolean hostIPC
  Boolean hostNetwork
  Boolean hostPID
  String hostname
  @JsonIgnore
  Set<String> imagePullSecrets
  String nodeName
  Map nodeSelector
  String restartPolicy
  KubernetesSecurityContext securityContext
  String serviceAccountName
  String subdomain
  Long terminationGracePeriodSeconds
  List<KubernetesVolumeSource> volumeSources
}

@AutoClone
@Canonical
class Source {
  String serverGroupName
  String region
  String namespace
  String account
  Boolean useSourceCapacity
}

class KubernetesPersistentVolumeClaimDescription {
  String claimName
  List<String> accessModes
  KubernetesPeristentResourceRequirement requirements
  KubernetesPersistentLabelSelector selector
  String storageClassName
}

@AutoClone
@Canonical
class KubernetesPeristentResourceRequirement {
  Map<String, String> limits
  Map<String, String> requests
}

@AutoClone
@Canonical
class KubernetesPersistentLabelSelector {
  List<KubernetesLabelSelectorRequirements> matchExpressions
  Map<String, String> matchLabels
}

@AutoClone
@Canonical
class KubernetesLabelSelectorRequirements {
  String key
  String operator
  List<String> values
}

enum KubernetesTolerationEffect {
  @JsonProperty("NoSchedule")
  NoSchedule,

  @JsonProperty("PreferNoSchedule")
  PreferNoSchedule,

  @JsonProperty("NoExecute")
  NoExecute
}

enum KubernetesTolerationOperator {
  @JsonProperty("Exists")
  Exists,

  @JsonProperty("Equal")
  Equal
}

@AutoClone
@Canonical
class KubernetesToleration {
  KubernetesTolerationEffect effect
  String key
  KubernetesTolerationOperator operator
  Long tolerationSeconds
  String value
}

