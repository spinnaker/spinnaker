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
import com.netflix.kayenta.r.{MannWhitney, MannWhitneyParams}
import org.apache.commons.math3.stat.StatUtils

case class MannWhitneyResult(pValue: Double, lowerConfidence: Double, upperConfidence: Double, estimate: Double)

//todo (csanden) rename this classifier
class MannWhitneyClassifier(fraction: Double=0.25, confLevel: Double=0.99, mw: MannWhitney) extends BaseMetricClassifier{

  /**
    * Mann-Whitney U Test
    * An implementation of the Mann-Whitney U test (also called Wilcoxon rank-sum test).
    * @param experimentValues
    * @param controlValues
    * @return
    */
  def MannWhitneyUTest(experimentValues: Array[Double], controlValues: Array[Double]): MannWhitneyResult ={
    //todo: move this to its own package
    val params = MannWhitneyParams.builder()
      .mu(0)
      .confidenceLevel(0.99)
      .controlData(controlValues)
      .experimentData(experimentValues)
      .build()

    val mwResult = mw.eval(params)
    val confInterval = mwResult.getConfidenceInterval
    val pValue = mwResult.getPValue
    val estimate = mwResult.getEstimate

    MannWhitneyResult(pValue, confInterval(0), confInterval(1), estimate)
  }

  def calculateBounds(control: Metric, experiment: Metric):  (Double, Double)={
    //todo(csanden): use the Hodges–Lehmann estimate
    val delta = math.abs(StatUtils.mean(experiment.values) - StatUtils.mean(control.values))
    val criticalValue = fraction * delta
    val lowerBound = -1 * criticalValue
    val upperBound = criticalValue
    (lowerBound, upperBound)
  }

  override def classify(control: Metric, experiment: Metric): MetricClassification = {
    //todo(csanden): classification label should not be a string

    //Check if there is no-data for the experiment or control
    if(experiment.values.isEmpty || control.values.isEmpty){
      return MetricClassification(Nodata, None, 0.0)
    }

    //Check if the experiment and control data are equal
    if (experiment.values.sameElements(control.values)){
      return MetricClassification(Pass, None, 0.0)
    }

    //Perform Mann-Whitney U Test
    val mwResult = MannWhitneyUTest(experiment.values, control.values)
    val ratio = StatUtils.mean(experiment.values)/StatUtils.mean(control.values)
    val (lowerBound, upperBound) = calculateBounds(control, experiment)

    //todo(csanden): improve the reason
    if(mwResult.lowerConfidence > upperBound){
      val reason = "The metric was classified as High"
      return MetricClassification(High, Some(reason), ratio)

    }else if(mwResult.upperConfidence < lowerBound){
      val reason = "The metric was classified as Low"
      return MetricClassification(Low, Some(reason), ratio)
    }

    MetricClassification(Pass, None, ratio)

  }

}
