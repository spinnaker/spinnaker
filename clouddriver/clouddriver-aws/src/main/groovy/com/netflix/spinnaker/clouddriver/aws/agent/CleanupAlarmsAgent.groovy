/*
 * Copyright 2016 Netflix, Inc.
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

package com.netflix.spinnaker.clouddriver.aws.agent

import software.amazon.awssdk.services.autoscaling.AutoScalingClient
import software.amazon.awssdk.services.autoscaling.model.DescribePoliciesRequest
import software.amazon.awssdk.services.cloudwatch.model.DeleteAlarmsRequest
import software.amazon.awssdk.services.cloudwatch.model.DescribeAlarmsRequest
import software.amazon.awssdk.services.cloudwatch.model.MetricAlarm
import software.amazon.awssdk.services.cloudwatch.model.StateValue
import com.netflix.spinnaker.cats.agent.RunnableAgent
import com.netflix.spinnaker.clouddriver.aws.provider.AwsCleanupProvider
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider
import com.netflix.spinnaker.clouddriver.aws.security.AmazonCredentials
import com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials
import com.netflix.spinnaker.clouddriver.cache.CustomScheduledAgent
import com.netflix.spinnaker.credentials.CredentialsRepository
import groovy.util.logging.Slf4j
import org.joda.time.DateTime

import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

@Slf4j
class CleanupAlarmsAgent implements RunnableAgent, CustomScheduledAgent {
  public static final long POLL_INTERVAL_MILLIS = TimeUnit.HOURS.toMillis(24)
  public static final long DEFAULT_TIMEOUT_MILLIS = TimeUnit.MINUTES.toMillis(20)

  public final Pattern ALARM_NAME_PATTERN = Pattern.compile(alarmsNamePattern)

  final AmazonClientProvider amazonClientProvider
  final CredentialsRepository<NetflixAmazonCredentials> credentialsRepository
  final long pollIntervalMillis
  final long timeoutMillis
  final int daysToLeave
  final String alarmsNamePattern;


  CleanupAlarmsAgent(AmazonClientProvider amazonClientProvider,
                     CredentialsRepository<NetflixAmazonCredentials> credentialsRepository,
                     int daysToLeave,
                     String alarmsNamePattern) {
    this(amazonClientProvider, credentialsRepository, POLL_INTERVAL_MILLIS, DEFAULT_TIMEOUT_MILLIS, daysToLeave, alarmsNamePattern)
  }

  CleanupAlarmsAgent(AmazonClientProvider amazonClientProvider,
                     CredentialsRepository<NetflixAmazonCredentials> credentialsRepository,
                     long pollIntervalMillis,
                     long timeoutMills,
                     int daysToLeave,
                     String alarmsNamePattern) {
    this.amazonClientProvider = amazonClientProvider
    this.credentialsRepository = credentialsRepository
    this.pollIntervalMillis = pollIntervalMillis
    this.timeoutMillis = timeoutMills
    this.daysToLeave = daysToLeave
    this.alarmsNamePattern = alarmsNamePattern
  }

  @Override
  String getAgentType() {
    "${CleanupAlarmsAgent.simpleName}"
  }

  @Override
  String getProviderName() {
    return AwsCleanupProvider.PROVIDER_NAME
  }

  @Override
  void run() {
    getAccounts().each { NetflixAmazonCredentials credentials ->
      credentials.regions.each { AmazonCredentials.AWSRegion region ->
        log.info("Looking for alarms to delete")
        try {
          def cloudWatch = amazonClientProvider.getAmazonCloudWatchV2(credentials, region.name)
          Set<String> attachedAlarms = getAttachedAlarms(amazonClientProvider.getAutoScalingV2(credentials, region.name))
          def describeAlarmsRequest = DescribeAlarmsRequest.builder().stateValue(StateValue.INSUFFICIENT_DATA).build()
          def cutoff = DateTime.now().minusDays(daysToLeave).toDate().toInstant()

          while (true) {
            def result = cloudWatch.describeAlarms(describeAlarmsRequest)

            List<MetricAlarm> alarmsToDelete = result.metricAlarms().findAll {
              it.stateUpdatedTimestamp().isBefore(cutoff) &&
                !attachedAlarms.contains(it.alarmName()) &&
                ALARM_NAME_PATTERN.matcher(it.alarmName()).matches()
            }

            if (alarmsToDelete) {
              // terminate up to 20 alarms at a time (avoids any AWS limits on # of concurrent deletes)
              alarmsToDelete.collect { it.alarmName() }.collate(20).each {
                log.info("Deleting ${it.size()} alarms in ${credentials.name}/${region.name} " +
                  "(alarms: ${it.join(", ")})")
                cloudWatch.deleteAlarms(DeleteAlarmsRequest.builder().alarmNames(it).build())
                Thread.sleep(500)
              }
            }

            if (result.nextToken()) {
              describeAlarmsRequest = describeAlarmsRequest.toBuilder().nextToken(result.nextToken()).build()
            } else {
              break
            }
          }
        } catch (Exception e) {
          log.error("Error occurred while processing alarms for ${credentials.name}/${region.name}: ${e.message}", e)
        }
      }
    }
  }

  private Set<NetflixAmazonCredentials> getAccounts() {
    return credentialsRepository.getAll()
  }

  private static Set<String> getAttachedAlarms(AutoScalingClient autoScaling) {
    Set<String> alarms = []
    def request = DescribePoliciesRequest.builder().build()
    while (true) {
      def result = autoScaling.describePolicies(request)
      alarms.addAll(result.scalingPolicies().collectMany { it.alarms() }*.alarmName())

      if (result.nextToken()) {
        request = request.toBuilder().nextToken(result.nextToken()).build()
      } else {
        break
      }
    }
    alarms
  }
}
