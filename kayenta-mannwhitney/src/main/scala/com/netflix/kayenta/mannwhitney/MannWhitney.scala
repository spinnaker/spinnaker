/*
 * Copyright 2018 Netflix, Inc.
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

package com.netflix.kayenta.mannwhitney

import org.apache.commons.math3.analysis.UnivariateFunction
import org.apache.commons.math3.distribution.NormalDistribution
import org.apache.commons.math3.stat.ranking._

case class MannWhitneyParams(mu: Double, confidenceLevel: Double, controlData: Array[Double], experimentData: Array[Double])
case class MannWhitneyResult(confidenceInterval: Array[Double], estimate: Double)

/**
  * An implementation of the Mann-Whitney U test with confidence intervals (also called Wilcoxon rank-sum test).
  */
class MannWhitney {

  /**
    * Performs a two-sample, two-sided Mann-Whitney U test.
    * The null hypothesis is that the distributions of x and y differ by a location shift of mu and the alternative is
    * that they differ by some other location shift.
    *
    * A nonparametric confidence interval and an estimator for the difference of the location parameters x-y is
    * computed. Note that the difference in location parameters does not estimate the difference in medians
    * but rather the median of the difference between a sample from x and a sample from y.
    *
    * @param x the first sample (array)
    * @param y the second sample (array)
    * @param confidenceLevel confidence level of the interval
    * @param mu the location shift parameter used to form the null hypothesis
    * @return a confidence interval and an estimate of the location parameter
    */
  def mannWhitneyUTest(x: Array[Double], y: Array[Double], confidenceLevel: Double = 0.95, mu: Double = 0.0): MannWhitneyResult = {
    val (confidenceInterval, estimate) = calculateConfidenceInterval(x, y, confidenceLevel, mu)
    MannWhitneyResult(confidenceInterval, estimate)
  }

  @deprecated("Use mannWhitneyUTest instead.", "2.9.0")
  def eval(params: MannWhitneyParams): MannWhitneyResult = synchronized {
    val (confidenceInterval, estimate) =
      calculateConfidenceInterval(params.experimentData, params.controlData, params.confidenceLevel, params.mu)
    MannWhitneyResult(confidenceInterval, estimate)
  }

  def wilcoxonDiff(mu: Double, quantile: Double, x: Array[Double], y: Array[Double]): Double = {
    val xLen = x.length.toDouble
    val yLen = y.length.toDouble

    val dr = new NaturalRanking(NaNStrategy.MAXIMAL, TiesStrategy.AVERAGE).rank(x.map(_ - mu) ++ y)
    val ntiesCi = dr.groupBy(identity).mapValues(_.length)
    val dz = {
      for (e <- x.indices) yield dr(e)
    }.sum - xLen * (xLen + 1) / 2 - xLen * yLen / 2
    val correctionCi = (if (dz.signum.isNaN) 0 else dz.signum) * 0.5
    val sigmaCi = Math.sqrt(
      (xLen * yLen / 12) *
        (
          (xLen + yLen + 1)
            - ntiesCi.mapValues(v => Math.pow(v, 3) - v).values.sum
            / ((xLen + yLen) * (xLen + yLen - 1))
          )
    )
    if (sigmaCi == 0) throw new IllegalArgumentException("cannot compute confidence interval when all observations are tied")
    (dz - correctionCi) / sigmaCi - quantile
  }

  protected def calculateConfidenceInterval(x: Array[Double],
                                            y: Array[Double],
                                            confidenceLevel: Double,
                                            mu: Double): (Array[Double], Double) = {

    val alpha: Double = 1.0 - confidenceLevel
    val muMin: Double = x.min - y.max
    val muMax: Double = x.max - y.min

    val wilcoxonDiffWrapper = (zq: Double) => new UnivariateFunction {
      override def value(input: Double): Double = wilcoxonDiff(input, zq, x, y)
    }

    def findRoot(zq: Double): Double = {
      val fLower = wilcoxonDiff(muMin, zq, x, y)
      val fUpper = wilcoxonDiff(muMax, zq, x, y)
      if (fLower <= 0) muMin
      else if (fUpper >= 0) muMax
      else BrentSolver.solve(muMin, muMax, fLower, fUpper, wilcoxonDiffWrapper(zq))
    }

    val zQuant = new NormalDistribution(0,1).inverseCumulativeProbability(alpha/2)
    val confidenceInterval: Array[Double] = Array(findRoot(-zQuant), findRoot(zQuant))
    val fLower = wilcoxonDiff(muMin, 0, x, y)
    val fUpper = wilcoxonDiff(muMax, 0, x, y)

    val estimate = BrentSolver.solve(muMin, muMax, fLower, fUpper, wilcoxonDiffWrapper(0))
    (confidenceInterval, estimate)
  }
}

