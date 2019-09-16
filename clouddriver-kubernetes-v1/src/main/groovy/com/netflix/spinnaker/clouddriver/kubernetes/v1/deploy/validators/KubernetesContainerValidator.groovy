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

package com.netflix.spinnaker.clouddriver.kubernetes.v1.deploy.validators

import com.netflix.spinnaker.clouddriver.kubernetes.v1.deploy.description.servergroup.KubernetesContainerDescription
import com.netflix.spinnaker.clouddriver.kubernetes.v1.deploy.description.servergroup.KubernetesHandlerType
import com.netflix.spinnaker.clouddriver.kubernetes.v1.deploy.description.servergroup.KubernetesProbe

class KubernetesContainerValidator {
  static void validate(KubernetesContainerDescription description, StandardKubernetesAttributeValidator helper, String prefix) {
    helper.validateName(description.name, "${prefix}.name")

    helper.validateNotEmpty(description.imageDescription, "${prefix}.imageDescription")

    if (description.limits) {
      helper.validateCpu(description.limits.cpu, "${prefix}.limits.cpu")
      helper.validateMemory(description.limits.memory, "${prefix}.limits.memory")
    }

    if (description.requests) {
      helper.validateCpu(description.requests.cpu, "${prefix}.requests.cpu")
      helper.validateMemory(description.requests.memory, "${prefix}.requests.memory")
    }

    description.ports?.eachWithIndex { port, i ->
      if (port.name) {
        helper.validateName(port.name, "${prefix}.ports[$i].name")
      }
      if (port.containerPort) {
        helper.validatePort(port.containerPort, "${prefix}.ports[$i].containerPort")
      }
      if (port.hostPort) {
        helper.validatePort(port.hostPort, "${prefix}.ports[$i].hostPort")
      }
      if (port.hostIp) {
        helper.validateIpv4(port.hostIp, "${prefix}.ports[$i].hostIp")
      }
      if (port.protocol) {
        helper.validateProtocol(port.protocol, "${prefix}.ports[$i].protocol")
      }
    }

    description.envVars?.eachWithIndex { envVar, i ->
      helper.validateNotEmpty(envVar.name, "${prefix}.envVars[$i].name")
    }

    description.volumeMounts?.eachWithIndex { mount, i ->
      helper.validateName(mount.name, "${prefix}.mounts[$i].name")
      helper.validatePath(mount.mountPath, "${prefix}.mounts[$i].mountPath")
    }

    if (description.livenessProbe) {
      validateProbe(description.livenessProbe, helper, "${prefix}.livenessProbe")
    }

    if (description.readinessProbe) {
      validateProbe(description.readinessProbe, helper, "${prefix}.readinessProbe")
    }

    description.command?.eachWithIndex { command, i ->
      helper.validateNotEmpty(command, "${prefix}.command[$i]")
    }

    description.args?.eachWithIndex { arg, i ->
      helper.validateNotEmpty(arg, "${prefix}.args[$i]")
    }
  }

  static void validateProbe(KubernetesProbe probe, StandardKubernetesAttributeValidator helper, String prefix) {
    if (probe.initialDelaySeconds) {
      helper.validateNonNegative(probe.initialDelaySeconds, "${prefix}.initialDelaySeconds")
    }

    if (probe.timeoutSeconds) {
      helper.validatePositive(probe.timeoutSeconds, "${prefix}.timeoutSeconds")
    }

    if (probe.periodSeconds) {
      helper.validatePositive(probe.periodSeconds, "${prefix}.periodSeconds")
    }

    if (probe.successThreshold) {
      helper.validatePositive(probe.successThreshold, "${prefix}.successThreshold")
    }

    if (probe.failureThreshold) {
      helper.validatePositive(probe.failureThreshold, "${prefix}.failureThreshold")
    }

    helper.validateNotEmpty(probe.handler, "${prefix}.handler")
    helper.validateNotEmpty(probe.handler?.type, "${prefix}.handler.type")

    if (probe.handler?.type == KubernetesHandlerType.EXEC) {
      helper.validateNotEmpty(probe.handler?.execAction?.commands, "${prefix}.handler.execAction.commands")
    }

    if (probe.handler?.type == KubernetesHandlerType.TCP) {
      helper.validatePort(probe.handler?.tcpSocketAction?.port, "${prefix}.handler.tcpSocketAction.port")
    }

    if (probe.handler?.type == KubernetesHandlerType.HTTP) {
      helper.validatePort(probe.handler?.httpGetAction?.port, "${prefix}.handler.httpGetAction.port")

      if (probe.handler?.httpGetAction?.uriScheme) {
        helper.validateUriScheme(probe.handler?.httpGetAction?.uriScheme, "${prefix}.handler.httpGetAction.uriScheme")
      }
    }
  }
}
