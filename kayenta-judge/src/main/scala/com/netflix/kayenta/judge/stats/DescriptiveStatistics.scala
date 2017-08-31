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

}
