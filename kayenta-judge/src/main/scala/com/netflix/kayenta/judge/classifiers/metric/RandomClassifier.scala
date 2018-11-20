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

import scala.util.Random

/**
  * Random Metric Classifier
  *
  * Randomly selects an element from the list of labels as the metric classification
  * @param labels list of metric classification labels
  */
class RandomClassifier(labels: List[MetricClassificationLabel] = List(Pass, High, Low)) extends BaseMetricClassifier{

  /**
    * Randomly select an element from the input list
    */
  def getRandomLabel(list: List[MetricClassificationLabel]): MetricClassificationLabel = Random.shuffle(list).head

  override def classify(control: Metric,
                        experiment: Metric,
                        direction: MetricDirection,
                        nanStrategy: NaNStrategy,
                        isCriticalMetric: Boolean,
                        isDataRequired: Boolean): MetricClassification = {

    //Check if there is no-data for the experiment or control
    if (experiment.values.isEmpty || control.values.isEmpty) {
      if (nanStrategy == NaNStrategy.Remove) {
        //Check if the config indicates that the given metric should have data but not critically fail the canary
        if (isDataRequired && !isCriticalMetric) {
          return MetricClassification(NodataFailMetric, None, 1.0, critical = false)
        }
        return MetricClassification(Nodata, None, 0.0, isCriticalMetric)
      } else {
        return MetricClassification(Pass, None, 1.0, critical = false)
      }
    }

    //Check if the experiment and control data are equal
    if (experiment.values.sameElements(control.values)){
      return MetricClassification(Pass, None, 1.0, critical = false)
    }

    val ratio = StatUtils.mean(experiment.values)/StatUtils.mean(control.values)
    val randomClassificationLabel = getRandomLabel(labels)

    MetricClassification(randomClassificationLabel, None, ratio, isCriticalMetric)
  }

}
