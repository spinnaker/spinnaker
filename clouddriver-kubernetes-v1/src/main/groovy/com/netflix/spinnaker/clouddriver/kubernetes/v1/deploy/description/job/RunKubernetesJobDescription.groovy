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

package com.netflix.spinnaker.clouddriver.kubernetes.v1.deploy.description.job

import com.netflix.spinnaker.clouddriver.kubernetes.v1.deploy.description.KubernetesKindAtomicOperationDescription
import com.netflix.spinnaker.clouddriver.kubernetes.v1.deploy.description.servergroup.KubernetesContainerDescription
import com.netflix.spinnaker.clouddriver.kubernetes.v1.deploy.description.servergroup.KubernetesDnsPolicy
import com.netflix.spinnaker.clouddriver.kubernetes.v1.deploy.description.servergroup.KubernetesVolumeSource
import com.netflix.spinnaker.clouddriver.kubernetes.v1.deploy.description.servergroup.KubernetesToleration
import groovy.transform.AutoClone
import groovy.transform.Canonical

@AutoClone
@Canonical
class RunKubernetesJobDescription extends KubernetesKindAtomicOperationDescription {
  String application
  String stack
  String freeFormDetails
  String namespace
  Boolean hostNetwork=false
  Map<String, String> nodeSelector
  // this should be deprecated at some point
  KubernetesContainerDescription container
  List<KubernetesContainerDescription> containers
  List<KubernetesVolumeSource> volumeSources
  Map<String, String> labels
  Map<String, String> annotations
  String serviceAccountName
  KubernetesDnsPolicy dnsPolicy
  List<KubernetesToleration> tolerations
}
