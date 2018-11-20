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
import com.netflix.kayenta.judge.stats.EffectSizes
import com.netflix.kayenta.mannwhitney.{MannWhitney, MannWhitneyParams}
import org.apache.commons.math3.stat.StatUtils

case class MannWhitneyResult(lowerConfidence: Double, upperConfidence: Double, estimate: Double, deviation: Double)
case class ComparisonResult(classification: MetricClassificationLabel, reason: Option[String], deviation: Double)

class MannWhitneyClassifier(tolerance: Double=0.25,
                            confLevel: Double=0.95,
                            effectSizeThresholds: (Double, Double) = (1.0, 1.0),
                            criticalThresholds: (Double, Double) = (1.0, 1.0)) extends BaseMetricClassifier {

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

    //Calculate the deviation (Effect Size) between the experiment and control
    val effectSize = calculateDeviation(experiment, control)

    MannWhitneyResult(confInterval(0), confInterval(1), estimate, effectSize)
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
  private def calculateBounds(testResult: MannWhitneyResult): (Double, Double) = {
    val estimate = math.abs(testResult.estimate)
    val criticalValue = tolerance * estimate

    val lowerBound = -1 * criticalValue
    val upperBound = criticalValue
    (lowerBound, upperBound)
  }

  /**
    * Calculate the deviation (Effect Size) between the experiment and control
    */
  private def calculateDeviation(experiment: Array[Double], control: Array[Double]): Double = {
    if(StatUtils.mean(control) == 0.0) 1.0 else EffectSizes.meanRatio(control, experiment)
  }

  /**
    * Compare the experiment to the control using the Mann-Whitney U Test and check the magnitude of the effect
    */
  private def compare(control: Metric,
                      experiment: Metric,
                      direction: MetricDirection,
                      effectSizeThresholds: (Double, Double)): ComparisonResult = {

    //Perform the Mann-Whitney U Test
    val mwResult = MannWhitneyUTest(experiment.values, control.values)
    val (lowerBound, upperBound) = calculateBounds(mwResult)

    //Check if the experiment is high in comparison to the control
    val isHigh = {
      (direction == MetricDirection.Increase || direction == MetricDirection.Either) &&
        mwResult.lowerConfidence > upperBound &&
        mwResult.deviation >= effectSizeThresholds._2
    }

    //Check if the experiment is low in comparison to the control
    val isLow = {
      (direction == MetricDirection.Decrease || direction == MetricDirection.Either) &&
        mwResult.upperConfidence < lowerBound &&
        mwResult.deviation <= effectSizeThresholds._1
    }

    if(isHigh){
      val reason = s"${experiment.name} was classified as $High"
      ComparisonResult(High, Some(reason), mwResult.deviation)

    }else if(isLow){
      val reason = s"${experiment.name} was classified as $Low"
      ComparisonResult(Low, Some(reason), mwResult.deviation)

    } else {
      ComparisonResult(Pass, None, mwResult.deviation)
    }
  }

  override def classify(control: Metric,
                        experiment: Metric,
                        direction: MetricDirection,
                        nanStrategy: NaNStrategy,
                        isCriticalMetric: Boolean,
                        isDataRequired: Boolean): MetricClassification = {

    //Check if there is no-data for the experiment or control
    if (experiment.values.isEmpty || control.values.isEmpty) {
      if (nanStrategy == NaNStrategy.Remove) {
        val reason = s"Missing data for ${experiment.name}"
        //Check if the config indicates that the given metric should have data but not critically fail the canary
        if (isDataRequired && !isCriticalMetric) {
          return MetricClassification(NodataFailMetric, Some(reason), 1.0, critical = false)
        }
        return MetricClassification(Nodata, Some(reason), 1.0, isCriticalMetric)
      } else {
        return MetricClassification(Pass, None, 1.0, critical = false)
      }
    }

    //Check if the experiment and control data are equal
    if (experiment.values.sorted.sameElements(control.values.sorted)) {
      val reason = s"The ${experiment.label} and ${control.label} data are identical"
      return MetricClassification(Pass, Some(reason), 1.0, critical = false)
    }

    //Check the number of unique observations
    if (experiment.values.union(control.values).distinct.length == 1) {
      return MetricClassification(Pass, None, 1.0, critical = false)
    }

    //Compare the experiment to the control using the Mann-Whitney U Test, checking the magnitude of the effect
    val comparison = compare(control, experiment, direction, effectSizeThresholds)

    //Check if the metric was marked as critical, and if the metric was classified as a failure (High, Low)
    if(isCriticalMetric && comparison.classification == High && comparison.deviation >= criticalThresholds._2){
      val reason = s"The metric ${experiment.name} was classified as $High (Critical)"
      MetricClassification(High, Some(reason), comparison.deviation, critical = true)

    }else if(isCriticalMetric && comparison.classification == Low && comparison.deviation <= criticalThresholds._1){
      val reason = s"The metric ${experiment.name} was classified as $Low (Critical)"
      MetricClassification(Low, Some(reason), comparison.deviation, critical = true)

    }else if(isCriticalMetric && (comparison.classification == Nodata || comparison.classification == Error)){
      MetricClassification(comparison.classification, comparison.reason, comparison.deviation, critical = true)

    }else{
      MetricClassification(comparison.classification, comparison.reason, comparison.deviation, critical = false)
    }

  }
}
