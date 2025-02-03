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
import com.netflix.spinnaker.clouddriver.helpers.EnableDisablePercentageCategorizer
import com.netflix.spinnaker.clouddriver.model.ClusterProvider
import com.netflix.spinnaker.clouddriver.model.ServerGroup
import com.netflix.spinnaker.kork.retrofit.Retrofit2SyncCall
import com.netflix.spinnaker.kork.retrofit.exceptions.SpinnakerHttpException
import com.netflix.spinnaker.kork.retrofit.exceptions.SpinnakerNetworkException
import com.netflix.spinnaker.kork.retrofit.exceptions.SpinnakerServerException
import groovy.transform.InheritConstructors
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

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
                                         List<String> instanceIds,
                                         boolean strict = false) {
    updateDiscoveryStatusForInstances(
      description, task, phaseName, discoveryStatus, instanceIds,
      eurekaSupportConfigurationProperties.retryMax, eurekaSupportConfigurationProperties.retryMax, strict
    )
  }


  void updateDiscoveryStatusForInstances(def description,
                                         Task task,
                                         String phaseName,
                                         DiscoveryStatus discoveryStatus,
                                         List<String> instanceIds,
                                         int findApplicationNameRetryMax,
                                         int updateEurekaRetryMax,
                                         boolean strict = false) {

    if (eurekaSupportConfigurationProperties == null) {
      throw new IllegalStateException("eureka configuration not supplied")
    }

    def eureka = getEureka(description.credentials, description.region)
    def random = new Random()
    def instanceDetails = null
    def applicationName = null
    def targetHealthyDeployPercentage = description.targetHealthyDeployPercentage != null ? description.targetHealthyDeployPercentage : 100

    if (targetHealthyDeployPercentage < 0 || targetHealthyDeployPercentage > 100) {
      throw new NumberFormatException("targetHealthyDeployPercentage must be an integer between 0 and 100")
    } else if (targetHealthyDeployPercentage < 100) {
      AbstractEurekaSupport.log.info("Marking ${description.asgName} instances ${discoveryStatus.value} with targetHealthyDeployPercentage ${targetHealthyDeployPercentage}")
    }

    try {
      (applicationName, instanceDetails) = retry(task, phaseName, findApplicationNameRetryMax) { retryCount ->
        def instanceId = instanceIds[random.nextInt(instanceIds.size())]
        task.updateStatus phaseName, "Looking up discovery application name for instance $instanceId (attempt: $retryCount)"

        def details = Retrofit2SyncCall.execute(eureka.getInstanceInfo(instanceId))
        def appName = details?.instance?.app
        if (!appName) {
          throw new RetryableException("Looking up instance application name in Discovery failed for instance ${instanceId} (attempt: $retryCount)")
        }
        return [appName, details]
      }
    } catch (e) {
      if (discoveryStatus == DiscoveryStatus.UP || verifyInstanceAndAsgExist(description.credentials, description.region, null, description.asgName)) {
        throw e
      }
    }

    // In rare (delayed evented) cases, calls to update discovery status may happen against instances that no longer exist
    if (applicationName == null) {
      task.updateStatus phaseName, "Could not find application name in Discovery or AWS, short-circuiting (asg: ${description.asgName}, region: ${description.region})"
      return
    }

    def errors = [:]
    def fatals = []
    List<String> skipped = []
    int index = 0
    for (String instanceId : instanceIds) {
      if (index > 0) {
        sleep eurekaSupportConfigurationProperties.throttleMillis
      }

      if (discoveryStatus == DiscoveryStatus.OUT_OF_SERVICE) {
        if (index % eurekaSupportConfigurationProperties.attemptShortCircuitEveryNInstances == 0) {
          try {
            def hasUpInstances = doesCachedClusterContainDiscoveryStatus(
              clusterProviders, description.account, description.region, description.asgName, "UP"
            )
            if (hasUpInstances.present && !hasUpInstances.get()) {
              // there are no UP instances, we can return early
              task.updateStatus phaseName, "ASG and all instances are '${discoveryStatus.value}', short circuiting."
              break
            }
          } catch (Exception e) {
            def account = description.account
            def region = description.region
            def asgName = description.asgName
            AbstractEurekaSupport.log.error("[$phaseName] - Unable to verify cached discovery status (account: ${account}, region: ${region}, asgName: ${asgName}", e)
          }
        }
      }

      try {
        retry(task, phaseName, updateEurekaRetryMax) { retryCount ->
          task.updateStatus phaseName, "Attempting to mark ${instanceId} as '${discoveryStatus.value}' in discovery (attempt: ${retryCount})."

          if (discoveryStatus == DiscoveryStatus.OUT_OF_SERVICE) {
            Retrofit2SyncCall.execute(eureka.updateInstanceStatus(applicationName, instanceId, discoveryStatus.value))
          } else {
            Retrofit2SyncCall.execute(eureka.resetInstanceStatus(applicationName, instanceId, DiscoveryStatus.OUT_OF_SERVICE.value))
          }
        }
      } catch (SpinnakerServerException e) {
        def alwaysSkippable = e instanceof SpinnakerHttpException && ((SpinnakerHttpException)e).getResponseCode() == 404
        def willSkip = alwaysSkippable || !strict
        def skippingOrNot = willSkip ? "skipping" : "not skipping"

        String errorMessage = "Failed updating status of $instanceId to '$discoveryStatus' in application '$applicationName' in discovery" +
          " and strict=$strict, $skippingOrNot operation."

        // in strict mode, only 404 errors are ignored
        if (!willSkip) {
          errors[instanceId] = e
        } else {
          skipped.add(instanceId)
        }

        task.updateStatus phaseName, errorMessage
      } catch (ex) {
        errors[instanceId] = ex
      }

      if (errors[instanceId]) {
        if (verifyInstanceAndAsgExist(description.credentials, description.region, instanceId, description.asgName)) {
          fatals.add(instanceId)
        } else {
          task.updateStatus phaseName, "Instance '${instanceId}' does not exist and will not be marked as '${discoveryStatus.value}'"
        }
      }

      index++
    }

    if (fatals) {
      Integer requiredInstances = Math.ceil(instanceIds.size() * targetHealthyDeployPercentage / 100D) as Integer
      if (instanceIds.size() - fatals.size() >= requiredInstances) {
        AbstractEurekaSupport.log.info("[$phaseName] - Failed marking discovery $discoveryStatus.value for instances ${fatals} " +
          "but proceeding as ${fatals.size()} failures is within targetHealthyDeployPercentage: ${targetHealthyDeployPercentage}")
      } else {
        task.updateStatus phaseName, "Failed marking instances '${discoveryStatus.value}' in discovery for instances ${errors.keySet()}"
        task.fail()
        AbstractEurekaSupport.log.info("[$phaseName] - Failed marking discovery $discoveryStatus.value for instances ${errors}")
      }
    }

    if (!skipped.isEmpty()) {
      task.addResultObjects([
        ["discoverySkippedInstanceIds": skipped]
      ])
    }
  }

  def retry(Task task, String phaseName, int maxRetries, Closure c) {
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

        AbstractEurekaSupport.log.debug("[$phaseName] - Caught retryable exception", ex)

        retryCount++
        sleep(getDiscoveryRetryMs());
      } catch (SpinnakerServerException e) {
        if (retryCount >= (maxRetries - 1)) {
          throw e
        }

        AbstractEurekaSupport.log.debug("[$phaseName] - Failed calling external service ${e.getMessage()}")

        if ( (e instanceof SpinnakerNetworkException)
          || ((e instanceof SpinnakerHttpException) && ((SpinnakerHttpException) e).responseCode == 406)
          || ((e instanceof SpinnakerHttpException) && ((SpinnakerHttpException) e).responseCode == 404)) {
          retryCount++
          sleep(getDiscoveryRetryMs())
        } else if (e instanceof SpinnakerHttpException && ((SpinnakerHttpException) e).responseCode >= 500) {
          // automatically retry on server errors (but wait a little longer between attempts)
          sleep(getDiscoveryRetryMs() * 10)
          retryCount++
        } else {
          throw e
        }
      } catch (AmazonServiceException ase) {
        if (ase.statusCode == 503) {
          AbstractEurekaSupport.log.debug("[$phaseName] - Failed calling AmazonService", ase)
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

    ServerGroup serverGroup = getCachedServerGroup(clusterProviders, account, region, asgName)

    if (!serverGroup) {
      return Optional.empty()
    }

    def containsDiscoveryStatus = false

    serverGroup*.instances*.health.flatten().each { Map<String, String> health ->
      if (targetDiscoveryStatus.equalsIgnoreCase(health?.eurekaStatus)) {
        containsDiscoveryStatus = true
      }
    }

    return Optional.of(containsDiscoveryStatus)
  }

  static ServerGroup getCachedServerGroup(Collection<ClusterProvider> clusterProviders,
                                          String account,
                                          String region,
                                          String asgName) {
    def matches = (Set<ServerGroup>) clusterProviders.findResults {
      it.getServerGroup(account, region, asgName)
    }

    if (!matches) {
      return null
    }

    def serverGroup = matches.first()
    return serverGroup
  }

  /**
   * Returns a list of instanceIds to disable. Only really used for RollingRedBlack strategy.
   * The list represents the given percentage of "enabled" instances.
   * Enabled instance is one that that has at least 1 health provider indicating it's UP
   * and zero health providers indicating it's DOWN.
   *
   * @param account
   * @param region
   * @param asgName
   * @param instances instanceIDs to pick from
   * @param desiredPercentage (0-100)
   * @return list of instance IDs
   */
  List<String> getInstanceToModify(String account, String region, String asgName, List<String> instances, int desiredPercentage) {
    ServerGroup serverGroup = getCachedServerGroup(clusterProviders, account, region, asgName)
    if (!serverGroup) {
      return []
    }

    Set<String> ineligible = []
    Set<String> eligible = []

    instances.each { instanceId ->
      def instanceInExistingServerGroup = serverGroup.instances.find { it.name == instanceId }

      if (instanceInExistingServerGroup) {
        boolean anyDown = instanceInExistingServerGroup.health?.flatten()?.any {
          Map<String, String> health -> ("down".compareToIgnoreCase(health.state ?: "") == 0)
        }
        boolean anyUp = instanceInExistingServerGroup.health?.flatten()?.any {
          Map<String, String> health -> ("up".compareToIgnoreCase(health.state ?: "") == 0)
        }

        if (anyUp && !anyDown) {
          eligible.add(instanceId)
        } else {
          ineligible.add(instanceId)
        }
      }
    }

    return EnableDisablePercentageCategorizer.getInstancesToModify(
      ineligible as List<String>,
      eligible as List<String>,
      desiredPercentage
    )
  }

  enum DiscoveryStatus {
    UP('UP'),
    OUT_OF_SERVICE('OUT_OF_SERVICE')

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
