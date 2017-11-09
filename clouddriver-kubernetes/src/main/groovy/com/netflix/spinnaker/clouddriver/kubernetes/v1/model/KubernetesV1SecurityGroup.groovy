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

package com.netflix.spinnaker.clouddriver.kubernetes.v1.model

import com.netflix.spinnaker.clouddriver.kubernetes.KubernetesCloudProvider
import com.netflix.spinnaker.clouddriver.kubernetes.v1.api.KubernetesApiConverter
import com.netflix.spinnaker.clouddriver.kubernetes.v1.deploy.description.securitygroup.KubernetesIngressTlS
import com.netflix.spinnaker.clouddriver.kubernetes.v1.deploy.description.securitygroup.KubernetesSecurityGroupDescription
import com.netflix.spinnaker.clouddriver.model.SecurityGroup
import com.netflix.spinnaker.clouddriver.model.SecurityGroupSummary
import com.netflix.spinnaker.clouddriver.model.securitygroups.HttpRule
import com.netflix.spinnaker.clouddriver.model.securitygroups.Rule
import groovy.transform.EqualsAndHashCode
import io.fabric8.kubernetes.api.model.extensions.Ingress
import io.fabric8.kubernetes.client.internal.SerializationUtils

@EqualsAndHashCode(includes = ["name", "namespace", "accountName"])
class KubernetesV1SecurityGroup implements SecurityGroup, Serializable {
  final String type = KubernetesCloudProvider.ID
  final String cloudProvider = KubernetesCloudProvider.ID

  static final private HTTP_PORT = 80
  static final private HTTPS_PORT = 443

  String id
  String name
  String application
  String accountName
  String region
  String namespace
  String yaml

  Map<String, String> annotations
  Map<String, String> labels

  Set<Rule> inboundRules
  Set<Rule> outboundRules

  Set<String> loadBalancers = [] as Set

  List<KubernetesIngressTlS> tls

  Ingress ingress
  KubernetesSecurityGroupDescription description

  KubernetesV1SecurityGroup(String application, String account, Ingress ingress, boolean includeRules) {
    this.ingress = ingress

    this.application = application
    this.accountName = account
    this.region = ingress.metadata.namespace
    this.namespace = this.region
    this.name = ingress.metadata.name
    this.id = this.name
    this.description = KubernetesApiConverter.fromIngress(ingress)
    this.yaml = SerializationUtils.dumpWithoutRuntimeStateAsYaml(ingress)

    this.annotations = ingress.metadata.annotations
    this.labels = ingress.metadata.labels

    if (ingress.spec?.backend?.serviceName) {
      loadBalancers.add(ingress.spec.backend.serviceName)
    }

    this.inboundRules = (includeRules ? (ingress.spec.rules?.collect { rule ->
      def defaultPort = new Rule.PortRange(startPort: HTTP_PORT, endPort: HTTP_PORT)
      def tlsPort = new Rule.PortRange(startPort: HTTPS_PORT, endPort: HTTPS_PORT)

      def paths = rule.http?.paths?.collect { path ->
        loadBalancers.add(path.backend.serviceName)
        path.path
      }

      def host = rule.host

      return new HttpRule(portRanges: ([defaultPort, tlsPort] as SortedSet),
                          paths: paths,
                          host: host)
    }) : []) as Set

    tls = ingress.spec.tls?.collect{ tlsSpecEntry ->
      return new KubernetesIngressTlS(hosts: tlsSpecEntry.hosts, secretName: tlsSpecEntry.secretName)
    }
  }

  SecurityGroupSummary getSummary() {
    return new KubernetesV1SecurityGroupSummary(name: name, id: id)
  }
}
