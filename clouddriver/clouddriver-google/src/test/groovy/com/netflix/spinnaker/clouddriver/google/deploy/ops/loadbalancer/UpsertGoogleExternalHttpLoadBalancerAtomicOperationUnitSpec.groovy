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

import com.fasterxml.jackson.databind.ObjectMapper
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.compute.Compute
import com.google.api.services.compute.model.ForwardingRule
import com.google.api.services.compute.model.TargetHttpProxy
import com.netflix.spectator.api.DefaultRegistry
import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.google.config.GoogleConfigurationProperties
import com.netflix.spinnaker.clouddriver.google.deploy.GoogleOperationPoller
import com.netflix.spinnaker.clouddriver.google.deploy.SafeRetry
import com.netflix.spinnaker.clouddriver.google.deploy.converters.UpsertGoogleLoadBalancerAtomicOperationConverter
import com.netflix.spinnaker.clouddriver.google.deploy.exception.GoogleOperationException
import com.netflix.spinnaker.clouddriver.google.deploy.description.UpsertGoogleLoadBalancerDescription
import com.netflix.spinnaker.clouddriver.google.model.GoogleHealthCheck
import com.netflix.spinnaker.clouddriver.google.model.GoogleNetwork
import com.netflix.spinnaker.clouddriver.google.model.GoogleSubnet
import com.netflix.spinnaker.clouddriver.google.model.loadbalancing.GoogleInternalHttpLoadBalancer
import com.netflix.spinnaker.clouddriver.google.model.loadbalancing.GoogleLoadBalancerType
import com.netflix.spinnaker.clouddriver.google.provider.view.GoogleNetworkProvider
import com.netflix.spinnaker.clouddriver.google.provider.view.GoogleSubnetProvider
import com.netflix.spinnaker.clouddriver.google.security.FakeGoogleCredentials
import com.netflix.spinnaker.clouddriver.google.security.GoogleNamedAccountCredentials
import com.netflix.spinnaker.clouddriver.google.test.CapturingComputeTransport
import com.netflix.spinnaker.credentials.MapBackedCredentialsRepository
import com.netflix.spinnaker.credentials.NoopCredentialsLifecycleHandler
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Subject

import static com.netflix.spinnaker.clouddriver.google.deploy.ops.loadbalancer.UpsertGoogleHttpLoadBalancerTestConstants.*

class UpsertGoogleExternalHttpLoadBalancerAtomicOperationUnitSpec extends Specification {
  private static final ACCOUNT_NAME = "auto"
  private static final PROJECT_NAME = "my-project"
  private static final REGION = "us-central1"
  private static final EXTERNAL_HTTP_LB = "external-http-lb"
  private static final EXTERNAL_HC = "http-hc"
  private static final EXTERNAL_BS = "default-backend"
  private static final EXTERNAL_CERT = "regional-cert"
  private static final String CERT_URL =
    "//certificatemanager.googleapis.com/projects/${PROJECT_NAME}/locations/${REGION}/certificates/${EXTERNAL_CERT}"

  @Shared GoogleHealthCheck hc
  @Shared def threadSleeperMock = Mock(GoogleOperationPoller.ThreadSleeper)
  @Shared def registry = new DefaultRegistry()
  @Shared SafeRetry safeRetry
  @Shared ObjectMapper objectMapper = new ObjectMapper()

  def setupSpec() {
    TaskRepository.threadLocalTask.set(Mock(Task))
    hc = new GoogleHealthCheck(
      name: EXTERNAL_HC,
      healthCheckType: GoogleHealthCheck.HealthCheckType.HTTP,
      requestPath: "/",
      port: 80,
      checkIntervalSec: 5,
      timeoutSec: 5,
      healthyThreshold: 2,
      unhealthyThreshold: 2
    )
    safeRetry = SafeRetry.withoutDelay()
  }

  void "resolveSubnet accepts a same-project proxy-only subnet stored as a bare local network name"() {
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
      // GoogleSubnetProvider.deriveNetworkId reduces a same-project network to its bare local name,
      // so this is the shape the cache actually produces (the earlier full-URL fixture never
      // exercised the real path).
      def proxyOnlySubnet = new GoogleSubnet(
        account: ACCOUNT_NAME,
        region: REGION,
        network: "default",
        purpose: "REGIONAL_MANAGED_PROXY")
      subnetProvider.getAllMatchingKeyPattern("gce:subnets:*:${ACCOUNT_NAME}:${REGION}") >> ([proxyOnlySubnet] as Set)

    when:
      def resolvedSubnet = operation.resolveSubnet(network)

    then:
      resolvedSubnet == null
  }

  void "resolveSubnet accepts a proxy-only subnet whose network is a full self-link URL"() {
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
        network: "default")
      @Subject def operation = new UpsertGoogleExternalHttpLoadBalancerAtomicOperation(description)
      operation.googleSubnetProvider = subnetProvider
      def network = new GoogleNetwork(
        name: "default",
        id: "default",
        selfLink: "https://compute.googleapis.com/compute/v1/projects/${PROJECT_NAME}/global/networks/default")
      // GoogleSubnetProvider.deriveNetworkId qualifies an XPN host-project network as
      // "<project>/<name>", so a same-named network in another project must not be accepted.
      def proxyOnlySubnet = new GoogleSubnet(
        account: ACCOUNT_NAME,
        region: REGION,
        network: "other-project/default",
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

  void "deleteRegionalListenerIfOwned rejects listener from another URL map"() {
    setup:
      def compute = Mock(Compute)
      def forwardingRules = Mock(Compute.ForwardingRules)
      def forwardingRulesGet = Mock(Compute.ForwardingRules.Get)
      def targetHttpProxies = Mock(Compute.RegionTargetHttpProxies)
      def targetHttpProxiesGet = Mock(Compute.RegionTargetHttpProxies.Get)
      @Subject def operation = new UpsertGoogleExternalHttpLoadBalancerAtomicOperation(
        new UpsertGoogleLoadBalancerDescription())
      operation.registry = new com.netflix.spectator.api.DefaultRegistry()
      operation.safeRetry = com.netflix.spinnaker.clouddriver.google.deploy.SafeRetry.withoutDelay()

    when:
      operation.deleteRegionalListenerIfOwned(
        compute,
        PROJECT_NAME,
        REGION,
        "unowned-listener",
        "expected-url-map")

    then:
      1 * compute.forwardingRules() >> forwardingRules
      1 * forwardingRules.get(PROJECT_NAME, REGION, "unowned-listener") >> forwardingRulesGet
      1 * forwardingRulesGet.execute() >> new ForwardingRule(
        name: "unowned-listener",
        loadBalancingScheme: "EXTERNAL_MANAGED",
        target: "projects/${PROJECT_NAME}/regions/${REGION}/targetHttpProxies/unowned-proxy")

      1 * compute.regionTargetHttpProxies() >> targetHttpProxies
      1 * targetHttpProxies.get(PROJECT_NAME, REGION, "unowned-proxy") >> targetHttpProxiesGet
      1 * targetHttpProxiesGet.execute() >> new TargetHttpProxy(urlMap: "projects/${PROJECT_NAME}/regions/${REGION}/urlMaps/other-url-map")

      thrown GoogleOperationException
      0 * forwardingRules.delete(_, _, _)
      0 * targetHttpProxies.delete(_, _, _)
  }

  void "create path serializes regional external managed HTTP(S) resources through compute transport"() {
    setup:
      CapturingComputeTransport transport = new CapturingComputeTransport()
      Compute compute = new Compute.Builder(
        transport, GsonFactory.getDefaultInstance(), null).setApplicationName("test").build()
      def credentialsRepo = new MapBackedCredentialsRepository(
        GoogleNamedAccountCredentials.CREDENTIALS_TYPE, new NoopCredentialsLifecycleHandler<>())
      credentialsRepo.save(new GoogleNamedAccountCredentials.Builder()
        .name(ACCOUNT_NAME)
        .project(PROJECT_NAME)
        .compute(compute)
        .credentials(new FakeGoogleCredentials())
        .build())
      def converter = new UpsertGoogleLoadBalancerAtomicOperationConverter(
        credentialsRepository: credentialsRepo)
      def googleNetworkProviderMock = Mock(GoogleNetworkProvider)
      def googleSubnetProviderMock = Mock(GoogleSubnetProvider)
      def description = converter.convertDescription([
        accountName: ACCOUNT_NAME,
        loadBalancerType: GoogleLoadBalancerType.EXTERNAL_MANAGED,
        loadBalancerName: EXTERNAL_HTTP_LB,
        region: REGION,
        network: "default",
        networkTier: "STANDARD",
        portRange: SSL_PROXY_PORT_RANGE,
        certificate: CERT_URL,
        defaultService: [
          name: EXTERNAL_BS,
          backends: [],
          healthCheck: hc,
          sessionAffinity: "NONE",
        ],
        hostRules: null,
      ])
      @Subject def operation = new UpsertGoogleExternalHttpLoadBalancerAtomicOperation(description)
      setGoogleOperationPoller(operation, new GoogleOperationPoller(
        googleConfigurationProperties: new GoogleConfigurationProperties(),
        threadSleeper: threadSleeperMock,
        registry: registry,
        safeRetry: safeRetry))
      operation.googleNetworkProvider = googleNetworkProviderMock
      operation.googleSubnetProvider = googleSubnetProviderMock
      operation.registry = registry
      operation.safeRetry = safeRetry

    when:
      operation.operate([])

    then:
      1 * googleNetworkProviderMock.getAllMatchingKeyPattern("gce:networks:default:${ACCOUNT_NAME}:global") >> [
        new GoogleNetwork(
          name: "default",
          selfLink: "https://compute.googleapis.com/compute/v1/projects/${PROJECT_NAME}/global/networks/default")
      ]
      1 * googleSubnetProviderMock.getAllMatchingKeyPattern("gce:subnets:*:${ACCOUNT_NAME}:${REGION}") >> [
        new GoogleSubnet(account: ACCOUNT_NAME, region: REGION, network: "default", purpose: "REGIONAL_MANAGED_PROXY")
      ]

      def forwardingRuleBody = objectMapper.readTree(
        transport.findPostTo("/forwardingRules").orElseThrow().body())
      forwardingRuleBody.path("loadBalancingScheme").asText() == "EXTERNAL_MANAGED"
      forwardingRuleBody.path("network").asText() ==
        "https://compute.googleapis.com/compute/v1/projects/${PROJECT_NAME}/global/networks/default"
      forwardingRuleBody.path("networkTier").asText() == "STANDARD"
      !forwardingRuleBody.has("subnetwork")
      forwardingRuleBody.path("IPProtocol").asText() == "TCP"
      forwardingRuleBody.path("portRange").asText() == SSL_PROXY_PORT_RANGE
      forwardingRuleBody.path("target").asText().contains("/targetHttpsProxies/")
      transport.findPostTo("/regions/${REGION}/forwardingRules").isPresent()

      def httpsProxyBody = objectMapper.readTree(
        transport.findPostTo("/targetHttpsProxies").orElseThrow().body())
      httpsProxyBody.path("urlMap").asText().contains("/urlMaps/${EXTERNAL_HTTP_LB}")
      httpsProxyBody.path("sslCertificates").size() == 1
      httpsProxyBody.path("sslCertificates").get(0).asText() == CERT_URL
      !httpsProxyBody.has("certificateMap")

      def urlMapBody = objectMapper.readTree(transport.findPostTo("/urlMaps").orElseThrow().body())
      urlMapBody.path("defaultService").asText().contains("/backendServices/${EXTERNAL_BS}")

      def backendServiceBody = objectMapper.readTree(
        transport.findPostTo("/backendServices").orElseThrow().body())
      backendServiceBody.path("loadBalancingScheme").asText() == "EXTERNAL_MANAGED"
      backendServiceBody.path("healthChecks").get(0).asText().contains("/healthChecks/${EXTERNAL_HC}")

      def healthCheckBody = objectMapper.readTree(
        transport.findPostTo("/healthChecks").orElseThrow().body())
      healthCheckBody.path("name").asText() == EXTERNAL_HC
      healthCheckBody.has("httpHealthCheck")

      def writeOrder = transport.writeRequests*.url()
      writeOrder.findIndexOf { it.contains("/healthChecks") } <
        writeOrder.findIndexOf { it.contains("/backendServices") }
      writeOrder.findIndexOf { it.contains("/backendServices") } <
        writeOrder.findIndexOf { it.contains("/urlMaps") }
      writeOrder.findIndexOf { it.contains("/urlMaps") } <
        writeOrder.findIndexOf { it.contains("/targetHttpsProxies") }
      writeOrder.findIndexOf { it.contains("/targetHttpsProxies") } <
        writeOrder.findIndexOf { it.contains("/forwardingRules") }
  }

  void "operate accepts omitted optional external managed fields on create without NPE"() {
    setup:
      CapturingComputeTransport transport = new CapturingComputeTransport()
      Compute compute = new Compute.Builder(
        transport, GsonFactory.getDefaultInstance(), null).setApplicationName("test").build()
      def credentialsRepo = new MapBackedCredentialsRepository(
        GoogleNamedAccountCredentials.CREDENTIALS_TYPE, new NoopCredentialsLifecycleHandler<>())
      credentialsRepo.save(new GoogleNamedAccountCredentials.Builder()
        .name(ACCOUNT_NAME)
        .project(PROJECT_NAME)
        .compute(compute)
        .credentials(new FakeGoogleCredentials())
        .build())
      def converter = new UpsertGoogleLoadBalancerAtomicOperationConverter(
        credentialsRepository: credentialsRepo)
      def googleNetworkProviderMock = Mock(GoogleNetworkProvider)
      def googleSubnetProviderMock = Mock(GoogleSubnetProvider)
      def description = converter.convertDescription([
        accountName: ACCOUNT_NAME,
        loadBalancerType: GoogleLoadBalancerType.EXTERNAL_MANAGED,
        loadBalancerName: "external-http-minimal",
        region: REGION,
        network: "default",
        portRange: PORT_RANGE,
        defaultService: [
          name: "minimal-backend",
          backends: [],
          healthCheck: hc,
        ],
        hostRules: null,
      ])
      @Subject def operation = new UpsertGoogleExternalHttpLoadBalancerAtomicOperation(description)
      setGoogleOperationPoller(operation, new GoogleOperationPoller(
        googleConfigurationProperties: new GoogleConfigurationProperties(),
        threadSleeper: threadSleeperMock,
        registry: registry,
        safeRetry: safeRetry))
      operation.googleNetworkProvider = googleNetworkProviderMock
      operation.googleSubnetProvider = googleSubnetProviderMock
      operation.registry = registry
      operation.safeRetry = safeRetry

    when:
      operation.operate([])

    then:
      1 * googleNetworkProviderMock.getAllMatchingKeyPattern(_) >> [
        new GoogleNetwork(
          name: "default",
          selfLink: "https://compute.googleapis.com/compute/v1/projects/${PROJECT_NAME}/global/networks/default")
      ]
      1 * googleSubnetProviderMock.getAllMatchingKeyPattern(_) >> [
        new GoogleSubnet(account: ACCOUNT_NAME, region: REGION, network: "default", purpose: "REGIONAL_MANAGED_PROXY")
      ]
      transport.findPostTo("/targetHttpProxies").isPresent()
      !transport.findPostTo("/targetHttpsProxies").isPresent()
  }

  void "deleteRegionalListenerIfOwned ignores missing listener"() {
    setup:
      def compute = Mock(Compute)
      def forwardingRules = Mock(Compute.ForwardingRules)
      def forwardingRulesGet = Mock(Compute.ForwardingRules.Get)
      @Subject def operation = new UpsertGoogleExternalHttpLoadBalancerAtomicOperation(
        new UpsertGoogleLoadBalancerDescription())
      operation.registry = new com.netflix.spectator.api.DefaultRegistry()
      operation.safeRetry = com.netflix.spinnaker.clouddriver.google.deploy.SafeRetry.withoutDelay()

    when:
      def result = operation.deleteRegionalListenerIfOwned(
        compute,
        PROJECT_NAME,
        REGION,
        "already-deleted-listener",
        "expected-url-map")

    then:
      1 * compute.forwardingRules() >> forwardingRules
      1 * forwardingRules.get(PROJECT_NAME, REGION, "already-deleted-listener") >> forwardingRulesGet
      1 * forwardingRulesGet.execute() >> null

      result == null
      0 * forwardingRules.delete(_, _, _)
      0 * compute.regionTargetHttpProxies()
      0 * compute.regionTargetHttpsProxies()
  }

  private static void setGoogleOperationPoller(
    UpsertGoogleExternalHttpLoadBalancerAtomicOperation operation,
    GoogleOperationPoller poller) {
    def pollerField =
      UpsertGoogleInternalHttpLoadBalancerAtomicOperation.getDeclaredField("googleOperationPoller")
    pollerField.accessible = true
    pollerField.set(operation, poller)
  }
}
