/*
 * Copyright 2026 Harness, Inc.
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

package com.netflix.spinnaker.clouddriver.google.deploy.ops.loadbalancer

import com.google.api.services.compute.model.ForwardingRule
import com.netflix.spinnaker.clouddriver.google.deploy.description.UpsertGoogleLoadBalancerDescription
import com.netflix.spinnaker.clouddriver.google.model.GoogleNetwork
import com.netflix.spinnaker.clouddriver.google.model.GoogleSubnet
import com.netflix.spinnaker.clouddriver.google.model.loadbalancing.GoogleInternalHttpLoadBalancer
import com.netflix.spinnaker.clouddriver.google.provider.view.GoogleSubnetProvider
import spock.lang.Specification
import spock.lang.Subject

class UpsertGoogleExternalHttpLoadBalancerAtomicOperationUnitSpec extends Specification {
  private static final ACCOUNT_NAME = "auto"
  private static final PROJECT_NAME = "my-project"
  private static final REGION = "us-central1"

  void "resolveSubnet validates proxy-only subnet exists on selected network"() {
    setup:
      def subnetProvider = Mock(GoogleSubnetProvider)
      def description = new UpsertGoogleLoadBalancerDescription(
        accountName: ACCOUNT_NAME,
        region: REGION,
        network: "default")
      @Subject def operation = new UpsertGoogleExternalHttpLoadBalancerAtomicOperation(description)
      operation.googleSubnetProvider = subnetProvider
      def network = new GoogleNetwork(
        name: "default",
        id: "default",
        selfLink: "https://compute.googleapis.com/compute/v1/projects/${PROJECT_NAME}/global/networks/default")
      def proxyOnlySubnet = new GoogleSubnet(
        account: ACCOUNT_NAME,
        region: REGION,
        network: "https://compute.googleapis.com/compute/v1/projects/${PROJECT_NAME}/global/networks/default",
        purpose: "REGIONAL_MANAGED_PROXY")
      subnetProvider.getAllMatchingKeyPattern("gce:subnets:*:${ACCOUNT_NAME}:${REGION}") >> ([proxyOnlySubnet] as Set)

    when:
      def resolvedSubnet = operation.resolveSubnet(network)

    then:
      resolvedSubnet == null
  }

  void "resolveSubnet rejects proxy-only subnet from different XPN project with same local network name"() {
    setup:
      def subnetProvider = Mock(GoogleSubnetProvider)
      def description = new UpsertGoogleLoadBalancerDescription(
        accountName: ACCOUNT_NAME,
        region: REGION,
        network: "https://compute.googleapis.com/compute/v1/projects/${PROJECT_NAME}/global/networks/default")
      @Subject def operation = new UpsertGoogleExternalHttpLoadBalancerAtomicOperation(description)
      operation.googleSubnetProvider = subnetProvider
      def network = new GoogleNetwork(
        name: "default",
        id: "default",
        selfLink: "https://compute.googleapis.com/compute/v1/projects/${PROJECT_NAME}/global/networks/default")
      def proxyOnlySubnet = new GoogleSubnet(
        account: ACCOUNT_NAME,
        region: REGION,
        network: "https://compute.googleapis.com/compute/v1/projects/other-project/global/networks/default",
        purpose: "REGIONAL_MANAGED_PROXY")
      subnetProvider.getAllMatchingKeyPattern("gce:subnets:*:${ACCOUNT_NAME}:${REGION}") >> ([proxyOnlySubnet] as Set)

    when:
      operation.resolveSubnet(network)

    then:
      thrown IllegalArgumentException
  }

  void "resolveSubnet fails when proxy-only subnet is missing"() {
    setup:
      def subnetProvider = Mock(GoogleSubnetProvider)
      def description = new UpsertGoogleLoadBalancerDescription(
        accountName: ACCOUNT_NAME,
        region: REGION,
        network: "default")
      @Subject def operation = new UpsertGoogleExternalHttpLoadBalancerAtomicOperation(description)
      operation.googleSubnetProvider = subnetProvider
      def network = new GoogleNetwork(
        name: "default",
        id: "default",
        selfLink: "https://compute.googleapis.com/compute/v1/projects/${PROJECT_NAME}/global/networks/default")
      def regularSubnet = new GoogleSubnet(
        account: ACCOUNT_NAME,
        region: REGION,
        network: "default",
        purpose: "PRIVATE")
      subnetProvider.getAllMatchingKeyPattern("gce:subnets:*:${ACCOUNT_NAME}:${REGION}") >> ([regularSubnet] as Set)

    when:
      operation.resolveSubnet(network)

    then:
      thrown IllegalArgumentException
  }

  void "configureForwardingRule uses external managed scheme network tier and no subnetwork"() {
    setup:
      def description = new UpsertGoogleLoadBalancerDescription(networkTier: "STANDARD")
      @Subject def operation = new UpsertGoogleExternalHttpLoadBalancerAtomicOperation(description)
      def rule = new ForwardingRule()
      def loadBalancer = new GoogleInternalHttpLoadBalancer(
        name: "external-http",
        ipAddress: "1.1.1.1",
        ipProtocol: "UDP",
        network: "projects/${PROJECT_NAME}/global/networks/default",
        subnet: "projects/${PROJECT_NAME}/regions/${REGION}/subnetworks/proxy-only",
        portRange: "80",
        certificate: "my-cert")

    when:
      operation.configureForwardingRule(rule, loadBalancer, "target-proxy-url")

    then:
      rule.name == "external-http"
      rule.loadBalancingScheme == "EXTERNAL_MANAGED"
      rule.IPProtocol == "TCP"
      rule.networkTier == "STANDARD"
      rule.subnetwork == null
      rule.network == "projects/${PROJECT_NAME}/global/networks/default"
      rule.portRange == "443"
      rule.target == "target-proxy-url"
  }

  void "uses external managed task identity"() {
    setup:
      @Subject def operation = new UpsertGoogleExternalHttpLoadBalancerAtomicOperation(
        new UpsertGoogleLoadBalancerDescription())

    expect:
      operation.basePhase == "UPSERT_EXTERNAL_HTTP_LOAD_BALANCER"
      operation.loadBalancerDescriptionLabel == "Regional External HTTP(S) load balancer"
  }

  void "buildCertificateUrl preserves certificate manager regional resource URLs"() {
    setup:
      @Subject def operation = new UpsertGoogleExternalHttpLoadBalancerAtomicOperation(
        new UpsertGoogleLoadBalancerDescription())
      def httpsUrl = "https://certificatemanager.googleapis.com/projects/${PROJECT_NAME}/locations/${REGION}/certificates/cm-cert"
      def versionedHttpsUrl = "https://certificatemanager.googleapis.com/v1alpha1/projects/${PROJECT_NAME}/locations/${REGION}/certificates/cm-cert"
      def protocolRelativeUrl = "//certificatemanager.googleapis.com/projects/${PROJECT_NAME}/locations/${REGION}/certificates/cm-cert"
      def bareResourceName = "projects/${PROJECT_NAME}/locations/${REGION}/certificates/cm-cert"

    expect:
      operation.buildCertificateUrl(PROJECT_NAME, REGION, httpsUrl) == protocolRelativeUrl
      operation.buildCertificateUrl(PROJECT_NAME, REGION, versionedHttpsUrl) == protocolRelativeUrl
      operation.buildCertificateUrl(PROJECT_NAME, REGION, protocolRelativeUrl) == protocolRelativeUrl
      operation.buildCertificateUrl(PROJECT_NAME, REGION, bareResourceName) == protocolRelativeUrl
      operation.buildCertificateUrl(PROJECT_NAME, REGION, "compute-cert")
        .endsWith("/projects/${PROJECT_NAME}/regions/${REGION}/sslCertificates/compute-cert")
  }

  void "certificate comparison preserves certificate manager identity and localizes compute certs"() {
    setup:
      @Subject def operation = new UpsertGoogleExternalHttpLoadBalancerAtomicOperation(
        new UpsertGoogleLoadBalancerDescription())
      def certificateManagerUrl = "//certificatemanager.googleapis.com/projects/${PROJECT_NAME}/locations/${REGION}/certificates/shared-name"
      def computeUrl = "https://compute.googleapis.com/compute/v1/projects/${PROJECT_NAME}/regions/${REGION}/sslCertificates/shared-name"

    expect:
      operation.getExistingCertificateForComparison([certificateManagerUrl]) == certificateManagerUrl
      operation.getDesiredCertificateForComparison(PROJECT_NAME, REGION, "projects/${PROJECT_NAME}/locations/${REGION}/certificates/shared-name") == certificateManagerUrl
      operation.getExistingCertificateForComparison([computeUrl]) == "shared-name"
      operation.getDesiredCertificateForComparison(PROJECT_NAME, REGION, "shared-name") == "shared-name"
  }

  void "buildCertificateUrl rejects malformed certificate manager resources"() {
    setup:
      @Subject def operation = new UpsertGoogleExternalHttpLoadBalancerAtomicOperation(
        new UpsertGoogleLoadBalancerDescription())

    expect:
      rejectsMalformedCertificateManagerResource(operation, "certificatemanager.googleapis.com/bad-cert")
      rejectsMalformedCertificateManagerResource(operation, "projects/${PROJECT_NAME}/locations/${REGION}/certificates")
      rejectsMalformedCertificateManagerResource(operation, "projects/${PROJECT_NAME}/locations/${REGION}/certificates/")
      rejectsMalformedCertificateManagerResource(operation, "projects/${PROJECT_NAME}/locations/${REGION}/certificates/cm-cert/extra")
  }

  private static boolean rejectsMalformedCertificateManagerResource(
    UpsertGoogleExternalHttpLoadBalancerAtomicOperation operation,
    String certificate) {
    try {
      operation.buildCertificateUrl(PROJECT_NAME, REGION, certificate)
      return false
    } catch (IllegalArgumentException ignored) {
      return true
    }
  }

  void "operate rejects certificateMap defensively"() {
    setup:
      @Subject def operation = new UpsertGoogleExternalHttpLoadBalancerAtomicOperation(
        new UpsertGoogleLoadBalancerDescription(certificateMap: "my-map"))

    when:
      operation.operate([])

    then:
      thrown IllegalArgumentException
  }
}
