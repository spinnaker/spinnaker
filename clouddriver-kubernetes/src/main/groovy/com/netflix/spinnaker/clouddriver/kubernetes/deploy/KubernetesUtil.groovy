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
import com.netflix.spinnaker.clouddriver.kubernetes.deploy.description.KubernetesAtomicOperationDescription
import com.netflix.spinnaker.clouddriver.kubernetes.security.KubernetesCredentials
import io.fabric8.kubernetes.api.model.ReplicationController
import io.fabric8.kubernetes.api.model.ReplicationControllerList
import io.fabric8.kubernetes.api.model.Service

// TODO(stevenkim): make all methods non-static and wire in as bean for usage.
class KubernetesUtil {

  private static String SECURITY_GROUP_LABEL_PREFIX = "security-group-"
  private static String LOAD_BALANCER_LABEL_PREFIX = "load-balancer-"
  private static int SECURITY_GROUP_LABEL_PREFIX_LENGTH = SECURITY_GROUP_LABEL_PREFIX.length()
  private static int LOAD_BALANCER_LABEL_PREFIX_LENGTH = LOAD_BALANCER_LABEL_PREFIX.length()

  static def securityGroupKey(String securityGroup) {
    return String.format("$SECURITY_GROUP_LABEL_PREFIX%s", securityGroup)
  }

  static def loadBalancerKey(String loadBalancer) {
    return String.format("$LOAD_BALANCER_LABEL_PREFIX%s", loadBalancer)
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

  ReplicationController getReplicationController(KubernetesCredentials credentials, String serverGroupName) {
    credentials.client.replicationControllers().inNamespace(credentials.namespace).withName(serverGroupName).get()
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

  static List<String> getDescriptionLoadBalancers(ReplicationController rc) {
    def loadBalancers = []
    rc.spec?.template?.metadata?.labels?.each { key, val ->
      if (key.startsWith(LOAD_BALANCER_LABEL_PREFIX)) {
        loadBalancers.push(key.substring(LOAD_BALANCER_LABEL_PREFIX_LENGTH, key.length()))
      }
    }
    return loadBalancers
  }

  static List<String> getDescriptionSecurityGroups(ReplicationController rc) {
    def securityGroups = []
    rc.spec?.template?.metadata?.labels?.each { key, val ->
      if (key.startsWith(SECURITY_GROUP_LABEL_PREFIX)) {
        securityGroups.push(key.substring(SECURITY_GROUP_LABEL_PREFIX_LENGTH, key.length()))
      }
    }
    return securityGroups
  }

}
