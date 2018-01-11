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

package com.netflix.kayenta.judge.evaluation

import com.netflix.kayenta.judge.Metric
import com.netflix.kayenta.judge.classifiers.metric._

/**
  * Class that represents an instance of data and truth labels.
  * @param experiment experiment metric
  * @param control control metric
  * @param label ground truth label
  */
case class LabeledInstance(experiment: Metric, control: Metric, label: MetricClassificationLabel)

/**
  * Abstract class for evaluators that compute metrics from predictions.
  */
abstract class BaseEvaluator{
  def evaluate[T <: BaseMetricClassifier](classifier: T, dataset: List[LabeledInstance]): Map[String, Double]
}