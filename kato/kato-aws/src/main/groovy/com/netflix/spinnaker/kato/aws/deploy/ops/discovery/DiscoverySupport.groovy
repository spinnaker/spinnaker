/*
 * Copyright 2014 Netflix, Inc.
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

package com.netflix.spinnaker.kato.aws.deploy.ops.discovery

import com.amazonaws.AmazonServiceException
import com.amazonaws.services.ec2.AmazonEC2
import com.amazonaws.services.ec2.model.DescribeInstancesRequest
import com.google.common.annotations.VisibleForTesting
import com.netflix.spinnaker.kato.aws.deploy.description.EnableDisableInstanceDiscoveryDescription
import com.netflix.spinnaker.kato.aws.services.AsgService
import com.netflix.spinnaker.kato.aws.services.RegionScopedProviderFactory
import com.netflix.spinnaker.kato.data.task.Task
import com.netflix.spinnaker.oort.model.ClusterProvider
import com.netflix.spinnaker.oort.model.ServerGroup
import groovy.transform.InheritConstructors
import groovy.transform.PackageScope
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import retrofit.RetrofitError
import retrofit.client.Response

@Slf4j
@Component
class DiscoverySupport {
  private static final long THROTTLE_MS = 150

  static final int ATTEMPT_SHORT_CIRCUIT_EVERY = 100
  static final int DISCOVERY_RETRY_MAX = 10
  private static final long DEFAULT_DISCOVERY_RETRY_MS = 3000

  @Value('${discovery.retry.max:#{T(com.netflix.spinnaker.kato.aws.deploy.ops.discovery.DiscoverySupport).DISCOVERY_RETRY_MAX}}')
  int discoveryRetry = DISCOVERY_RETRY_MAX

  @Value('${discovery.attemptShortCurcuitEvery:#{T(com.netflix.spinnaker.kato.aws.deploy.ops.discovery.DiscoverySupport).ATTEMPT_SHORT_CIRCUIT_EVERY}}')
  int attemptShortCircuitEveryNInstances = ATTEMPT_SHORT_CIRCUIT_EVERY

  @Autowired
  List<ClusterProvider> clusterProviders

  @Autowired
  RegionScopedProviderFactory regionScopedProviderFactory

  void updateDiscoveryStatusForInstances(EnableDisableInstanceDiscoveryDescription description,
                                         Task task,
                                         String phaseName,
                                         DiscoveryStatus discoveryStatus,
                                         List<String> instanceIds) {
    if (!description.credentials.discoveryEnabled) {
      throw new DiscoveryNotConfiguredException()
    }

    def regionScopedProvider = regionScopedProviderFactory.forRegion(description.credentials, description.region)
    def amazonEC2 = regionScopedProvider.getAmazonEC2()
    def asgService = regionScopedProvider.asgService

    def eureka = regionScopedProvider.eureka

    def random = new Random()
    def applicationName = null

    try {
      applicationName = retry(task, discoveryRetry) { retryCount ->
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
      if (discoveryStatus == DiscoveryStatus.Enable || verifyInstanceAndAsgExist(amazonEC2, asgService, null, description.asgName)) {
        throw e
      }
    }

    int index = 0
    for (String instanceId : instanceIds) {
      if (index > 0) {
        sleep THROTTLE_MS
      }

      if (discoveryStatus == DiscoveryStatus.Disable) {
        if (index % attemptShortCircuitEveryNInstances == 0) {
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
            log.error("Unable to verify cached discovery status (account: ${account}, region: ${region}, asgName: ${asgName}", e)
          }
        }
      }

      def errors = [:]
      try {
        retry(task, discoveryRetry) { retryCount ->
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
      if (errors) {
        task.updateStatus phaseName, "Failed marking instances '${discoveryStatus.value}' in discovery for instances ${errors.keySet()}"
        task.fail()
        log.info("Failed marking discovery $discoveryStatus.value for instances ${errors}")
      }

      index++
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
      if (targetDiscoveryStatus.equalsIgnoreCase(health?.discoveryStatus)) {
        containsDiscoveryStatus = true
      }
    }

    return Optional.of(containsDiscoveryStatus)
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

  protected long getDiscoveryRetryMs() {
    return DEFAULT_DISCOVERY_RETRY_MS
  }

  enum DiscoveryStatus {
    Enable('UP'),
    Disable('OUT_OF_SERVICE')

    String value

    DiscoveryStatus(String value) {
      this.value = value
    }
  }

  @VisibleForTesting
  @PackageScope
  boolean verifyInstanceAndAsgExist(AmazonEC2 amazonEC2,
                                    AsgService asgService,
                                    String instanceId,
                                    String asgName) {
    if (asgName) {
      def autoScalingGroup = asgService.getAutoScalingGroup(asgName)
      if (!autoScalingGroup || autoScalingGroup.status) {
        // ASG does not exist or is in the process of being deleted
        return false
      }
      log.info("AutoScalingGroup (${asgName}) exists")

      if (!autoScalingGroup.instances.find { it.instanceId == instanceId }) {
        return false
      }
      log.info("AutoScalingGroup (${asgName}) contains instance (${instanceId})")

      if (autoScalingGroup.desiredCapacity == 0) {
        return false
      }
      log.info("AutoScalingGroup (${asgName}) has non-zero desired capacity (desiredCapacity: ${autoScalingGroup.desiredCapacity})")
    }

    if (instanceId) {
      def instances = amazonEC2.describeInstances(
        new DescribeInstancesRequest().withInstanceIds(instanceId)
      ).reservations*.instances.flatten()
      if (!instances.find { it.instanceId == instanceId }) {
        return false
      }
      log.info("Instance (${instanceId}) exists")
    }

    return true
  }

  @InheritConstructors
  static class DiscoveryNotConfiguredException extends RuntimeException {}

  @InheritConstructors
  static class RetryableException extends RuntimeException {}
}
