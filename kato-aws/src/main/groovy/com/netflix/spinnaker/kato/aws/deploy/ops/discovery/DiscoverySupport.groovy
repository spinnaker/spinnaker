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

import com.amazonaws.services.ec2.AmazonEC2
import com.amazonaws.services.ec2.model.DescribeInstancesRequest
import com.google.common.annotations.VisibleForTesting
import com.netflix.spinnaker.kato.aws.deploy.description.EnableDisableInstanceDiscoveryDescription
import com.netflix.spinnaker.kato.aws.services.AsgService
import com.netflix.spinnaker.kato.aws.services.RegionScopedProviderFactory
import com.netflix.spinnaker.kato.data.task.Task
import groovy.transform.InheritConstructors
import groovy.transform.PackageScope
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.HttpServerErrorException
import org.springframework.web.client.ResourceAccessException
import org.springframework.web.client.RestTemplate

import java.util.regex.Pattern

@Slf4j
@Component
class DiscoverySupport {
  private static final long THROTTLE_MS = 150

  static final int DISCOVERY_RETRY_MAX = 10
  private static final long DEFAULT_DISCOVERY_RETRY_MS = 3000

  @Autowired
  RestTemplate restTemplate

  @Value('${discovery.retry.max:#{T(com.netflix.spinnaker.kato.aws.deploy.ops.discovery.DiscoverySupport).DISCOVERY_RETRY_MAX}}')
  int discoveryRetry = DISCOVERY_RETRY_MAX


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

    def region = description.region
    def discovery = description.credentials.discovery.replaceAll(Pattern.quote('{{region}}'), region)

    def regionScopedProvider = regionScopedProviderFactory.forRegion(description.credentials, description.region)
    def amazonEC2 = regionScopedProviderFactory.amazonClientProvider.getAmazonEC2(description.credentials, region)
    def asgService = regionScopedProvider.asgService


    def random = new Random()
    def applicationName = retry(task, discoveryRetry) { retryCount ->
      def instanceId = instanceIds[random.nextInt(instanceIds.size())]
      task.updateStatus phaseName, "Looking up discovery application name for instance $instanceId"
      def instanceDetails = restTemplate.getForEntity("${discovery}/v2/instances/${instanceId}", Map)
      def applicationName = instanceDetails?.body?.instance?.app
      if (!applicationName) {
        throw new RetryableException("Looking up instance application name in Discovery failed for instance ${instanceId}")
      }
      return applicationName
    }

    instanceIds.eachWithIndex { instanceId, index ->
      if (index > 0) {
        sleep THROTTLE_MS
      }

      def errors = [:]
      try {
        retry(task, discoveryRetry) { retryCount ->
          task.updateStatus phaseName, "Attempting to mark ${instanceId} as '${discoveryStatus.value}' in discovery (attempt: ${retryCount})."

          if (discoveryStatus == DiscoveryStatus.Disable && !verifyInstanceAndAsgExist(amazonEC2, asgService, instanceId, description.asgName)) {
            task.updateStatus phaseName, "Instance (${instanceId}) or ASG (${description.asgName}) no longer exist, skipping"
            return
          }

          restTemplate.put("${discovery}/v2/apps/${applicationName}/${instanceId}/status?value=${discoveryStatus.value}", [:])
          task.updateStatus phaseName, "Marked ${instanceId} in application $applicationName as '${discoveryStatus.value}' in discovery."
        }
      } catch (ex) {
        errors[instanceId] = ex
      }
      if (errors) {
        task.updateStatus phaseName, "Failed marking instances '${discoveryStatus.value}' in discovery for instances ${errors.keySet()}"
        task.fail()
        log.info("Failed marking discovery $discoveryStatus.value for instances ${errors}")
      }
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
      } catch (RetryableException | ResourceAccessException ex) {
        if (retryCount >= (maxRetries - 1)) {
          throw ex
        }

        retryCount++
        sleep(getDiscoveryRetryMs());

      } catch (HttpServerErrorException | HttpClientErrorException e) {
        if (retryCount >= (maxRetries - 1)) {
          throw e
        }

        if (e.statusCode.is5xxServerError()) {
          // automatically retry on server errors (but wait a little longer between attempts)
          sleep(getDiscoveryRetryMs() * 10)
          retryCount++
        } else if (e.statusCode == HttpStatus.NOT_FOUND) {
          // automatically retry on 404's
          retryCount++
          sleep(getDiscoveryRetryMs())
        } else {
          throw e
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

    def instances = amazonEC2.describeInstances(
      new DescribeInstancesRequest().withInstanceIds(instanceId)
    ).reservations*.instances.flatten()
    if (!instances.find { it.instanceId == instanceId }) {
      return false
    }
    log.info("Instance (${instanceId}) exists")

    return true
  }

  @InheritConstructors
  static class DiscoveryNotConfiguredException extends RuntimeException {}

  @InheritConstructors
  static class RetryableException extends RuntimeException {}
}
