package com.netflix.kayenta.judge.stats

import com.netflix.kayenta.judge.Metric
import com.netflix.kayenta.judge.stats.DescriptiveStatistics._
import org.apache.commons.math3.util.FastMath
import org.apache.commons.math3.stat.StatUtils
import org.apache.commons.math3.stat.ranking._

object EffectSizes {

  /**
    * Mean Ratio
    * Measures the difference between the mean values as a ratio (experiment/control)
    * Note: This is included for backwards compatibility
    */
  def meanRatio(control: Array[Double], experiment: Array[Double]): Double = {
    val controlMean = StatUtils.mean(control)
    val experimentMean = StatUtils.mean(experiment)
    if(controlMean == 0.0 || experimentMean == 0.0) Double.NaN else experimentMean/controlMean
  }

  /**
    * Mean Ratio
    * Measures the difference between the mean values as a ratio (experiment/control)
    * Note: This is included for backwards compatibility
    */
  def meanRatio(control: Metric, experiment: Metric): Double = {
    meanRatio(control.values, experiment.values)
  }

  /**
    * Mean Ratio
    * Measures the difference between the mean values as a ratio (experiment/control)
    * Note: This is included for backwards compatibility
    */
  def meanRatio(control: MetricStatistics, experiment: MetricStatistics): Double = {
    if(control.mean == 0.0 || experiment.mean == 0.0) Double.NaN else experiment.mean/control.mean
  }

  /**
    * Cohen's d (Pooled Standard Deviation)
    * Cohen's d is an effect size used to indicate the standardized difference between two means
    * https://en.wikipedia.org/wiki/Effect_size#Cohen's_d
    */
  def cohenD(control: Metric, experiment: Metric): Double = {
    cohenD(summary(control), summary(experiment))
  }

  /**
    * Cohen's d (Pooled Standard Deviation)
    * Cohen's d is an effect size used to indicate the standardized difference between two means
    * https://en.wikipedia.org/wiki/Effect_size#Cohen's_d
    */
  def cohenD(control: MetricStatistics, experiment: MetricStatistics): Double = {
    val pooledStd = FastMath.sqrt(((experiment.count - 1) * FastMath.pow(experiment.std, 2) + (control.count - 1) * FastMath.pow(control.std, 2)) / (control.count + experiment.count - 2))
    FastMath.abs(experiment.mean - control.mean) / pooledStd
  }

  /**
    * Common Language Effect Size (CLES)
    * The common language effect size is the proportion of pairs where x is higher than y.
    * Uses a brute-force version of the formula given by Vargha and Delaney 2000
    *
    * A. Vargha and H. D. Delaney.
    * A critique and improvement of the CL common language
    * effect size statistics of McGraw and Wong.
    * Journal of Educational and Behavioral Statistics, 25(2):101-132, 2000
    *
    * The formula has been transformed to minimize accuracy errors
    */
  def cles(control: Array[Double], experiment: Array[Double]): Double = {
    val m = experiment.length
    val n = control.length
    val ranks = new NaturalRanking(NaNStrategy.MAXIMAL, TiesStrategy.AVERAGE).rank(experiment ++ control)
    val r1 = ranks.take(m).sum
    (2 * r1 - m * (m + 1)) / (2 * n * m)
  }

  /**
    * Common Language Effect Size (CLES)
    * The common language effect size is the proportion of pairs where x is higher than y.
    * Uses a brute-force version of the formula given by Vargha and Delaney 2000
    *
    * A. Vargha and H. D. Delaney.
    * A critique and improvement of the CL common language
    * effect size statistics of McGraw and Wong.
    * Journal of Educational and Behavioral Statistics, 25(2):101-132, 2000
    *
    * The formula has been transformed to minimize accuracy errors
    */
  def cles(control: Metric, experiment: Metric): Double = {
    cles(control.values, experiment.values)
  }
}
