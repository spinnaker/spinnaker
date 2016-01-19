/*
 * Copyright 2015 The original authors.
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

package com.netflix.spinnaker.clouddriver.azure.common

import com.microsoft.azure.management.network.models.LoadBalancer
import com.netflix.spinnaker.clouddriver.azure.security.AzureCredentials
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider
import com.netflix.spinnaker.clouddriver.azure.config.AzureConfigurationProperties
import com.netflix.spinnaker.clouddriver.azure.security.AzureNamedAccountCredentials
import com.netflix.spinnaker.clouddriver.azure.resources.loadbalancer.model.AzureLoadBalancerDescription
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired

import javax.annotation.PostConstruct
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReentrantLock

@Slf4j
class AzureResourceRetriever {

  @Autowired
  AccountCredentialsProvider accountCredentialsProvider

  @Autowired
  AzureConfigurationProperties azureConfigurationProperties

  protected Lock cacheLock = new ReentrantLock()

  Map<String, Map<String, Collection<AzureLoadBalancerDescription>>> applicationLoadBalancerMap;

  @PostConstruct
  void init() {
    log.info "Initializing AzureResourceRetriever thread..."

    int initialTimeToLoadSeconds = 15

    Executors.newSingleThreadScheduledExecutor().scheduleAtFixedRate({
      try {
        load()
      } catch (Throwable t) {
        t.printStackTrace()
      }
    }, initialTimeToLoadSeconds, azureConfigurationProperties.pollingIntervalSeconds, TimeUnit.SECONDS)
  }

  private void load() {
    log.info "Loading Azure resources...";

    cacheLock.lock();

    log.info "Acquired cacheLock for reloading cache.";

    try {
      def tmpLoadBalancerMap = new HashMap<String, Map<String, Set<AzureLoadBalancerDescription>>>()

      getAllAzureCredentials().each() {
        def accountName = it.key;
        def account = it.value;

        AzureCredentials azureCredentials = (AzureCredentials) account

        def networkClient = azureCredentials.getNetworkClient()

        def accountLoadBalancers = networkClient.getLoadBalancersAll(azureCredentials)

        tmpLoadBalancerMap[accountName] = new HashMap<String, Set<AzureLoadBalancerDescription>>()
        for (LoadBalancer lb : accountLoadBalancers) {
          String appName = getAppNameFromLoadBalancer(lb.name)
          if (!appName.isEmpty()) {
            if (!tmpLoadBalancerMap[accountName].containsKey(appName)) {
              tmpLoadBalancerMap[accountName].put(appName, new HashSet<AzureLoadBalancerDescription>())
            }
            def loadBalancer = getDescriptionForLoadBalancer(lb)
            loadBalancer.appName = appName
            tmpLoadBalancerMap[accountName][appName].add(loadBalancer)
          }
        }
      }
      applicationLoadBalancerMap = tmpLoadBalancerMap;

      log.info "Done loading new version of data"
    }
    finally {
      cacheLock.unlock();
    }

    log.info "Finished loading Azure resources.";

  }

  public Collection<AzureLoadBalancerDescription> getLoadBalancersForApplication(String applicationName) {
    applicationLoadBalancerMap.each() {
      def applications = it.value
      if (applications.containsKey(applicationName)) {
        return applications[applicationName]
      }
    }
    null
  }

  public AzureLoadBalancerDescription getLoadBalancer(String applicationName, String loadBalancerName) {
    applicationLoadBalancerMap.each() {
      def applications = it.value
      if (applications.containsKey(applicationName)) {
        for (def loadBalancer : applications[applicationName]) {
          if (loadBalancer.loadBalancerName == loadBalancerName) {
            return loadBalancer
          }
        }
      }
    }
    null
  }

  public Collection<AzureLoadBalancerDescription> getLoadBalancersForApplication(String accountName, String applicationName) {
    if (applicationLoadBalancerMap[accountName].containsKey(applicationName)) {
      return applicationLoadBalancerMap[accountName][applicationName]
    }
    null
  }

  public AzureLoadBalancerDescription getLoadBalancer(String accountName, String applicationName, String loadBalancerName) {
    def applicationLoadBalancers = applicationLoadBalancerMap[accountName][applicationName]
    for (AzureLoadBalancerDescription loadBalancer : applicationLoadBalancers) {
      if (loadBalancer.loadBalancerName == loadBalancerName) {
        return loadBalancer
      }
    }
    null
  }

  public static String getAppNameFromLoadBalancer(String name) {
    String appName = ""
    int idx = name.indexOf('-')
    if (idx > 0) {
      appName = name.substring(0, idx)
    }
    appName
  }

  private Map<String, AzureCredentials> getAllAzureCredentials() {
    def azureAccountNameCredentials = new HashMap<String, AzureCredentials>();

    for (def accountCredentials : accountCredentialsProvider.getAll()) {
      if (accountCredentials instanceof AzureNamedAccountCredentials) {
        try {
          def accountName = accountCredentials.getName();

          azureAccountNameCredentials[accountName] = accountCredentials.getCredentials();
        }
        catch (Exception e) {
          log.error "Squashed exception ${e.getClass().getName()} thrown by ${accountCredentials}."
        }
      }
    }
    return azureAccountNameCredentials;
  }

  private AzureLoadBalancerDescription getDescriptionForLoadBalancer(LoadBalancer azureLoadBalancer) {
    AzureLoadBalancerDescription description = new AzureLoadBalancerDescription(loadBalancerName: azureLoadBalancer.name)
    description.stack = azureLoadBalancer.tags["stack"]
    description.detail = azureLoadBalancer.tags["detail"]
    description.region = azureLoadBalancer.location

    for (def rule : azureLoadBalancer.loadBalancingRules) {
      def r = new AzureLoadBalancerDescription.AzureLoadBalancingRule(ruleName: rule.name)
      r.externalPort = rule.frontendPort
      r.backendPort = rule.backendPort
      r.probeName = getResourceNameFromID(rule.probe.id)
      r.persistence = rule.loadDistribution;
      r.idleTimeout = rule.idleTimeoutInMinutes;

      if (rule.protocol.toLowerCase() == "udp") {
        r.protocol = AzureLoadBalancerDescription.AzureLoadBalancingRule.AzureLoadBalancingRulesType.UDP
      } else {
        r.protocol = AzureLoadBalancerDescription.AzureLoadBalancingRule.AzureLoadBalancingRulesType.TCP
      }
      description.loadBalancingRules.add(r)
    }

    // Add the probes
    for (def probe : azureLoadBalancer.probes) {
      def p = new AzureLoadBalancerDescription.AzureLoadBalancerProbe()
      p.probeName = probe.name
      p.probeInterval = probe.intervalInSeconds
      p.probePath = probe.requestPath
      p.probePort = probe.port
      p.unhealthyThreshold = probe.numberOfProbes
      if (probe.protocol.toLowerCase() == "tcp") {
        p.probeProtocol = AzureLoadBalancerDescription.AzureLoadBalancerProbe.AzureLoadBalancerProbesType.TCP
      } else {
        p.probeProtocol = AzureLoadBalancerDescription.AzureLoadBalancerProbe.AzureLoadBalancerProbesType.HTTP
      }
      description.probes.add(p)
    }

    for (def natRule : azureLoadBalancer.inboundNatRules) {
      def n = new AzureLoadBalancerDescription.AzureLoadBalancerInboundNATRule(ruleName: natRule.name)
      description.inboundNATRules.add(n)
    }

    description
  }

  private String getResourceNameFromID(String resourceId) {
    int idx = resourceId.lastIndexOf('/')
    if (idx > 0) {
      return resourceId.substring(idx + 1)
    }
    resourceId
  }
}
