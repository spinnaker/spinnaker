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

sealed trait MetricClassificationLabel
case object Pass extends MetricClassificationLabel
case object High extends MetricClassificationLabel
case object Low extends MetricClassificationLabel
case object Nodata extends MetricClassificationLabel
case object Error extends MetricClassificationLabel

//todo (csanden): report deviation instead of ratio?
case class MetricClassification(classification: MetricClassificationLabel, reason: Option[String], ratio: Double)

abstract class BaseMetricClassifier {
  def classify(control: Metric, experiment: Metric): MetricClassification
}
