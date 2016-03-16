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

import com.amazonaws.services.cloudwatch.AmazonCloudWatch
import com.amazonaws.services.cloudwatch.model.Dimension
import com.amazonaws.services.cloudwatch.model.DimensionFilter
import com.amazonaws.services.cloudwatch.model.GetMetricStatisticsRequest
import com.amazonaws.services.cloudwatch.model.GetMetricStatisticsResult
import com.amazonaws.services.cloudwatch.model.ListMetricsRequest
import com.netflix.spinnaker.clouddriver.aws.AmazonCloudProvider
import com.netflix.spinnaker.clouddriver.aws.model.AmazonMetricDatapoint
import com.netflix.spinnaker.clouddriver.aws.model.AmazonMetricDescriptor
import com.netflix.spinnaker.clouddriver.aws.model.AmazonMetricStatistics
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider
import com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials
import com.netflix.spinnaker.clouddriver.model.CloudMetricProvider
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component
class AmazonCloudMetricProvider implements CloudMetricProvider<AmazonMetricDescriptor> {

  final AmazonClientProvider amazonClientProvider
  final AccountCredentialsProvider accountCredentialsProvider
  final AmazonCloudProvider amazonCloudProvider

  @Autowired
  AmazonCloudMetricProvider(AmazonClientProvider amazonClientProvider,
                            AccountCredentialsProvider accountCredentialsProvider,
                            AmazonCloudProvider amazonCloudProvider) {
    this.amazonClientProvider = amazonClientProvider
    this.accountCredentialsProvider = accountCredentialsProvider
    this.amazonCloudProvider = amazonCloudProvider
  }

  @Override
  String getCloudProvider() {
    amazonCloudProvider.id
  }

  @Override
  AmazonMetricDescriptor getMetricDescriptor(String account, String region, Map<String, String> filters) {
    def cloudWatch = getCloudWatch(account, region)
    def request = new ListMetricsRequest()
        .withNamespace(filters.namespace)
        .withMetricName(filters.metricName)
    def results = cloudWatch.listMetrics(request).metrics
    if (!results) {
      return null
    }
    if (results.size() > 1) {
      throw new IllegalArgumentException("Multiple metric descriptors (${results.size()}) found for provided filters")
    }
    return AmazonMetricDescriptor.from(results[0])
  }

  @Override
  List<AmazonMetricDescriptor> findMetricDescriptors(String account, String region, Map<String, String> filters) {
    def cloudWatch = getCloudWatch(account, region)
    def request = new ListMetricsRequest()
    if (filters.namespace) {
      request.withNamespace(filters.namespace)
    }
    if (filters.name) {
      request.withMetricName(filters.name)
    }

    request.withDimensions(filters.findResults {
      if (it.key != "namespace" && it.key != "name") {
        new DimensionFilter().withName(it.key).withValue(it.value)
      } else {
        null
      }
    })
    def results = cloudWatch.listMetrics(request).metrics
    return results.findResults { AmazonMetricDescriptor.from(it) }
  }

  @Override
  AmazonMetricStatistics getStatistics(String account, String region, String metricName, Map<String, String> filters,
                                       Long startTime, Long endTime) {
    List<String> requiredFilters = ["namespace"]
    List<String> optionalFilters = ["statistics", "period"]
    if (!filters || !requiredFilters.every({ filters.containsKey(it)})) {
      throw new IllegalArgumentException("Not all required filters (${requiredFilters.join(', ')}) are present")
    }
    AmazonCloudWatch cloudWatch = getCloudWatch(account, region)
    GetMetricStatisticsRequest request = new GetMetricStatisticsRequest()
      .withNamespace(filters.namespace)
      .withMetricName(metricName)
      .withStartTime(new Date(startTime))
      .withEndTime(new Date(endTime))
      .withStatistics(filters.statistics ? filters.statistics.split(",") : "Average")
      .withPeriod(filters.period ? Integer.parseInt(filters.period) : 600)
      .withDimensions(filters.findResults {
        if (!requiredFilters.contains(it.key) && !optionalFilters.contains(it.key)) {
          new Dimension().withName(it.key).withValue(it.value)
        } else {
          null
        }
      })
    GetMetricStatisticsResult results = cloudWatch.getMetricStatistics(request)
    return AmazonMetricStatistics.from(results)
  }

  private AmazonCloudWatch getCloudWatch(String account, String region) {
    def credentials = accountCredentialsProvider.getCredentials(account)
    if (!(credentials instanceof NetflixAmazonCredentials)) {
      throw new IllegalArgumentException("Invalid credentials: ${account}:${region}")
    }
    amazonClientProvider.getCloudWatch(credentials, region)
  }
}
