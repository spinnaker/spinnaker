/*
 * Copyright 2017 Netflix, Inc.
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

package com.netflix.kayenta.judge.stats

import com.netflix.kayenta.judge.Metric
import org.apache.commons.math3.stat.StatUtils

case class MetricStatistics(min: Double, max: Double, mean: Double, median: Double, count: Int)

object DescriptiveStatistics {

  def mean(metric: Metric): Double = {
    if(metric.values.isEmpty) 0.0 else StatUtils.mean(metric.values)
  }

  def median(metric: Metric): Double = {
    if(metric.values.isEmpty) 0.0 else StatUtils.percentile(metric.values, 50)
  }

  def min(metric: Metric): Double = {
    if(metric.values.isEmpty) 0.0 else StatUtils.min(metric.values)
  }

  def max(metric: Metric): Double = {
    if(metric.values.isEmpty) 0.0 else StatUtils.max(metric.values)
  }

  def summary(metric: Metric): MetricStatistics = {
    val mean = this.mean(metric)
    val median = this.median(metric)
    val min = this.min(metric)
    val max = this.max(metric)
    val count = metric.values.length
    MetricStatistics(min, max, mean, median, count)
  }

  def toMap(metricStatistics: MetricStatistics): Map[String, Any] = {
    Map(
      "min" -> metricStatistics.min,
      "max" -> metricStatistics.max,
      "mean" -> metricStatistics.mean,
      "median" -> metricStatistics.median,
      "count" -> metricStatistics.count)
  }
}
