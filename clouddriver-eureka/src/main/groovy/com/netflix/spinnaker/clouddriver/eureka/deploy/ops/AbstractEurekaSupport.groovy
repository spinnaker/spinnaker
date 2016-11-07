/*
 * Copyright 2016 Netflix, Inc.
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
package com.netflix.spinnaker.clouddriver.eureka.deploy.ops

import com.amazonaws.AmazonServiceException
import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.eureka.api.Eureka
import com.netflix.spinnaker.clouddriver.model.ClusterProvider
import com.netflix.spinnaker.clouddriver.model.ServerGroup
import groovy.transform.InheritConstructors
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import retrofit.RetrofitError
import retrofit.client.Response

@Slf4j
@Component
abstract class AbstractEurekaSupport {

  abstract Eureka getEureka(def credentials, String region)

  abstract boolean verifyInstanceAndAsgExist(def credentials,
                                             String region,
                                             String instanceId,
                                             String asgName)

  @Autowired(required = false)
  EurekaSupportConfigurationProperties eurekaSupportConfigurationProperties

  @Autowired
  List<ClusterProvider> clusterProviders

  void updateDiscoveryStatusForInstances(def description,
                                         Task task,
                                         String phaseName,
                                         DiscoveryStatus discoveryStatus,
                                         List<String> instanceIds) {

    if (eurekaSupportConfigurationProperties == null) {
      throw new IllegalStateException("eureka configuration not supplied")
    }

    def eureka = getEureka(description.credentials, description.region)
    def random = new Random()
    def applicationName = null
    try {
      applicationName = retry(task, eurekaSupportConfigurationProperties.retryMax) { retryCount ->
        def instanceId = instanceIds[random.nextInt(instanceIds.size())]
        task.updateStatus phaseName, "Looking up discovery application name for instance $instanceId"

        def instanceDetails = eureka.getInstanceInfo(instanceId)
        def appName = instanceDetails?.instance?.app
        if (!appName) {
          throw new RetryableException("Looking up instance application name in Discovery failed for instance ${instanceId}")
        }
        return appName
      }
    } catch (e) {
      if (discoveryStatus == DiscoveryStatus.Enable || verifyInstanceAndAsgExist(description.credentials, description.region, null, description.asgName)) {
        throw e
      }
    }

    def errors = [:]
    boolean shouldFail = false
    int index = 0
    for (String instanceId : instanceIds) {
      if (index > 0) {
        sleep eurekaSupportConfigurationProperties.throttleMillis
      }

      if (discoveryStatus == DiscoveryStatus.Disable) {
        if (index % eurekaSupportConfigurationProperties.attemptShortCircuitEveryNInstances == 0) {
          try {
            def hasUpInstances = doesCachedClusterContainDiscoveryStatus(
              clusterProviders, description.credentialAccount, description.region, description.asgName, "UP"
            )
            if (hasUpInstances.present && !hasUpInstances.get()) {
              // there are no UP instances, we can return early
              task.updateStatus phaseName, "ASG and all instances are '${discoveryStatus.value}', short circuiting."
              break
            }
          } catch (Exception e) {
            def account = description.credentialAccount
            def region = description.region
            def asgName = description.asgName
            AbstractEurekaSupport.log.error("Unable to verify cached discovery status (account: ${account}, region: ${region}, asgName: ${asgName}", e)
          }
        }
      }

      try {
        retry(task, eurekaSupportConfigurationProperties.retryMax) { retryCount ->
          task.updateStatus phaseName, "Attempting to mark ${instanceId} as '${discoveryStatus.value}' in discovery (attempt: ${retryCount})."

          Response resp = eureka.updateInstanceStatus(applicationName, instanceId, discoveryStatus.value)
          if (resp.status != 200) {
            throw new RetryableException("Non HTTP 200 response from discovery for instance ${instanceId}, will retry (attempt: $retryCount}).")
          }
        }
      } catch (RetrofitError retrofitError) {
        if (retrofitError.response?.status == 404 && discoveryStatus == DiscoveryStatus.Disable) {
          task.updateStatus phaseName, "Could not find ${instanceId} in application $applicationName in discovery, skipping disable operation."
        } else {
          errors[instanceId] = retrofitError
        }
      } catch (ex) {
        errors[instanceId] = ex
      }
      if (errors[instanceId]) {
        if (verifyInstanceAndAsgExist(description.credentials, description.region, instanceId, description.asgName)) {
          shouldFail = true
        } else {
          task.updateStatus phaseName, "Instance '${instanceId}' does not exist and will not be marked as '${discoveryStatus.value}'"
        }
      }
      index++
    }
    if (shouldFail) {
      task.updateStatus phaseName, "Failed marking instances '${discoveryStatus.value}' in discovery for instances ${errors.keySet()}"
      task.fail()
      AbstractEurekaSupport.log.info("Failed marking discovery $discoveryStatus.value for instances ${errors}")
    }
  }

  def retry(Task task, int maxRetries, Closure c) {
    def retryCount = 0
    while (true) {
      try {
        if (!task.status.isFailed()) {
          return c.call(retryCount)
        }
        break
      } catch (RetryableException ex) {
        if (retryCount >= (maxRetries - 1)) {
          throw ex
        }

        retryCount++
        sleep(getDiscoveryRetryMs());
      } catch (RetrofitError re) {
        if (retryCount >= (maxRetries - 1)) {
          throw re
        }

        if (re.kind == RetrofitError.Kind.NETWORK || re.response.status == 404 || re.response.status == 406) {
          retryCount++
          sleep(getDiscoveryRetryMs())
        } else if (re.response.status >= 500) {
          // automatically retry on server errors (but wait a little longer between attempts)
          sleep(getDiscoveryRetryMs() * 10)
          retryCount++
        } else {
          throw re
        }
      } catch (AmazonServiceException ase) {
        if (ase.statusCode == 503) {
          retryCount++
          sleep(getDiscoveryRetryMs())
        } else {
          throw ase
        }
      }
    }
  }

/**
 * Determine whether at least one cached instance in target ASG has a discovery status of <code>targetDiscoveryStatus</code>.
 */
  static Optional<Boolean> doesCachedClusterContainDiscoveryStatus(Collection<ClusterProvider> clusterProviders,
                                                                   String account,
                                                                   String region,
                                                                   String asgName,
                                                                   String targetDiscoveryStatus) {
    def matches = (Set<ServerGroup>) clusterProviders.findResults {
      it.getServerGroup(account, region, asgName)
    }

    if (!matches) {
      return Optional.empty()
    }

    def serverGroup = matches.first()
    def containsDiscoveryStatus = false

    serverGroup*.instances*.health.flatten().each { Map<String, String> health ->
      if (targetDiscoveryStatus.equalsIgnoreCase(health?.eurekaStatus)) {
        containsDiscoveryStatus = true
      }
    }

    return Optional.of(containsDiscoveryStatus)
  }


  enum DiscoveryStatus {
    Enable('UP'),
    Disable('OUT_OF_SERVICE')

    String value

    DiscoveryStatus(String value) {
      this.value = value
    }
  }

  protected long getDiscoveryRetryMs() {
    return eurekaSupportConfigurationProperties.retryIntervalMillis
  }

  @InheritConstructors
  static class DiscoveryNotConfiguredException extends RuntimeException {}

  @InheritConstructors
  static class RetryableException extends RuntimeException {}


}
