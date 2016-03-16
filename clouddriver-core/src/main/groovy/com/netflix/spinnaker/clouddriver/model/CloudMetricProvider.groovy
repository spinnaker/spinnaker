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

package com.netflix.spinnaker.clouddriver.model

interface CloudMetricProvider<T extends CloudMetricDescriptor> {

  /**
   * Returns the platform of the provider
   * @return a String, e.g. 'aws', 'gcp'
   */
  String getCloudProvider()

  /**
   * Returns a specific metric descriptor
   * @param account the account
   * @param region the region
   * @param filters a collection of identifiers used to uniquely identify a metric
   * @return a metric descriptor if one is found; should throw an exception if multiple metric descriptors are found
   * for the supplied filters
   */
  T getMetricDescriptor(String account, String region, Map<String, String> filters)

  /**
   * Returns a list of metric descriptors matching the supplied filters
   * @param account the account
   * @param region the region
   * @param filters a collection of identifiers used to select a subset of all metrics in the account and region
   * @return a list of metric descriptors matching the filters
   */
  List<T> findMetricDescriptors(String account, String region, Map<String, String> filters)

  /**
   * Returns a statistic set for the metric descriptor uniquely identified by the supplied filters
   * @param account the account
   * @param region the region
   * @param name the name of the target metric
   * @param filters a collection of identifiers used to uniquely identify a metric
   * @param startTime an inclusive timestamp to determine the oldest datapoint to return
   * @param endTime an exclusive timestamp to determine the newest datapoint to return
   * @return a CloudMetricStatistics object, describing the statistics with timestamps
   */
  CloudMetricStatistics getStatistics(String account, String region, String metricName, Map<String, String> filters,
                                      Long startTime, Long endTime)

}
