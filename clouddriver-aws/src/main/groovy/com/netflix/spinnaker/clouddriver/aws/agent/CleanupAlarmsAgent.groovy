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

import com.amazonaws.services.autoscaling.AmazonAutoScaling
import com.amazonaws.services.cloudwatch.model.DeleteAlarmsRequest
import com.amazonaws.services.cloudwatch.model.DescribeAlarmsRequest
import com.amazonaws.services.cloudwatch.model.MetricAlarm
import com.amazonaws.services.cloudwatch.model.StateValue
import com.amazonaws.services.gamelift.model.DescribeScalingPoliciesRequest
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

        def cloudWatch = amazonClientProvider.getCloudWatch(credentials, region.name)
        Set<String> attachedAlarms = getAttachedAlarms(amazonClientProvider.getAutoScaling(credentials, region.name))
        def describeAlarmsRequest = new DescribeAlarmsRequest().withStateValue(StateValue.INSUFFICIENT_DATA)

        while (true) {
          def result = cloudWatch.describeAlarms(describeAlarmsRequest)

          List<MetricAlarm> alarmsToDelete = result.metricAlarms.findAll {
            it.stateUpdatedTimestamp.before(DateTime.now().minusDays(daysToLeave).toDate()) &&
              !attachedAlarms.contains(it.alarmName) &&
              ALARM_NAME_PATTERN.matcher(it.alarmName).matches()
          }

          if (alarmsToDelete) {
            // terminate up to 20 alarms at a time (avoids any AWS limits on # of concurrent deletes)
            alarmsToDelete.collate(20).each {
              log.info("Deleting ${it.size()} alarms in ${credentials.name}/${region.name} " +
                "(alarms: ${it.alarmName.join(", ")})")
              cloudWatch.deleteAlarms(new DeleteAlarmsRequest().withAlarmNames(it.alarmName))
              Thread.sleep(500)
            }

          }

          if (result.nextToken) {
            describeAlarmsRequest.withNextToken(result.nextToken)
          } else {
            break
          }
        }
      }
    }
  }

  private Set<NetflixAmazonCredentials> getAccounts() {
    return credentialsRepository.getAll()
  }

  private static Set<String> getAttachedAlarms(AmazonAutoScaling autoScaling) {
    Set<String> alarms = []
    def request = new DescribeScalingPoliciesRequest()
    while (true) {
      def result = autoScaling.describePolicies()
      alarms.addAll(result.scalingPolicies.alarms.alarmName.flatten())

      if (result.nextToken) {
        request.withNextToken(result.nextToken)
      } else {
        break
      }
    }
    alarms
  }
}
