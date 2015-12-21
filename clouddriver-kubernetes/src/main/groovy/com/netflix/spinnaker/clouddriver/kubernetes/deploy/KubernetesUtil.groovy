/*
 * Copyright 2015 Google, Inc.
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

package com.netflix.spinnaker.clouddriver.kubernetes.deploy

import com.netflix.frigga.NameValidation
import com.netflix.frigga.Names
import com.netflix.spinnaker.clouddriver.kubernetes.security.KubernetesCredentials
import io.fabric8.kubernetes.api.model.ReplicationControllerList
import io.fabric8.kubernetes.api.model.Service

class KubernetesUtil {
  private static final String BASE_PHASE = "DEPLOY"

  static def securityGroupKey(String securityGroup) {
    return String.format("security-group-%s", securityGroup)
  }

  static def loadBalancerKey(String loadBalancer) {
    return String.format("load-balancer-%s", loadBalancer)
  }

  static def combineAppStackDetail(String appName, String stack, String detail) {
    NameValidation.notEmpty(appName, "appName");

    // Use empty strings, not null references that output "null"
    stack = stack != null ? stack : "";

    if (detail != null && !detail.isEmpty()) {
      return appName + "-" + stack + "-" + detail;
    }

    if (!stack.isEmpty()) {
      return appName + "-" + stack;
    }

    return appName;
  }

  static ReplicationControllerList getReplicationControllers(KubernetesCredentials credentials) {
    credentials.client.replicationControllers().inNamespace(credentials.namespace).list()
  }

  static Service getService(KubernetesCredentials credentials, String service) {
    credentials.client.services().inNamespace(credentials.namespace).withName(service).get()
  }

  static Service getSecurityGroup(KubernetesCredentials credentials, String securityGroup) {
    getService(credentials, securityGroup)
  }

  static Service getLoadBalancer(KubernetesCredentials credentials, String loadBalancer) {
    getService(credentials, loadBalancer)
  }

  static def getNextSequence(String clusterName, KubernetesCredentials credentials) {
    def maxSeqNumber = -1
    def replicationControllers = getReplicationControllers(credentials)

    for (def replicationController : replicationControllers.getItems()) {
      def names = Names.parseName(replicationController.getMetadata().getName())

      if (names.cluster == clusterName) {
        maxSeqNumber = Math.max(maxSeqNumber, names.sequence)
      }
    }

    String.format("%03d", ++maxSeqNumber)
  }
}
