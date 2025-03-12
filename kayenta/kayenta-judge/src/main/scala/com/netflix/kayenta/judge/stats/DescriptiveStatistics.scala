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
import org.apache.commons.math3.util.FastMath
import org.apache.commons.math3.stat.StatUtils
import org.apache.commons.math3.stat.descriptive.rank.Percentile
import org.apache.commons.math3.stat.descriptive.rank.Percentile.EstimationType


case class MetricStatistics(min: Double, max: Double, mean: Double, std: Double, count: Int){
  def toMap:  Map[String, Any] = {
    Map("min" -> min, "max" -> max, "mean" -> mean, "std" -> std, "count" -> count)
  }
}

object DescriptiveStatistics {

  def mean(metric: Metric): Double = {
    if (metric.values.isEmpty) 0.0 else StatUtils.mean(metric.values)
  }

  def median(metric: Metric): Double = {
    if (metric.values.isEmpty) 0.0 else StatUtils.percentile(metric.values, 50)
  }

  def min(metric: Metric): Double = {
    if (metric.values.isEmpty) 0.0 else StatUtils.min(metric.values)
  }

  def max(metric: Metric): Double = {
    if (metric.values.isEmpty) 0.0 else StatUtils.max(metric.values)
  }

  def std(metric: Metric): Double = {
    if (metric.values.isEmpty) 0.0 else FastMath.sqrt(StatUtils.variance(metric.values))
  }

  /**
    * Returns an estimate of the pth percentile of the values in the metric object.
    * Uses the R-7 estimation strategy when the desired percentile lies between two data points.
    * @param metric input metric
    * @param p the percentile value to compute
    * @return the percentile value or Double.NaN if the metric is empty
    */
  def percentile(metric: Metric, p: Double): Double ={
    this.percentile(metric.values, p)
  }

  /**
    * Returns an estimate of the pth percentile of the values in the values array.
    * Uses the R-7 estimation strategy when the desired percentile lies between two data points.
    * @param values input array of values
    * @param p the percentile value to compute
    * @return the percentile value or Double.NaN if the array is empty
    */
  def percentile(values: Array[Double], p: Double): Double ={
    val percentile = new Percentile().withEstimationType(EstimationType.R_7)
    percentile.evaluate(values, p)
  }

  /**
    * Calculate a set of descriptive statistics for the input metric
    */
  def summary(metric: Metric): MetricStatistics = {
    val mean = this.mean(metric)
    val min = this.min(metric)
    val max = this.max(metric)
    val std = this.std(metric)
    val count = metric.values.length
    MetricStatistics(min, max, mean, std, count)
  }
}
