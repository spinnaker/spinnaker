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

package com.netflix.spinnaker.clouddriver.aws.provider.view

import software.amazon.awssdk.services.cloudwatch.CloudWatchClient
import software.amazon.awssdk.services.cloudwatch.model.Dimension
import software.amazon.awssdk.services.cloudwatch.model.GetMetricStatisticsRequest
import software.amazon.awssdk.services.cloudwatch.model.GetMetricStatisticsResponse
import software.amazon.awssdk.services.cloudwatch.model.ListMetricsResponse
import software.amazon.awssdk.services.cloudwatch.model.Metric
import com.netflix.spinnaker.clouddriver.aws.AmazonCloudProvider
import com.netflix.spinnaker.clouddriver.aws.model.AmazonMetricDescriptor
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider
import com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials
import com.netflix.spinnaker.credentials.CredentialsRepository
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Subject

class AmazonCloudMetricProviderSpec extends Specification {

  @Subject
  AmazonCloudMetricProvider provider

  @Shared
  CloudWatchClient cloudWatch

  def setup() {
    cloudWatch = Mock(CloudWatchClient)
    CredentialsRepository credentialsRepository = Stub(CredentialsRepository) {
      getOne(_) >> Stub(NetflixAmazonCredentials)
    }
    AmazonClientProvider amazonClientProvider = Stub(AmazonClientProvider) {
      getAmazonCloudWatchV2(_, _) >> cloudWatch
    }
    AmazonCloudProvider amazonCloudProvider = Mock(AmazonCloudProvider)
    provider = new AmazonCloudMetricProvider(amazonClientProvider, credentialsRepository, amazonCloudProvider)
  }

  void "getMetric returns null when none found"() {
    when:
    def result = provider.getMetricDescriptor("test", "us-east-1", [:])

    then:
    result == null
    1 * cloudWatch.listMetrics(_) >> ListMetricsResponse.builder().metrics([]).build()
  }

  void "getMetrics throws exception when multiple results found"() {
    when:
    provider.getMetricDescriptor("test", "us-east-1", [:])

    then:
    thrown(IllegalArgumentException)
    1 * cloudWatch.listMetrics(_) >> ListMetricsResponse.builder().metrics([ Metric.builder().build(), Metric.builder().build() ]).build()
  }

  void "getMetrics returns a metric when one found"() {
    given:
    Dimension dimension = Dimension.builder().name("AutoScalingGroupName").value("asg-v001").build()
    def filters = [name: "expectedMetric", namespace: "space"]

    when:
    AmazonMetricDescriptor result = provider.getMetricDescriptor("test", "us-east-1", filters)

    then:
    result.cloudProvider == "aws"
    result.name == "expectedMetric"
    result.namespace == "space"
    result.dimensions == [ dimension ]
    1 * cloudWatch.listMetrics(_) >> ListMetricsResponse.builder().metrics([
        Metric.builder()
            .metricName("expectedMetric")
            .namespace("space")
            .dimensions(dimension)
            .build()
    ]).build()
  }

  void "getStatistics sends defaults for statistics, period"() {
    given:
    GetMetricStatisticsRequest request
    def filters = [namespace: "space"]

    when:
    provider.getStatistics("test", "us-east-1", "expectedMetric", filters, 0, 1)

    then:
    1 * cloudWatch.getMetricStatistics(_) >> { arguments ->
      request = arguments[0]
      return GetMetricStatisticsResponse.builder().datapoints([]).build()
    }
    request != null
    request.startTime() == new Date(0).toInstant()
    request.endTime() == new Date(1).toInstant()
    request.period() == 600
    request.statisticsAsStrings() == ["Average"]
  }

  void "getStatistics throws exception if namespace is missing"() {
    when:
    provider.getStatistics("test", "us-east-1", "someMetric", [:], null, null)

    then:
    thrown(IllegalArgumentException)
  }

  void "getStatistics treats all other filter values as dimensions"() {
    given:
    GetMetricStatisticsRequest request
    def filters = [
        namespace: "space",
        period: "100",
        statistics: "Average,Sum",
        someDimension: "dimension!"
    ]

    when:
    provider.getStatistics("test", "us-east-1", "expectedMetric", filters, 0, 50)

    then:
    1 * cloudWatch.getMetricStatistics(_) >> { arguments ->
      request = arguments[0]
      return GetMetricStatisticsResponse.builder().datapoints([]).build()
    }
    request != null
    request.dimensions().size() == 1
    request.dimensions()[0].name() == "someDimension"
    request.dimensions()[0].value() == "dimension!"
  }


}
