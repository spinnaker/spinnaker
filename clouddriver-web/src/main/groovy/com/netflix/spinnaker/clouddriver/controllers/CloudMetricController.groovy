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

package com.netflix.spinnaker.clouddriver.controllers

import static java.time.temporal.ChronoUnit.HOURS

import com.netflix.spinnaker.clouddriver.model.CloudMetricDescriptor
import com.netflix.spinnaker.clouddriver.model.CloudMetricProvider
import com.netflix.spinnaker.clouddriver.model.CloudMetricStatistics
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.Clock

@RequestMapping("/cloudMetrics")
@RestController
class CloudMetricController {

  @Autowired
  final List<CloudMetricProvider> metricProviders

  final Clock clock

  @RequestMapping(method = RequestMethod.GET, value = "/{cloudProvider}/{account}/{region}")
  List<CloudMetricDescriptor> findAll(@PathVariable String cloudProvider,
                        @PathVariable String account,
                        @PathVariable String region,
                        @RequestParam Map<String, String> filters) {

    getProvider(cloudProvider).findMetricDescriptors(account, region, filters)
  }

  CloudMetricController(List<CloudMetricProvider> metricProviders, Clock clock) {
    this.clock = clock;
    this.metricProviders = metricProviders
  }

  @Autowired
  CloudMetricController(List<CloudMetricProvider> metricProviders) {
    this(metricProviders, Clock.systemDefaultZone())
  }

  /**
   * Returns a data set describing the metric over the specified period of time. If start and end times are not provided,
   * they will be defaulted to 24 hours ago and now, respectively
   * @param cloudProvider the provider, e.g. "aws", "gce", etc.
   * @param account the account of the provider
   * @param region the region applicable to the metric
   * @param metricName the name of the metric
   * @param startTime start time (inclusive)
   * @param endTime end time (exclusive)
   * @param filters any provider-specific filters used to create the data set
   * @return the set of statistics
   */
  @RequestMapping(method = RequestMethod.GET, value = "/{cloudProvider}/{account}/{region}/{metricName}/statistics")
  CloudMetricStatistics getStatistics(@PathVariable String cloudProvider,
                                      @PathVariable String account,
                                      @PathVariable String region,
                                      @PathVariable String metricName,
                                      @RequestParam(required = false) Long startTime,
                                      @RequestParam(required = false) Long endTime,
                                      @RequestParam Map<String, String> filters) {

    // Spring will map all request parameters into the filters map, so it's best to remove the declared ones
    filters.remove("startTime")
    filters.remove("endTime")

    Long start = startTime ?: clock.instant().minus(24, HOURS).toEpochMilli()
    Long end = endTime ?: clock.millis()

    getProvider(cloudProvider).getStatistics(account, region, metricName, filters, start, end)
  }

  private CloudMetricProvider getProvider(String cloudProvider) {
    CloudMetricProvider provider = metricProviders.findResult {
      it.cloudProvider == cloudProvider ? it : null
    }

    if (!provider) {
      throw new IllegalArgumentException("No provider found named '${cloudProvider}' that supports cloud metrics")
    }

    provider
  }
}
