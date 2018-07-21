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

package com.netflix.kayenta.judge.classifiers.metric

import com.netflix.kayenta.judge.Metric
import com.netflix.kayenta.judge.preprocessing.Transforms
import com.netflix.kayenta.mannwhitney.{MannWhitney, MannWhitneyParams}
import org.apache.commons.math3.stat.StatUtils

case class MannWhitneyResult(lowerConfidence: Double, upperConfidence: Double, estimate: Double)

class MannWhitneyClassifier(tolerance: Double=0.25, confLevel: Double=0.95) extends BaseMetricClassifier {

  /**
    * Mann-Whitney U Test
    * An implementation of the Mann-Whitney U test (also called the Wilcoxon rank-sum test).
    * Note: In the case of the degenerate distribution, Gaussian noise is added
    */
  def MannWhitneyUTest(experimentValues: Array[Double], controlValues: Array[Double]): MannWhitneyResult = {
    val mwTest = new MannWhitney()

    //Check for tied ranks and transform the data by adding Gaussian noise
    val addNoise = if (experimentValues.distinct.length == 1 && controlValues.distinct.length == 1) true else false
    val experiment = if(addNoise) addGaussianNoise(experimentValues) else experimentValues
    val control = if(addNoise) addGaussianNoise(controlValues) else controlValues

    val params = MannWhitneyParams(mu = 0, confLevel, control, experiment)
    val testResult = mwTest.eval(params)
    val confInterval = testResult.confidenceInterval
    val estimate = testResult.estimate

    MannWhitneyResult(confInterval(0), confInterval(1), estimate)
  }

  /**
    * Add Gaussian noise to the input array
    * Scale the amplitude of the noise based on the input values
    * Note: the input array should not contain NaN values
    */
  private def addGaussianNoise(values: Array[Double]): Array[Double] = {
    val scalingFactor = 1e-5
    val metricScale = values.distinct.head * scalingFactor
    Transforms.addGaussianNoise(values, mean=0.0, stdev = metricScale)
  }

  /**
    * Calculate the upper and lower bounds for classifying the metric.
    * The bounds are calculated as a fraction of the Hodges–Lehmann estimator
    */
  def calculateBounds(testResult: MannWhitneyResult): (Double, Double) = {
    val estimate = math.abs(testResult.estimate)
    val criticalValue = tolerance * estimate

    val lowerBound = -1 * criticalValue
    val upperBound = criticalValue
    (lowerBound, upperBound)
  }

  override def classify(control: Metric, experiment: Metric, direction: MetricDirection, nanStrategy: NaNStrategy): MetricClassification = {
    //Check if there is no-data for the experiment or control
    if (experiment.values.isEmpty || control.values.isEmpty) {
      if (nanStrategy == NaNStrategy.Remove) {
        return MetricClassification(Nodata, None, 0.0)
      } else {
        return MetricClassification(Pass, None, 1.0)
      }
    }

    //Check if the experiment and control data are equal
    if (experiment.values.sorted.sameElements(control.values.sorted)) {
      val reason = s"The ${experiment.label} and ${control.label} data are identical"
      return MetricClassification(Pass, Some(reason), 1.0)
    }

    //Check the number of unique observations
    if (experiment.values.union(control.values).distinct.length == 1) {
      return MetricClassification(Pass, None, 1.0)
    }

    //Perform the Mann-Whitney U Test
    val mwResult = MannWhitneyUTest(experiment.values, control.values)
    val meanRatio = StatUtils.mean(experiment.values)/StatUtils.mean(control.values)
    val (lowerBound, upperBound) = calculateBounds(mwResult)

    if((direction == MetricDirection.Increase || direction == MetricDirection.Either) && mwResult.lowerConfidence > upperBound){
      val reason = s"The metric was classified as $High"
      return MetricClassification(High, Some(reason), meanRatio)

    }else if((direction == MetricDirection.Decrease || direction == MetricDirection.Either) && mwResult.upperConfidence < lowerBound){
      val reason = s"The metric was classified as $Low"
      return MetricClassification(Low, Some(reason), meanRatio)
    }

    MetricClassification(Pass, None, meanRatio)
  }

}
