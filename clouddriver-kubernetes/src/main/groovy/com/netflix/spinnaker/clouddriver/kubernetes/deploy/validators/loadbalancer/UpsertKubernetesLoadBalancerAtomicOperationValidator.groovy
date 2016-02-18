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

package com.netflix.spinnaker.clouddriver.kubernetes.deploy.validators.loadbalancer

import com.netflix.spinnaker.clouddriver.deploy.DescriptionValidator
import com.netflix.spinnaker.clouddriver.kubernetes.KubernetesOperation
import com.netflix.spinnaker.clouddriver.kubernetes.deploy.description.loadbalancer.UpsertKubernetesLoadBalancerAtomicOperationDescription
import com.netflix.spinnaker.clouddriver.kubernetes.deploy.validators.StandardKubernetesAttributeValidator
import com.netflix.spinnaker.clouddriver.kubernetes.security.KubernetesCredentials
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperations
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import org.springframework.validation.Errors

@KubernetesOperation(AtomicOperations.UPSERT_LOAD_BALANCER)
@Component
class UpsertKubernetesLoadBalancerAtomicOperationValidator extends DescriptionValidator<UpsertKubernetesLoadBalancerAtomicOperationDescription>{
  @Autowired
  AccountCredentialsProvider accountCredentialsProvider

  @Override
  void validate(List priorDescriptions, UpsertKubernetesLoadBalancerAtomicOperationDescription description, Errors errors) {
    def helper = new StandardKubernetesAttributeValidator("upsertKubernetesLoadBalancerAtomicOperationDescription", errors)

    if (!helper.validateCredentials(description.credentials, accountCredentialsProvider)) {
      return
    }

    KubernetesCredentials credentials = (KubernetesCredentials) accountCredentialsProvider.getCredentials(description.credentials).credentials

    helper.validateName(description.name, "name")
    helper.validateNamespace(credentials, description.namespace, "namespace")

    description.ports.eachWithIndex { port, idx ->
      helper.validateName(port.name, "ports[$idx].name")
      helper.validateProtocol(port.protocol, "ports[$idx].protocol")
      port.nodePort ? helper.validatePort(port.nodePort, "ports[$idx].nodePort") : null
      port.port ? helper.validatePort(port.port, "ports[$idx].port") : null
      port.targetPort ? helper.validatePort(port.targetPort, "ports[$idx].targetPort") : null
    }

    description.externalIps.eachWithIndex { ip, idx ->
      helper.validateIpv4(ip, "externalIps[$idx]")
    }

    description.clusterIp ? helper.validateIpv4(description.clusterIp, "clusterIp")  : null

    description.loadBalancerIp ? helper.validateIpv4(description.loadBalancerIp, "loadBalancerIp")  : null

    helper.validateSessionAffinity(description.sessionAffinity, "sessionAffinity")

    helper.validateServiceType(description.type, "type")
  }
}
