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
import software.amazon.awssdk.services.cloudwatch.model.DimensionFilter
import software.amazon.awssdk.services.cloudwatch.model.GetMetricStatisticsRequest
import software.amazon.awssdk.services.cloudwatch.model.GetMetricStatisticsResponse
import software.amazon.awssdk.services.cloudwatch.model.ListMetricsRequest
import com.netflix.spinnaker.clouddriver.aws.AmazonCloudProvider
import com.netflix.spinnaker.clouddriver.aws.model.AmazonMetricDescriptor
import com.netflix.spinnaker.clouddriver.aws.model.AmazonMetricStatistics
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider
import com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials
import com.netflix.spinnaker.clouddriver.model.CloudMetricProvider
import com.netflix.spinnaker.credentials.CredentialsRepository
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component
class AmazonCloudMetricProvider implements CloudMetricProvider<AmazonMetricDescriptor> {

  final AmazonClientProvider amazonClientProvider
  final CredentialsRepository<NetflixAmazonCredentials> credentialsRepository
  final AmazonCloudProvider amazonCloudProvider

  @Autowired
  AmazonCloudMetricProvider(AmazonClientProvider amazonClientProvider,
                            CredentialsRepository<NetflixAmazonCredentials> credentialsRepository,
                            AmazonCloudProvider amazonCloudProvider) {
    this.amazonClientProvider = amazonClientProvider
    this.credentialsRepository = credentialsRepository
    this.amazonCloudProvider = amazonCloudProvider
  }

  @Override
  String getCloudProvider() {
    amazonCloudProvider.id
  }

  @Override
  AmazonMetricDescriptor getMetricDescriptor(String account, String region, Map<String, String> filters) {
    def cloudWatch = getCloudWatch(account, region)
    def request = ListMetricsRequest.builder()
        .namespace(filters.namespace)
        .metricName(filters.metricName)
        .build()
    def results = cloudWatch.listMetrics(request).metrics()
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
    def requestBuilder = ListMetricsRequest.builder()
    if (filters.namespace) {
      requestBuilder.namespace(filters.namespace)
    }
    if (filters.name) {
      requestBuilder.metricName(filters.name)
    }

    requestBuilder.dimensions(filters.findResults {
      if (it.key != "namespace" && it.key != "name") {
        DimensionFilter.builder().name(it.key).value(it.value).build()
      } else {
        null
      }
    })
    def results = cloudWatch.listMetrics(requestBuilder.build()).metrics()
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
    CloudWatchClient cloudWatch = getCloudWatch(account, region)
    GetMetricStatisticsRequest request = GetMetricStatisticsRequest.builder()
      .namespace(filters.namespace)
      .metricName(metricName)
      .startTime(new Date(startTime).toInstant())
      .endTime(new Date(endTime).toInstant())
      .statisticsWithStrings(filters.statistics ? filters.statistics.split(",") as List : ["Average"])
      .period(filters.period ? Integer.parseInt(filters.period) : 600)
      .dimensions(filters.findResults {
        if (!requiredFilters.contains(it.key) && !optionalFilters.contains(it.key)) {
          Dimension.builder().name(it.key).value(it.value).build()
        } else {
          null
        }
      })
      .build()
    GetMetricStatisticsResponse results = cloudWatch.getMetricStatistics(request)
    return AmazonMetricStatistics.from(results)
  }

  private CloudWatchClient getCloudWatch(String account, String region) {
    def credentials = credentialsRepository.getOne(account)
    if (!(credentials instanceof NetflixAmazonCredentials)) {
      throw new IllegalArgumentException("Invalid credentials: ${account}:${region}")
    }
    amazonClientProvider.getAmazonCloudWatchV2(credentials, region)
  }
}
