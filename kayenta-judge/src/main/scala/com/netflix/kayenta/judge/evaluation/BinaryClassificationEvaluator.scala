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
import com.netflix.kayenta.judge.classifiers.metric._

/**
  * Evaluator for binary classification
  */
class BinaryClassificationEvaluator extends BaseEvaluator{

  private def validInput(input: Array[Int]): Boolean ={
   input.contains(0) || input.contains(1)
  }

  /**
    * Calculate evaluation metrics
    * @param truth ground truth (correct) labels
    * @param predictions predicted labels, as returned by a classifier
    * @return map of evaluation results (precision, recall, f1, accuracy)
    */
  def calculateMetrics(truth: Array[Int], predictions: Array[Int]): Map[String, Double] ={
    require(predictions.length == truth.length, "the prediction vector and truth vector must be the same size")
    require(validInput(predictions) && validInput(truth), "the prediction or truth vectors contain invalid entries")

    //Calculate the evaluation metrics
    val precision = Metrics.precision(truth, predictions)
    val recall = Metrics.recall(truth, predictions)
    val f1 = Metrics.fMeasure(truth, predictions)
    val accuracy = Metrics.accuracy(truth, predictions)

    //Return a default value of -1.0
    Map(("Precision", precision), ("Recall", recall), ("FMeasure", f1), ("Accuracy", accuracy)).withDefaultValue(-1.0)
  }

  /**
    * Convert the classification labels to a binary representation
    * @param label metric classification result (label)
    * @return binary representation
    */
  def convertLabel(label: MetricClassificationLabel): Int ={
    label match {
      case High => 1
      case Low => 1
      case NodataFailMetric => 1
      case Nodata => 0
      case Pass => 0
      case Error => -1
    }
  }

  /**
    * Evaluate a metric classification algorithm (binary)
    * @param classifier metric classification algorithm
    * @param dataset input dataset to evaluate
    * @return map of evaluation results (precision, recall, f1, accuracy)
    */
  override def evaluate[T <: BaseMetricClassifier](classifier: T, dataset: List[LabeledInstance]): Map[String, Double] = {
    val truth = dataset.map(x => convertLabel(x.label))
    val predictions = dataset.map(x => classifier.classify(x.control, x.experiment).classification)
    val binaryPredictions = predictions.map(convertLabel)
    calculateMetrics(truth.toArray, binaryPredictions.toArray)
  }

}
