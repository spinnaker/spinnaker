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
import org.apache.commons.math3.stat.StatUtils

/**
  * Mean Inequality Metric Classifier
  *
  * Compare the mean value of the experiment population to the mean value of the control population.
  * This classifier is primary used for benchmarking.
  */
class MeanInequalityClassifier extends BaseMetricClassifier{

  override def classify(control: Metric, experiment: Metric, direction: MetricDirection): MetricClassification = {

    //Check if there is no-data for the experiment or control
    if(experiment.values.isEmpty || control.values.isEmpty){
      return MetricClassification(Nodata, None, 0.0)
    }

    val experimentMean = StatUtils.mean(experiment.values)
    val controlMean = StatUtils.mean(control.values)
    val ratio = experimentMean/controlMean

    if((direction == MetricDirection.Increase || direction == MetricDirection.Either) && experimentMean > controlMean){
      val reason = s"The ${experiment.label} mean was greater than the ${control.label} mean"
      return MetricClassification(High, Some(reason), ratio)

    }else if((direction == MetricDirection.Decrease || direction == MetricDirection.Either) && experimentMean < controlMean){
      val reason = s"The ${experiment.label} mean was less than the ${control.label} mean"
      return MetricClassification(Low, Some(reason), ratio)
    }

    MetricClassification(Pass, None, 1.0)
  }

}
