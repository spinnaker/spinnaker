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

class MannWhitney {
  import MannWhitney._

  def eval(params: MannWhitneyParams): MannWhitneyResult = synchronized {
    val (confidenceInterval, estimate) =
      calculateConfidenceInterval(params.experimentData, params.controlData, params.confidenceLevel, params.mu)
    MannWhitneyResult(confidenceInterval, estimate)
  }

  protected def calculateConfidenceInterval(x: Array[Double],
                                            y: Array[Double],
                                            confidenceLevel: Double,
                                            mu: Double): (Array[Double], Double) = {

    val xLen = x.length.toDouble
    val yLen = y.length.toDouble

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
    val confidenceInterval: Array[Double] =
      Array(
        findRoot(-zQuant),
        findRoot(zQuant)
      )
    val fLower = wilcoxonDiff(muMin, 0, x, y)
    val fUpper = wilcoxonDiff(muMax, 0, x, y)

    val estimate = BrentSolver.solve(muMin, muMax, fLower, fUpper, wilcoxonDiffWrapper(0))
    (confidenceInterval, estimate)
  }
}

object MannWhitney {
  def wilcoxonDiff(mu: Double,
                   quantile: Double,
                   x: Array[Double],
                   y: Array[Double]): Double = {

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
    if (sigmaCi == 0) throw new MannWhitneyException("cannot compute confidence interval when all observations are tied")
    (dz - correctionCi) / sigmaCi - quantile
  }
}
