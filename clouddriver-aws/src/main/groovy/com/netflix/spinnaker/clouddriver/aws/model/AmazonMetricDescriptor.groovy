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

package com.netflix.spinnaker.clouddriver.aws.model

import com.amazonaws.services.cloudwatch.model.Dimension
import com.amazonaws.services.cloudwatch.model.Metric
import com.fasterxml.jackson.annotation.JsonInclude
import com.netflix.spinnaker.clouddriver.model.CloudMetricDescriptor

@JsonInclude(JsonInclude.Include.NON_EMPTY)
class AmazonMetricDescriptor implements CloudMetricDescriptor {
  final String cloudProvider = 'aws'
  final String namespace
  final String name
  final List<Dimension> dimensions

  AmazonMetricDescriptor (String cloudProvider, String namespace, String name, List<Dimension> dimensions) {
    this.cloudProvider = cloudProvider
    this.namespace = namespace
    this.name = name
    this.dimensions = dimensions
  }
  static AmazonMetricDescriptor from(Metric metric) {
    new AmazonMetricDescriptor('aws', metric.namespace, metric.metricName, metric.dimensions)
  }

}
